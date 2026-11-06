package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.security.*;
import java.net.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TlsAuthenticationTest {
    @Test public void mutualTlsPinsPersistentKeysAndRejectsChanges() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String first="tls-test-a-"+java.util.UUID.randomUUID(), second="tls-test-b-"+java.util.UUID.randomUUID();
        TlsIdentity a=new TlsIdentity(context,first),b=new TlsIdentity(context,second);
        try {
            String aPin=a.fingerprint(), bPin=b.fingerprint();
            assertEquals(aPin,new TlsIdentity(context,first).fingerprint());
            exchange(a,b,aPin,bPin,true);
            exchange(a,b,"00:".repeat(31)+"00",bPin,false);
            exchange(a,b,aPin,"00:".repeat(31)+"00",false);
        } finally { a.resetAfterConfirmation(); b.resetAfterConfirmation(); }
    }
    @Test public void preservesLegacyCertificateAndWrappedFile() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="legacy-tls-"+java.util.UUID.randomUUID();
        IdentityStore old=new IdentityStore(context,name);
        try {
            String pin=IdentityStore.fingerprint(old.load());
            byte[] saved=java.nio.file.Files.readAllBytes(old.encryptedFile().toPath());
            assertEquals(pin,new TlsIdentity(context,name).fingerprint());
            assertArrayEquals(saved,java.nio.file.Files.readAllBytes(old.encryptedFile().toPath()));
        } finally { old.reset(); }
    }
    private void exchange(TlsIdentity a,TlsIdentity b,String serverPin,String clientPin,boolean success) throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> diagnostic=new java.util.concurrent.atomic.AtomicReference<>("");
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(10000);
            Future<Boolean> received=worker.submit(()->{
                try(Socket raw=server.accept(); SSLSocket tls=(SSLSocket)a.tls(clientPin).getSocketFactory().createSocket(raw,null,server.getLocalPort(),true)) {
                    tls.setSoTimeout(10000); TlsIdentity.configure(tls,false);
                    assertEquals(b.fingerprint(),TlsIdentity.authenticate(tls));
                    return tls.getInputStream().read()==42;
                } catch(Exception e) { diagnostic.set(e.toString()+" cause="+e.getCause()); return false; }
            });
            boolean sent;
            try(SSLSocket tls=(SSLSocket)b.tls(serverPin).getSocketFactory().createSocket("127.0.0.1",server.getLocalPort())) {
                tls.setSoTimeout(10000);TlsIdentity.configure(tls,true);
                assertEquals(a.fingerprint(),TlsIdentity.authenticate(tls));
                tls.getOutputStream().write(42);tls.getOutputStream().flush();sent=true;
            } catch(Exception e) { diagnostic.set(diagnostic.get()+" client "+e.toString()+" cause="+e.getCause()); sent=false; }
            boolean accepted=received.get(15,TimeUnit.SECONDS);
            assertEquals(diagnostic.get(),success,sent && accepted);
        } finally { worker.shutdownNow(); }
    }
}
