package io.github.pavelsvistunoff.microminichat;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.tor.TorRuntime;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TorRuntimeIntegrationTest {
    @Test public void embeddedNativeRuntimeStartsAndCloses() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String storage="runtime-"+Long.toHexString(System.nanoTime());
        for (int attempt = 0; attempt < 2; attempt++) {
        CountDownLatch started = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        TorRuntime runtime = new TorRuntime(context, (progress, ready, error) -> {
            if (error) failed.set(true);
            if (progress > 0 || error) started.countDown();
        },0,storage);
        try {
            runtime.start();
            assertTrue("No actual bootstrap event", started.await(45, TimeUnit.SECONDS));
            assertFalse("Tor startup failed", failed.get());
            assertTrue(runtime.socksPort() > 0);
        } finally { runtime.close(); }
        runtime.close();
        long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
        while (runtime.isRunning() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
        assertFalse("Native Tor failed to stop", runtime.isRunning());
        assertEquals("Native Tor must exit cleanly",0,runtime.exitCodeAfterStop());
        }
    }
    @Test public void unavailableBundledExecutableFailsClosed() throws Exception {
        Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context missing=new android.content.ContextWrapper(real) {
            @Override public Context getApplicationContext() { return this; }
            @Override public android.content.pm.ApplicationInfo getApplicationInfo() {
                android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo(super.getApplicationInfo());
                info.nativeLibraryDir=new java.io.File(getCacheDir(),"missing-runtime-"+System.nanoTime()).getAbsolutePath();
                return info;
            }
        };
        CountDownLatch failed=new CountDownLatch(1);
        TorRuntime runtime=new TorRuntime(missing,(progress,ready,error)->{assertFalse(ready);if(error)failed.countDown();},0,"failure-"+Long.toHexString(System.nanoTime()));
        try {
            runtime.start();assertTrue(failed.await(15,TimeUnit.SECONDS));
            assertEquals("TOR_EXECUTE_IOException",runtime.failureCode);
            assertFalse(runtime.isRunning());assertThrows(java.io.IOException.class,runtime::socksPort);
        } finally { runtime.close(); }
    }
}
