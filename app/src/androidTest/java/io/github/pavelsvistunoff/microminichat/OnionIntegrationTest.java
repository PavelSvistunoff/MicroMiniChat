package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.tor.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OnionIntegrationTest {
    @Test public void publishesAndRestoresPersistentIdentity() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String previous="",storage="onion-"+Long.toHexString(System.nanoTime());
        TorRuntime[] last=new TorRuntime[1];
        for (int attempt=0; attempt<2; attempt++) {
            CountDownLatch ready=new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean failed=new java.util.concurrent.atomic.AtomicBoolean();
            try (ServerSocket server=new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
                TorRuntime runtime=new TorRuntime(context,new TorRuntime.Listener() {
                    @Override public void state(int progress,boolean bootstrapped,boolean error) { if(error){failed.set(true);ready.countDown();} }
                    @Override public void onionReady(String address) { ready.countDown(); }
                },server.getLocalPort(),storage);last[0]=runtime;
                try {
                    runtime.start();
                    assertTrue("Onion publication timed out",ready.await(310,TimeUnit.SECONDS));
                    assertFalse("Tor failed before publication: "+runtime.failureCode,failed.get());
                    String address=new OnionIdentity(context,storage).address();
                    assertFalse(address.isEmpty());
                    if(!previous.isEmpty())assertEquals(previous,address);
                    previous=address;
                } finally { runtime.close(); }
            }
            long deadline=android.os.SystemClock.elapsedRealtime()+10000;
            while(last[0].isRunning() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
            assertFalse(last[0].isRunning());assertEquals(0,last[0].exitCodeAfterStop());
        }
    }
}
