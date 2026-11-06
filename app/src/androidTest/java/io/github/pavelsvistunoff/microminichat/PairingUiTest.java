package io.github.pavelsvistunoff.microminichat;
import android.app.*;
import android.content.Intent;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.pairing.TorInvite;
import org.junit.Test;
import static org.junit.Assert.*;
public class PairingUiTest {
    @Test public void invitationImportRequiresExplicitConnectAndDraftSurvivesRecreation() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        String invitation=TorInvite.create("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion","AB:".repeat(31)+"AB",System.currentTimeMillis()).encode();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(invitation)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        TorChatSession session=((ChatApp)activity.getApplication()).torSession();
        try {
            instrumentation.runOnMainSync(()->{
                assertNotNull(activity.pairing);assertEquals(invitation,((EditText)activity.pairing.findViewById(R.id.pair_input)).getText().toString());
                assertFalse(session.ready);assertTrue(session.remoteFingerprint.isEmpty());
                activity.pairing.dismiss();((EditText)activity.findViewById(R.id.chat_input)).setText("draft survives recreation");
            });
            Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(MainActivity.class.getName(),null,false);instrumentation.addMonitor(monitor);
            try {
                instrumentation.runOnMainSync(activity::recreate);Activity recreated=monitor.waitForActivityWithTimeout(5000);assertNotNull(recreated);
                instrumentation.runOnMainSync(()->{
                    assertEquals("draft survives recreation",((EditText)recreated.findViewById(R.id.chat_input)).getText().toString());
                    assertFalse(recreated.findViewById(R.id.send).isEnabled());recreated.finish();
                });
            } finally {instrumentation.removeMonitor(monitor);}
        } finally {instrumentation.runOnMainSync(activity::finish);session.disconnect();}
    }
}
