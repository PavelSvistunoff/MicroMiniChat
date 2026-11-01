package io.github.pavelsvistunoff.microminichat;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.signal.Signal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Guided pairing interactions on emulator; no internet connectivity claim. */
public final class PairingUiTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private ChatSession session() { return ((ChatApp)instrumentation.getTargetContext().getApplicationContext()).session(); }
    private MainActivity launch(Intent intent) throws Exception {
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        session().disconnect();drain();
        return activity;
    }
    private Intent intent() { return new Intent(instrumentation.getTargetContext(),MainActivity.class); }
    private void drain() throws Exception {
        CountDownLatch latch=new CountDownLatch(1);session().worker.post(latch::countDown);
        assertTrue(latch.await(5,TimeUnit.SECONDS));instrumentation.waitForIdleSync();
    }
    private void close(MainActivity activity) throws Exception {
        session().disconnect();drain();
        instrumentation.runOnMainSync(()->{ if(activity.pairing!=null)activity.pairing.dismiss();activity.finish(); });
    }
    @Test public void joinShowsInputAndConnectionSettingsAreCollapsed() throws Exception {
        MainActivity activity=launch(intent());
        try {
            instrumentation.runOnMainSync(()->{
                activity.pair();
                assertEquals(View.GONE,activity.pairing.findViewById(R.id.pair_network).getVisibility());
                assertFalse(activity.pairing.findViewById(R.id.pair_input).isShown());
                assertFalse(activity.pairing.findViewById(R.id.pair_share).isShown());
                assertFalse(activity.pairing.findViewById(R.id.pair_verify).isShown());
                activity.pairing.findViewById(R.id.pair_join).performClick();
                assertTrue(activity.pairing.findViewById(R.id.pair_input).isShown());
                assertFalse(activity.pairing.findViewById(R.id.pair_start).isShown());
                activity.pairing.findViewById(R.id.pair_settings).performClick();
                assertEquals(View.VISIBLE,activity.pairing.findViewById(R.id.pair_network).getVisibility());
            });
        } finally { close(activity); }
    }
    @Test public void sharesheetPrefillsInvitationWithoutStartingNetworkOrTrust() throws Exception {
        String sdp="v=0\r\na=fingerprint:sha-256 "+"AB:".repeat(31)+"AB\r\n"
                +"m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";
        String payload=new Signal(true,UUID.randomUUID(),sdp).encode();
        MainActivity activity=launch(intent().setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,payload));
        try {
            instrumentation.runOnMainSync(()->{
                assertNotNull(activity.pairing);
                EditText input=activity.pairing.findViewById(R.id.pair_input);
                assertEquals(payload,input.getText().toString());
                assertTrue(input.isShown());
                assertFalse(session().open);assertFalse(session().verified);assertTrue(session().localSignal.isEmpty());
                assertFalse(activity.findViewById(R.id.send).isEnabled());
            });
        } finally { close(activity); }
    }
    @Test public void startProducesShareInvitationWithoutShowingRawPayload() throws Exception {
        MainActivity activity=launch(intent());
        try {
            instrumentation.runOnMainSync(()->{activity.pair();activity.pairing.findViewById(R.id.pair_start).performClick();});
            SessionTest.waitFor(()->!session().localSignal.isEmpty());
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{
                TextView share=activity.pairing.findViewById(R.id.pair_share);
                assertTrue(share.isShown());assertEquals("Share invitation",share.getText().toString());
                assertTrue(activity.pairing.findViewById(R.id.pair_input).isShown());
                assertTrue(((EditText)activity.pairing.findViewById(R.id.pair_input)).getText().toString().isEmpty());
                assertFalse(activity.pairing.findViewById(R.id.pair_verify).isShown());
            });
        } finally { close(activity); }
    }
}
