package io.github.pavelsvistunoff.microminichat;

import androidx.test.platform.app.InstrumentationRegistry;
import android.os.SystemClock;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

/** Two complete sessions in one emulator process; not a real-device internet test. */
public final class SessionTest {
    static void waitFor(BooleanSupplier condition) throws Exception {
        long end=SystemClock.elapsedRealtime()+45000;
        while(!condition.getAsBoolean() && SystemClock.elapsedRealtime()<end)Thread.sleep(50);
        assertTrue("Condition did not become true",condition.getAsBoolean());
    }
    static void connect(ChatSession left,ChatSession right,boolean verify) throws Exception {
        left.offer("");waitFor(()->!left.localSignal.isEmpty());
        right.apply(left.localSignal,"");waitFor(()->!right.localSignal.isEmpty());
        left.apply(right.localSignal,"");waitFor(()->left.open && right.open);
        if(verify) { assertFalse(left.ready);assertFalse(right.ready);left.verifyPeer();right.verifyPeer(); }
        waitFor(()->left.ready && right.ready);
    }
    @Test public void directSessionRejectsUnconsentedRelayOffer() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ChatSession session=new ChatSession(context,"relayconsent"+Long.toHexString(System.nanoTime()));
        try {
            String sdp="v=0\r\na=fingerprint:sha-256 "+"AB:".repeat(31)+"AB\r\n"
                    +"m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n"
                    +"a=candidate:1 1 UDP 1 192.0.2.1 3478 typ relay\r\n";
            session.apply(new io.github.pavelsvistunoff.microminichat.signal.Signal(true,java.util.UUID.randomUUID(),sdp).encode(),"");
            waitFor(()->session.status.contains("configure TURN to consent"));
            assertFalse(session.open);assertTrue(session.localSignal.isEmpty());
        } finally { session.shutdown(); }
    }

    @Test public void backgroundGraceExpiresWithoutTrustOrHistoryLoss() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String suffix=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"bgleft"+suffix,100),right=new ChatSession(context,"bgright"+suffix);
        try {
            connect(left,right,true);left.background();waitFor(()->!left.open && !right.open);assertFalse(left.ready);
            left.foreground();connect(left,right,false);left.clearEverything();right.clearEverything();
        } finally { left.shutdown();right.shutdown(); }
    }

    @Test public void networkChangeRequiresFreshSignalingAndRetainsVerifiedTrust() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"netleft"+suffix),right=new ChatSession(context,"netright"+suffix);
        try {
            connect(left,right,true);String oldAnswer=right.localSignal;
            left.networkChanged();waitFor(()->!left.open && !right.open);
            assertFalse(left.ready);
            left.offer("");waitFor(()->!left.localSignal.isEmpty());
            left.apply(oldAnswer,"");waitFor(()->left.status.contains("does not match"));assertFalse(left.open);
            right.apply(left.localSignal,"");
            java.util.UUID id=io.github.pavelsvistunoff.microminichat.signal.Signal.decode(left.localSignal).session;
            waitFor(()->!right.localSignal.isEmpty() && io.github.pavelsvistunoff.microminichat.signal.Signal.decode(right.localSignal).session.equals(id));
            left.apply(right.localSignal,"");waitFor(()->left.ready && right.ready);
            left.clearEverything();right.clearEverything();
        } finally { left.shutdown();right.shutdown(); }
    }

    @Test public void verifiedSessionsPersistMessagesAndReconnectWithTrust() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"left"+suffix),right=new ChatSession(context,"right"+suffix);
        try {
            connect(left,right,true);left.sendText("durable message");
            waitFor(()->left.history.toString().contains("[Delivered]: durable message") && right.history.toString().contains("Peer: durable message"));
            left.disconnect();right.disconnect();waitFor(()->!left.open && !right.open);
            left.shutdown();right.shutdown();
            ChatSession secondLeft=new ChatSession(context,"left"+suffix),secondRight=new ChatSession(context,"right"+suffix);
            try {
                waitFor(()->secondLeft.history.toString().contains("durable message"));
                connect(secondLeft,secondRight,false);
                secondRight.sendText("after restart");
                waitFor(()->secondRight.history.toString().contains("[Delivered]: after restart") && secondLeft.history.toString().contains("Peer: after restart"));
                secondLeft.clearEverything();secondRight.clearEverything();
                waitFor(()->secondLeft.history.isEmpty() && secondRight.history.isEmpty());
            } finally { secondLeft.shutdown();secondRight.shutdown(); }
        } finally { left.shutdown();right.shutdown(); }
    }
}
