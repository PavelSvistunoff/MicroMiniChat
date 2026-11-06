package io.github.pavelsvistunoff.microminichat.storage;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import io.github.pavelsvistunoff.microminichat.protocol.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** SQLite transactions define receipt, dedupe and ordered per-peer conversation state. */
public final class MessageDatabase extends SQLiteOpenHelper {
    public MessageDatabase(Context context) { this(context,"history.db"); }
    public MessageDatabase(Context context,String name) { super(context,name,null,1); setWriteAheadLoggingEnabled(true); }
    @Override public void onConfigure(SQLiteDatabase db) { db.execSQL("PRAGMA synchronous=FULL"); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages (rowid INTEGER PRIMARY KEY AUTOINCREMENT, peer TEXT NOT NULL, id TEXT NOT NULL, direction INTEGER NOT NULL, seq INTEGER NOT NULL, body TEXT NOT NULL, state TEXT NOT NULL, created INTEGER NOT NULL, UNIQUE(peer,id,direction), UNIQUE(peer,direction,seq))");
        db.execSQL("CREATE TABLE receipts (peer TEXT NOT NULL, id TEXT NOT NULL, seq INTEGER NOT NULL, hash BLOB NOT NULL, PRIMARY KEY(peer,id), UNIQUE(peer,seq))");
        db.execSQL("CREATE TABLE transfers (id TEXT PRIMARY KEY, peer TEXT NOT NULL, name TEXT NOT NULL, direction INTEGER NOT NULL, size INTEGER NOT NULL, progress INTEGER NOT NULL, state TEXT NOT NULL, uri TEXT NOT NULL, hash TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("UNSUPPORTED_SCHEMA"); }
    public void recover() {
        getWritableDatabase().execSQL("UPDATE messages SET state='Failed' WHERE state IN ('Sending','Sent')");
        getWritableDatabase().execSQL("UPDATE transfers SET state='Interrupted — select and restart' WHERE state NOT LIKE 'Complete%' AND state NOT LIKE 'Cancelled%' AND state NOT LIKE 'Failed%'");
    }
    private long counter(SQLiteDatabase db,String key) {
        try(Cursor c=db.rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})) { return c.moveToFirst()?c.getLong(0):0; }
    }
    private void counter(SQLiteDatabase db,String key,long value) {
        ContentValues values=new ContentValues(); values.put("key",key);values.put("value",value);
        db.insertWithOnConflict("meta",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public ReliableProtocol.Store repository(String peer) { return new Repository(peer); }
    private final class Repository implements ReliableProtocol.Store {
        private final String peer;
        Repository(String peer) { this.peer=peer; }
        @Override public ReliableProtocol.Message enqueue(String text) throws IOException {
            try {
                Frame.textBytes(text); SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
                try {
                    long seq=Math.addExact(counter(db,peer+":out"),1); UUID id=UUID.randomUUID();
                    insertMessage(db,peer,id,0,seq,text,ReliableProtocol.PENDING);counter(db,peer+":out",seq);
                    db.setTransactionSuccessful(); return new ReliableProtocol.Message(id,seq,text,ReliableProtocol.PENDING);
                } finally { db.endTransaction(); }
            } catch(RuntimeException e) { throw new IOException("MESSAGE_WRITE_FAILED"); }
        }
        @Override public ReliableProtocol.Message firstUnconfirmed() throws IOException {
            try(Cursor c=getReadableDatabase().rawQuery("SELECT id,seq,body,state FROM messages WHERE peer=? AND direction=0 AND state!='Delivered' ORDER BY seq LIMIT 1",new String[]{peer})) {
                return c.moveToFirst()?new ReliableProtocol.Message(UUID.fromString(c.getString(0)),c.getLong(1),c.getString(2),c.getString(3)):null;
            } catch(RuntimeException e) { throw new IOException("MESSAGE_READ_FAILED"); }
        }
        @Override public void state(UUID id,String state) throws IOException {
            if(!Arrays.asList(ReliableProtocol.PENDING,ReliableProtocol.SENDING,ReliableProtocol.SENT,ReliableProtocol.DELIVERED,ReliableProtocol.FAILED).contains(state))throw new IOException("INVALID_STATE");
            try {
                ContentValues values=new ContentValues();values.put("state",state);
                getWritableDatabase().update("messages",values,"peer=? AND id=? AND direction=0 AND state!='Delivered'",new String[]{peer,id.toString()});
            } catch(RuntimeException e) { throw new IOException("STATE_WRITE_FAILED"); }
        }
        @Override public void receive(UUID id,long seq,String text) throws IOException {
            try {
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(Frame.textBytes(text));
                SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
                try {
                    try(Cursor prior=db.rawQuery("SELECT seq,hash FROM receipts WHERE peer=? AND id=?",new String[]{peer,id.toString()})) {
                        if(prior.moveToFirst()) {
                            if(prior.getLong(0)!=seq || !MessageDigest.isEqual(hash,prior.getBlob(1)))throw new IOException("DUPLICATE_MISMATCH");
                            db.setTransactionSuccessful();return;
                        }
                    }
                    if(seq<=counter(db,peer+":in"))throw new IOException("SEQUENCE_MISMATCH");
                    ContentValues receipt=new ContentValues();receipt.put("peer",peer);receipt.put("id",id.toString());receipt.put("seq",seq);receipt.put("hash",hash);
                    db.insertOrThrow("receipts",null,receipt);
                    insertMessage(db,peer,id,1,seq,text,ReliableProtocol.DELIVERED); counter(db,peer+":in",seq);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } catch(IOException e) { throw e; }
            catch(Exception e) { throw new IOException("RECEIPT_COMMIT_FAILED"); }
        }
        @Override public void retry() throws IOException {
            try {
                ContentValues values=new ContentValues();values.put("state",ReliableProtocol.PENDING);
                getWritableDatabase().update("messages",values,"peer=? AND direction=0 AND state!='Delivered'",new String[]{peer});
            } catch(RuntimeException e) { throw new IOException("RETRY_WRITE_FAILED"); }
        }
    }
    private static void insertMessage(SQLiteDatabase db,String peer,UUID id,int direction,long seq,String text,String state) {
        ContentValues row=new ContentValues();row.put("peer",peer);row.put("id",id.toString());row.put("direction",direction);row.put("seq",seq);row.put("body",text);row.put("state",state);row.put("created",System.currentTimeMillis());
        db.insertOrThrow("messages",null,row);
    }
    public List<String> history(String peer,int limit) {
        List<String> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT direction,state,body FROM messages WHERE peer=? ORDER BY rowid DESC LIMIT ?",new String[]{peer,Integer.toString(Math.max(1,Math.min(limit,1000)))})) {
            while(c.moveToNext())rows.add((c.getInt(0)==0?"Me ["+c.getString(1)+"]: ":"Peer: ")+c.getString(2));
        }
        Collections.reverse(rows);return Collections.unmodifiableList(rows);
    }
    public void interrupt(String peer) {
        getWritableDatabase().execSQL("UPDATE messages SET state='Failed' WHERE peer=? AND state IN ('Sending','Sent')",new Object[]{peer});
        getWritableDatabase().execSQL("UPDATE transfers SET state='Interrupted — restart required' WHERE peer=? AND state NOT LIKE 'Complete%' AND state NOT LIKE 'Cancelled%' AND state NOT LIKE 'Failed%'",new Object[]{peer});
    }
    public static final class Page {
        public final List<String> rows; public final long oldest; public final boolean more;
        Page(List<String> rows,long oldest,boolean more) { this.rows=rows;this.oldest=oldest;this.more=more; }
    }
    public Page page(String peer,long before) {
        List<String> rows=new ArrayList<>();long oldest=before;boolean more=false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT rowid,direction,state,body FROM messages WHERE peer=? AND rowid<? ORDER BY rowid DESC LIMIT 201",new String[]{peer,Long.toString(before)})) {
            while(c.moveToNext()) {
                if(rows.size()==200){more=true;break;}
                oldest=c.getLong(0);rows.add((c.getInt(1)==0?"Me ["+c.getString(2)+"]: ":"Peer: ")+c.getString(3));
            }
        }
        Collections.reverse(rows);return new Page(Collections.unmodifiableList(rows),oldest,more);
    }
    public static final class Row {
        public final long rowId, created;
        public final boolean outgoing;
        public final String body, state;
        Row(long rowId,long created,boolean outgoing,String body,String state) {
            this.rowId=rowId;this.created=created;this.outgoing=outgoing;this.body=body;this.state=state;
        }
    }
    public List<Row> rows(String peer,long before) {
        List<Row> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT rowid,created,direction,body,state FROM messages WHERE peer=? AND rowid<? ORDER BY rowid DESC LIMIT 200",new String[]{peer,Long.toString(before)})) {
            while(c.moveToNext())result.add(new Row(c.getLong(0),c.getLong(1),c.getInt(2)==0,c.getString(3),c.getString(4)));
        }
        Collections.reverse(result);return Collections.unmodifiableList(result);
    }
    public static final class TransferRow {
        public final String name,state;
        public final boolean outgoing;
        public final long size,progress;
        TransferRow(String name,String state,boolean outgoing,long size,long progress) {
            this.name=name;this.state=state;this.outgoing=outgoing;this.size=size;this.progress=progress;
        }
    }
    public List<TransferRow> transferRows(String peer) {
        List<TransferRow> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name,state,direction,size,progress FROM transfers WHERE peer=? ORDER BY rowid DESC LIMIT 50",new String[]{peer})) {
            while(c.moveToNext())result.add(new TransferRow(c.getString(0),c.getString(1),c.getInt(2)==0,c.getLong(3),c.getLong(4)));
        }
        Collections.reverse(result);return Collections.unmodifiableList(result);
    }
    public void clearHistory(String peer) { getWritableDatabase().delete("messages","peer=?",new String[]{peer});getWritableDatabase().delete("transfers","peer=?",new String[]{peer}); }
    public void clearAll() { SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String table:new String[]{"messages","receipts","meta","transfers"})db.delete(table,null,null);db.setTransactionSuccessful();}finally{db.endTransaction();} }
    public void transfer(UUID id,String peer,String name,boolean outgoing,long size,long progress,String state,String uri,String hash) {
        ContentValues row=new ContentValues();row.put("id",id.toString());row.put("peer",peer);row.put("name",name);row.put("direction",outgoing?0:1);row.put("size",size);row.put("progress",progress);row.put("state",state);row.put("uri",uri);row.put("hash",hash);
        getWritableDatabase().insertWithOnConflict("transfers",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public List<String> transfers(String peer) {
        List<String> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT direction,name,progress,size,state FROM transfers WHERE peer=? ORDER BY rowid DESC LIMIT 50",new String[]{peer})) {
            while(c.moveToNext()) rows.add((c.getInt(0)==0?"Sent file: ":"Received file: ")+c.getString(1)+" ["+c.getString(4)+", "+c.getLong(2)+"/"+c.getLong(3)+" bytes]");
        }
        return rows;
    }
}
