package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.protocol.ReliableProtocol;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorSessionTest {
    static void waitFor(BooleanSupplier condition,long seconds) throws Exception {
        long deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000;
        while(!condition.getAsBoolean() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        assertTrue("Session state deadline",condition.getAsBoolean());
    }
    static void pair(TorChatSession left,TorChatSession right) throws Exception {
        left.createChat();right.start();
        waitFor(()->(!left.invitation.isEmpty() && !right.localOnion.isEmpty()) || left.state==TorChatSession.State.FAILED || right.state==TorChatSession.State.FAILED,310);
        assertFalse("left "+left.problem,left.state==TorChatSession.State.FAILED);assertFalse("right "+right.problem,right.state==TorChatSession.State.FAILED);
        right.joinChat(left.invitation);
        try { waitFor(()->(!left.remoteFingerprint.isEmpty() && !right.remoteFingerprint.isEmpty()) || left.state==TorChatSession.State.FAILED || right.state==TorChatSession.State.FAILED,360); }
        catch(AssertionError e) { throw new AssertionError("Connection states "+left.state+"/"+right.state+" diagnostics "+left.diagnostic+"/"+right.diagnostic,e); }
        assertFalse("left connection "+left.problem+" "+left.diagnostic,left.state==TorChatSession.State.FAILED);assertFalse("right connection "+right.problem+" "+right.diagnostic,right.state==TorChatSession.State.FAILED);
        assertEquals(left.localFingerprint,right.remoteFingerprint);assertEquals(right.localFingerprint,left.remoteFingerprint);
        left.verifyPeer(left.remoteFingerprint);right.verifyPeer(right.remoteFingerprint);
        waitFor(()->left.ready && right.ready,60);
    }
    @Test public void durableTextAndHistoryAcrossReopen() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String tag=Long.toHexString(System.nanoTime()), a="text-a-"+tag,b="text-b-"+tag;
        TorChatSession left=new TorChatSession(context,a),right=new TorChatSession(context,b);
        try {
            pair(left,right);left.sendText("Tor durable text");
            waitFor(()->left.history.stream().anyMatch(row->row.body.equals("Tor durable text") && row.state.equals(ReliableProtocol.DELIVERED)),60);
            assertEquals(1,right.history.stream().filter(row->row.body.equals("Tor durable text")).count());
            left.shutdown();right.shutdown();Thread.sleep(500);
            TorChatSession reopened=new TorChatSession(context,a);
            try { waitFor(()->!reopened.history.isEmpty(),10);assertEquals("Tor durable text",reopened.history.get(0).body); }
            finally { reopened.shutdown(); }
        } finally { left.shutdown();right.shutdown(); }
    }
}
