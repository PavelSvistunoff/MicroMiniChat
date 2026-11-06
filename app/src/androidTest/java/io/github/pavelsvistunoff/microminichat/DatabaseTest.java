package io.github.pavelsvistunoff.microminichat;

import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.storage.MessageDatabase;
import io.github.pavelsvistunoff.microminichat.protocol.ReliableProtocol;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DatabaseTest {
    @Test public void committedReceiptSurvivesReopenAndHistoryDeletionKeepsDedupe() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="receipt-test.db";context.deleteDatabase(name);UUID id=UUID.randomUUID();
        MessageDatabase db=new MessageDatabase(context,name);
        db.repository("peer").receive(id,1,"persisted");db.close();
        db=new MessageDatabase(context,name);db.repository("peer").receive(id,1,"persisted");
        assertEquals(1,db.history("peer",200).size());
        ReliableProtocol.Store store=db.repository("peer");
        assertThrows(java.io.IOException.class,()->store.receive(id,1,"tampered"));
        db.clearHistory("peer");store.receive(id,1,"persisted");assertTrue(db.history("peer",200).isEmpty());
        db.close();context.deleteDatabase(name);
    }
    @Test public void retryAndRecoveryPreserveIdsAndMonotonicSequences() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="outgoing-test.db";context.deleteDatabase(name);
        MessageDatabase db=new MessageDatabase(context,name);ReliableProtocol.Store store=db.repository("peer");
        ReliableProtocol.Message first=store.enqueue("one");store.state(first.id,ReliableProtocol.SENDING);db.close();
        db=new MessageDatabase(context,name);db.recover();store=db.repository("peer");
        assertEquals(first.id,store.firstUnconfirmed().id);assertEquals(ReliableProtocol.FAILED,store.firstUnconfirmed().state);
        store.retry();assertEquals(first.id,store.firstUnconfirmed().id);store.state(first.id,ReliableProtocol.DELIVERED);
        assertEquals(2,store.enqueue("two").sequence);db.close();context.deleteDatabase(name);
    }
    @Test public void versionOneHistoryMigratesWithoutChangingReceiptsOrTransferMetadata() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="upgrade-"+System.nanoTime()+".db";
        MessageDatabase initial=new MessageDatabase(context,name);
        UUID message=UUID.randomUUID(),transfer=UUID.randomUUID();
        initial.repository("peer").receive(message,1,"legacy history");
        initial.transfer(transfer,"peer","legacy.bin",false,10,10,"Complete","content://fixture/item","hash");
        initial.close();
        android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase(name,0,null);
        old.execSQL("ALTER TABLE transfers RENAME TO transfers_new");
        old.execSQL("CREATE TABLE transfers (id TEXT PRIMARY KEY, peer TEXT NOT NULL, name TEXT NOT NULL, direction INTEGER NOT NULL, size INTEGER NOT NULL, progress INTEGER NOT NULL, state TEXT NOT NULL, uri TEXT NOT NULL, hash TEXT NOT NULL)");
        old.execSQL("INSERT INTO transfers SELECT id,peer,name,direction,size,progress,state,uri,hash FROM transfers_new");
        old.execSQL("DROP TABLE transfers_new");old.setVersion(1);old.close();
        MessageDatabase upgraded=new MessageDatabase(context,name);
        try {
            assertEquals("legacy history",upgraded.rows("peer",Long.MAX_VALUE).get(0).body);
            upgraded.repository("peer").receive(message,1,"legacy history");
            assertEquals(1,upgraded.rows("peer",Long.MAX_VALUE).size());
            assertEquals("Complete",upgraded.transferRows("peer").get(0).state);
            assertEquals(0,upgraded.transferRows("peer").get(0).created);
            UUID fresh=UUID.randomUUID();upgraded.transfer(fresh,"peer","new.bin",true,10,1,"Sending","content://fixture/new","");
            long created=upgraded.transferRows("peer").get(1).created;assertTrue(created>0);
            upgraded.transfer(fresh,"peer","new.bin",true,10,10,"Complete","content://fixture/new","hash");
            assertEquals(created,upgraded.transferRows("peer").get(1).created);
        } finally { upgraded.close();context.deleteDatabase(name); }
    }
}
