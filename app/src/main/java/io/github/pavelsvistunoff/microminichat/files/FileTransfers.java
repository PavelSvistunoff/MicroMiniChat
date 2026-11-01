package io.github.pavelsvistunoff.microminichat.files;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** SAF-only bounded streaming on an independent thread. One in-flight chunk per direction. */
public final class FileTransfers implements AutoCloseable {
    private static final java.util.concurrent.Semaphore IO_SLOTS=new java.util.concurrent.Semaphore(4);
    public interface Link { void send(boolean fileChannel,Frame frame,Consumer<Boolean> result); }
    public interface Listener {
        void onOffer(FileOffer offer);
        void onUpdate(FileOffer offer,boolean outgoing,long progress,String state,String uri,String hash);
        void onError(String state);
    }
    private final ContentResolver resolver;
    private final UUID session;
    private final Link link;
    private final Listener listener;
    private final HandlerThread thread=new HandlerThread("file-streams");
    private final Handler owner;
    private final AtomicInteger queued=new AtomicInteger();
    private final AtomicBoolean closed=new AtomicBoolean(),overloaded=new AtomicBoolean();
    private final LinkedHashSet<UUID> finished=new LinkedHashSet<>();
    private Transfer outgoing,incoming;
    private static final class Transfer {
        final FileOffer offer;final boolean outgoing;Uri uri;
        InputStream input;OutputStream output;MessageDigest digest;
        long progress,lastReported;byte[] pending,hash;Runnable deadline;
        Transfer(FileOffer offer,boolean outgoing,Uri uri)throws Exception { this.offer=offer;this.outgoing=outgoing;this.uri=uri;digest=MessageDigest.getInstance("SHA-256"); }
    }
    public FileTransfers(Context context,UUID session,Link link,Listener listener) {
        if(!IO_SLOTS.tryAcquire())throw new IllegalStateException("FILE_PROVIDER_BUSY");
        resolver=context.getContentResolver();this.session=session;this.link=link;this.listener=listener;
        thread.start();owner=new Handler(thread.getLooper());
    }
    private interface Action { void run() throws Exception; }
    private void post(Action action) {
        if(!closed.get())owner.post(()->{
            if(closed.get())return;
            try{action.run();}catch(Exception e){failAll("Failed");listener.onError("File operation failed; reselect and restart");}
        });
    }
    private static void content(Uri uri) {
        if(uri==null || !"content".equals(uri.getScheme()))throw new IllegalArgumentException("SAF_URI_REQUIRED");
    }
    public void offer(Uri uri) { post(()->{
        content(uri);if(outgoing!=null){listener.onError("An outgoing transfer is already active");return;}
        String name="file.bin";long size=-1;
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)) {
            if(c!=null && c.moveToFirst()){
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);
                if(n>=0 && !c.isNull(n))name=c.getString(n);
                if(s>=0 && !c.isNull(s))size=c.getLong(s);
            }
        }
        if(size<0 || size>FileOffer.MAX_SIZE){listener.onError("Choose a file with known size, at most 16 GiB");return;}
        FileOffer offer=new FileOffer(UUID.randomUUID(),name,resolver.getType(uri),size);
        outgoing=new Transfer(offer,true,uri);report(outgoing,"Offered",true);
        sendControl(Frame.FILE_OFFER,outgoing,0,offer.encode());arm(outgoing,300000);
    }); }
    public void accept(UUID id,Uri uri) { post(()->{
        content(uri);
        if(incoming==null || !incoming.offer.id.equals(id) || incoming.output!=null) {
            cleanup(uri);listener.onError("File offer is no longer available");return;
        }
        incoming.uri=uri;incoming.output=resolver.openOutputStream(uri,"w");
        if(incoming.output==null)throw new IOException("DESTINATION_UNAVAILABLE");
        listener.onOffer(null);report(incoming,"Receiving",true);
        sendControl(Frame.FILE_ACCEPT,incoming,0,new byte[0]);arm(incoming,60000);
    }); }
    public void receive(boolean fileChannel,Frame frame) {
        int size=frame.payload.length+Frame.HEADER;
        if(queued.addAndGet(size)>256*1024){queued.addAndGet(-size);if(overloaded.compareAndSet(false,true))post(()->failAll("Failed — input limit"));return;}
        post(()->{try{handle(fileChannel,frame);}catch(Exception e){failAll("Failed");listener.onError("File data rejected or provider failed");}finally{queued.addAndGet(-size);}});
    }
    private void handle(boolean fileChannel,Frame frame)throws Exception {
        if(!session.equals(frame.session))throw new IOException("FILE_SESSION");
        if(finished.contains(frame.id))return;
        if(fileChannel!=(frame.type==Frame.FILE_CHUNK))throw new IOException("FILE_CHANNEL");
        if(frame.type==Frame.FILE_OFFER){
            if(frame.sequence!=0)throw new IOException();
            if(incoming!=null){link.send(false,new Frame(Frame.FILE_CANCEL,session,frame.id,0,new byte[0]),accepted->{});return;}
            incoming=new Transfer(FileOffer.decode(frame.id,frame.payload),false,null);
            report(incoming,"Offered",true);listener.onOffer(incoming.offer);arm(incoming,300000);return;
        }
        Transfer transfer=incoming!=null && incoming.offer.id.equals(frame.id)?incoming:
                outgoing!=null && outgoing.offer.id.equals(frame.id)?outgoing:null;
        if(transfer==null)return; // Late records for a cancelled session cannot create files.
        switch(frame.type){
            case Frame.FILE_ACCEPT:
                if(!transfer.outgoing || transfer.input!=null || transfer.hash!=null || frame.payload.length!=0 || frame.sequence!=0)throw new IOException();
                transfer.input=resolver.openInputStream(transfer.uri);if(transfer.input==null)throw new IOException();pump(transfer);break;
            case Frame.FILE_CHUNK:
                if(transfer.outgoing || transfer.output==null || !FileOffer.validChunk(frame.sequence,frame.payload.length,transfer.progress,transfer.offer.size))throw new IOException();
                transfer.output.write(frame.payload);transfer.digest.update(frame.payload);transfer.progress+=frame.payload.length;
                report(transfer,"Receiving",false);sendControl(Frame.FILE_CHUNK_ACK,transfer,transfer.progress,new byte[0]);arm(transfer,60000);break;
            case Frame.FILE_CHUNK_ACK:
                if(!transfer.outgoing || transfer.pending==null || frame.payload.length!=0 || frame.sequence!=transfer.progress+transfer.pending.length)throw new IOException();
                transfer.progress=frame.sequence;transfer.pending=null;report(transfer,"Sending",false);pump(transfer);break;
            case Frame.FILE_END:
                if(transfer.outgoing || transfer.output==null || frame.sequence!=transfer.offer.size || transfer.progress!=transfer.offer.size || frame.payload.length!=32)throw new IOException();
                transfer.hash=transfer.digest.digest();
                if(!MessageDigest.isEqual(transfer.hash,frame.payload))throw new IOException("HASH_MISMATCH");
                transfer.output.flush();transfer.output.close();transfer.output=null;
                sendControl(Frame.FILE_COMPLETE,transfer,transfer.progress,transfer.hash);finish(transfer,"Complete — SHA-256 verified",false);break;
            case Frame.FILE_COMPLETE:
                if(!transfer.outgoing || transfer.hash==null || frame.sequence!=transfer.offer.size || !MessageDigest.isEqual(transfer.hash,frame.payload))throw new IOException();
                finish(transfer,"Complete — SHA-256 verified",false);break;
            case Frame.FILE_CANCEL:
                if(frame.payload.length!=0)throw new IOException();finish(transfer,"Cancelled",true);break;
            default:throw new IOException("FILE_TYPE");
        }
    }
    private void pump(Transfer transfer)throws Exception {
        if(outgoing!=transfer)return;
        if(transfer.progress==transfer.offer.size){
            if(transfer.input.read()!=-1)throw new IOException("SOURCE_SIZE_CHANGED");
            transfer.hash=transfer.digest.digest();transfer.input.close();transfer.input=null;
            sendControl(Frame.FILE_END,transfer,transfer.progress,transfer.hash);report(transfer,"Waiting for completion",true);arm(transfer,30000);return;
        }
        int wanted=(int)Math.min(Frame.CHUNK,transfer.offer.size-transfer.progress);
        byte[] chunk=new byte[wanted];int offset=0;
        while(offset<wanted){int count=transfer.input.read(chunk,offset,wanted-offset);if(count<0)throw new EOFException();if(count==0)throw new IOException("EMPTY_SOURCE_READ");offset+=count;}
        transfer.digest.update(chunk);transfer.pending=chunk;report(transfer,"Sending",false);arm(transfer,30000);sendChunk(transfer);
    }
    private void sendChunk(Transfer transfer){
        if(outgoing!=transfer || transfer.pending==null)return;
        Frame chunk=new Frame(Frame.FILE_CHUNK,session,transfer.offer.id,transfer.progress,transfer.pending);
        link.send(true,chunk,accepted->post(()->{if(!accepted && outgoing==transfer && transfer.pending==chunk.payload)owner.postDelayed(()->sendChunk(transfer),100);}));
    }
    private void sendControl(int type,Transfer transfer,long offset,byte[] body){
        link.send(false,new Frame(type,session,transfer.offer.id,offset,body),accepted->post(()->{
            if(!accepted && (outgoing==transfer || incoming==transfer))finish(transfer,"Failed",true);
        }));
    }
    private void arm(Transfer transfer,long millis){
        if(transfer.deadline!=null)owner.removeCallbacks(transfer.deadline);
        transfer.deadline=()->{if(outgoing==transfer || incoming==transfer){sendControl(Frame.FILE_CANCEL,transfer,0,new byte[0]);finish(transfer,"Failed — timeout; restart",true);}};
        owner.postDelayed(transfer.deadline,millis);
    }
    private void report(Transfer transfer,String state,boolean force){
        long now=SystemClock.elapsedRealtime();if(!force && now-transfer.lastReported<250)return;transfer.lastReported=now;
        String hash="";if(transfer.hash!=null){StringBuilder hex=new StringBuilder();for(byte b:transfer.hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));hash=hex.toString();}
        listener.onUpdate(transfer.offer,transfer.outgoing,transfer.progress,state,transfer.uri==null?"":transfer.uri.toString(),hash);
    }
    private boolean cleanup(Uri uri){
        if(uri==null)return true;
        try{return DocumentsContract.deleteDocument(resolver,uri);}catch(Exception e){return false;}
    }
    private void finish(Transfer transfer,String state,boolean removePartial){
        if(transfer.deadline!=null)owner.removeCallbacks(transfer.deadline);
        if(outgoing==transfer)outgoing=null;if(incoming==transfer){incoming=null;listener.onOffer(null);}
        finished.add(transfer.offer.id);while(finished.size()>32)finished.remove(finished.iterator().next());
        try{if(transfer.input!=null)transfer.input.close();}catch(IOException ignored){}
        try{if(transfer.output!=null)transfer.output.close();}catch(IOException ignored){}
        transfer.input=null;transfer.output=null;transfer.pending=null;
        if(removePartial && !transfer.outgoing && !cleanup(transfer.uri))state+=" — partial output cleanup failed; delete manually";
        report(transfer,state,true);
    }
    private void failAll(String state){
        Transfer out=outgoing,in=incoming;
        if(out!=null){sendControl(Frame.FILE_CANCEL,out,0,new byte[0]);finish(out,state,true);}
        if(in!=null){sendControl(Frame.FILE_CANCEL,in,0,new byte[0]);finish(in,state,true);}
    }
    public void cancel(){post(()->failAll("Cancelled"));}
    @Override public void close(){
        if(closed.compareAndSet(false,true))owner.post(()->{
            try{failAll("Failed — disconnected; restart");}
            finally{owner.removeCallbacksAndMessages(null);thread.quitSafely();IO_SLOTS.release();}
        });
    }
}
