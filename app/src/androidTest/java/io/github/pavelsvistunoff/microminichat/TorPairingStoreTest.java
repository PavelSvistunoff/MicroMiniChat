package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.pairing.*;
import io.github.pavelsvistunoff.microminichat.security.TrustStore;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorPairingStoreTest {
    private static final String ONION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion";
    private static final String PIN="AB:".repeat(31)+"AB";
    @Test public void capabilityConsumptionSurvivesReopen() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="capability-test-"+java.util.UUID.randomUUID();
        PairingCapabilities store=new PairingCapabilities(context,name);
        TorInvite invite=TorInvite.create(ONION,PIN,1000);store.activate(invite);
        assertFalse(store.consume(new byte[32],1000));
        assertTrue(store.consume(invite.capability(),1000));
        assertFalse(new PairingCapabilities(context,name).consume(invite.capability(),1000));
        store.activate(invite);assertFalse(store.consume(invite.capability(),invite.expires));
        store.cancel();assertFalse(store.consume(invite.capability(),1000));
    }
    @Test public void routeTrustCommitsAtomicallyAndBlocksReplacement() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="trust-test-"+java.util.UUID.randomUUID();
        TrustStore store=new TrustStore(context,name);
        store.verify(PIN);assertEquals("",store.route()); // Legacy trust requires explicit route binding.
        store.verifyRoute(PIN,ONION);
        TrustStore reopened=new TrustStore(context,name);
        assertEquals(ONION,reopened.route());assertEquals(PIN,reopened.trusted());
        try { reopened.verifyRoute("CD:".repeat(31)+"CD",ONION);fail(); } catch(java.io.IOException expected) { }
        assertEquals(PIN,reopened.trusted());
    }
}
