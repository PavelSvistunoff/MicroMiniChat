package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorLifecycleTest {
    @Test public void backgroundGraceAndExplicitDisconnectSurviveForegroundReturn() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"life-a-"+tag),right=new ChatSession(context,"life-b-"+tag,500);
        try {
            TorSessionTest.pair(left,right);String fingerprint=right.remoteFingerprint;
            right.draft="retained input";right.background();Thread.sleep(100);right.foreground();Thread.sleep(200);
            assertTrue(right.ready);assertEquals("retained input",right.draft);
            right.background();TorSessionTest.waitFor(()->!right.ready && right.localOnion.isEmpty(),10);
            right.foreground();TorSessionTest.waitFor(()->left.ready && right.ready,310);
            assertEquals(fingerprint,right.remoteFingerprint);
            right.disconnect();TorSessionTest.waitFor(()->right.localOnion.isEmpty(),10);
            right.foreground();Thread.sleep(1000);assertTrue(right.localOnion.isEmpty());assertFalse(right.ready);
        }finally{left.shutdown();right.shutdown();}
    }
}
