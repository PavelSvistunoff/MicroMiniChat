package io.github.pavelsvistunoff.microminichat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import org.junit.Test;
import static org.junit.Assert.*;

/** Test-only AndroidX runner; no AndroidX runtime dependency in the app. */
public final class FoundationTest {
    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
    @Test
    public void testManifestHasNoDangerousPermissionsOrBackup() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        assertEquals(0, info.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        assertEquals(2, info.requestedPermissions.length);
    }

    @Test
    public void testLaunchAndRecreate() {
        android.app.Activity activity = getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        assertNotNull(activity.findViewById(android.R.id.content));
        assertNotNull(activity.findViewById(R.id.chat_input));
        assertFalse(activity.findViewById(R.id.send).isEnabled());
        assertFalse(activity.findViewById(R.id.attach).isEnabled());
        getInstrumentation().waitForIdleSync();
        assertTrue("Chat history must have visible space",activity.findViewById(R.id.messages).getHeight() > 150*activity.getResources().getDisplayMetrics().density);
        android.app.Instrumentation.ActivityMonitor monitor =
                getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        android.app.Activity recreated = null;
        try {
            getInstrumentation().runOnMainSync(activity::recreate);
            recreated = monitor.waitForActivityWithTimeout(5000);
            assertNotNull("Activity must be recreated", recreated);
            assertNotSame(activity, recreated);
            assertNotNull(recreated.findViewById(android.R.id.content));
        } finally {
            getInstrumentation().removeMonitor(monitor);
            android.app.Activity remaining = recreated != null ? recreated : activity;
            getInstrumentation().runOnMainSync(remaining::finish);
            ((ChatApp)getInstrumentation().getTargetContext().getApplicationContext()).session().disconnect();
        }
    }
}
