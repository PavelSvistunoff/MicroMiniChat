package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorReconnectTest {
    @Test public void verifiedPeersReconnectAndDrainOfflineQueueWithoutInvitation() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        TorChatSession left=new TorChatSession(context,"retry-a-"+tag),right=new TorChatSession(context,"retry-b-"+tag);
        try {
            TorSessionTest.pair(left,right);String pin=left.remoteFingerprint;
            right.disconnect();TorSessionTest.waitFor(()->!left.ready && !right.ready,30);
            left.sendText("queued during Tor peer restart");
            TorSessionTest.waitFor(()->left.history.stream().anyMatch(row->row.body.equals("queued during Tor peer restart") && !row.state.equals("Delivered")),10);
            right.start();
            try { TorSessionTest.waitFor(()->left.ready && right.ready,310); }
            catch(AssertionError e) { throw new AssertionError("Reconnection states "+left.state+"/"+right.state+" diagnostics "+left.diagnostic+"/"+right.diagnostic,e); }
            assertEquals(pin,left.remoteFingerprint);assertTrue(left.invitation.isEmpty());assertTrue(right.invitation.isEmpty());
            TorSessionTest.waitFor(()->left.history.stream().anyMatch(row->row.body.equals("queued during Tor peer restart") && row.state.equals("Delivered")),60);
            assertEquals(1,right.history.stream().filter(row->row.body.equals("queued during Tor peer restart")).count());
        } finally {left.shutdown();right.shutdown();}
    }
}
