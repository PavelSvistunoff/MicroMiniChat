package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.storage.MessageDatabase;
import io.github.pavelsvistunoff.microminichat.tor.OnionIdentity;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SecurityBoundaryTest {
    @Test public void missingEstablishedOnionKeyNeverRegenerates() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String storage="missing-"+UUID.randomUUID();
        OnionIdentity identity=new OnionIdentity(context,storage);File directory=identity.prepare();
        assertTrue(context.getSharedPreferences("onion_identity_v1-"+storage,0).edit().putBoolean("created",true).commit());
        assertThrows(IOException.class,identity::prepare);
        assertFalse(new File(directory,"hs_ed25519_secret_key").exists());
    }
    @Test public void oldHistoryStaysVisibleAndDeletionKeepsReplayReceipts() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="archive-"+UUID.randomUUID()+".db";
        MessageDatabase database=new MessageDatabase(context,name);UUID old=UUID.randomUUID();
        try {
            database.repository("old-peer").receive(old,1,"old verified history");
            database.repository("new-peer").receive(UUID.randomUUID(),1,"new verified history");
            assertEquals(2,database.rows(null,Long.MAX_VALUE).size());assertEquals("old-peer",database.rows(null,Long.MAX_VALUE).get(0).peer);
            database.clearAllHistory();database.repository("old-peer").receive(old,1,"old verified history");
            assertTrue(database.rows(null,Long.MAX_VALUE).isEmpty());
        }finally{database.close();context.deleteDatabase(name);}
    }
    @Test public void databaseWriteFailureCannotCreateAReceipt() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="failure-"+UUID.randomUUID()+".db";
        MessageDatabase database=new MessageDatabase(context,name);UUID id=UUID.randomUUID();
        try {
            database.getWritableDatabase().execSQL("PRAGMA query_only=ON");
            assertThrows(IOException.class,()->database.repository("peer").receive(id,1,"transaction boundary test"));
            database.getWritableDatabase().execSQL("PRAGMA query_only=OFF");
            assertTrue(database.rows(null,Long.MAX_VALUE).isEmpty());
            database.repository("peer").receive(id,1,"transaction boundary test");
            assertEquals(1,database.rows(null,Long.MAX_VALUE).size());
        }finally{database.close();context.deleteDatabase(name);}
    }
}
