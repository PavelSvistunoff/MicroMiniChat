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
        ChatSession session=((ChatApp)activity.getApplication()).session();
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
    @Test public void longInvitationConnectsByTouchWithKeyboardVisible() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=30);
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        String invitation=TorInvite.create("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion","AB:".repeat(31)+"AB",System.currentTimeMillis()).encode();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse(invitation)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ChatSession session=((ChatApp)activity.getApplication()).session();
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{
                EditText field=activity.pairing.findViewById(R.id.pair_input);field.requestFocus();
                ((android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            java.util.concurrent.atomic.AtomicBoolean keyboard=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+8000;
            while(!keyboard.get()&&android.os.SystemClock.elapsedRealtime()<deadline) {
                instrumentation.runOnMainSync(()->{
                    android.view.WindowInsets insets=activity.pairing.getWindow().getDecorView().getRootWindowInsets();
                    keyboard.set(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()));
                });
                Thread.sleep(50);
            }
            assertTrue("Real keyboard must be visible",keyboard.get());
            int[] center=new int[2];
            instrumentation.runOnMainSync(()->{
                android.widget.Button connect=activity.pairing.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
                int[] location=new int[2];connect.getLocationOnScreen(location);
                android.graphics.Rect visible=new android.graphics.Rect();connect.getWindowVisibleDisplayFrame(visible);
                center[0]=location[0]+connect.getWidth()/2;center[1]=location[1]+connect.getHeight()/2;
                assertTrue("Connect must remain above the IME",center[1]<visible.bottom);
            });
            long now=android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,center[0],center[1],0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,android.view.MotionEvent.ACTION_UP,center[0],center[1],0);
            try { instrumentation.sendPointerSync(down);instrumentation.sendPointerSync(up); }
            finally { down.recycle();up.recycle(); }
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{assertFalse("Touch Connect must dismiss the reviewed invitation",activity.pairing.isShowing());assertFalse(session.ready);});
        } finally { instrumentation.runOnMainSync(activity::finish);session.disconnect(); }
    }
}
