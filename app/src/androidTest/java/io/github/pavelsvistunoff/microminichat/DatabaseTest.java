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
}
