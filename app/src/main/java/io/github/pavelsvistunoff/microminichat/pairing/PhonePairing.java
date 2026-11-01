package io.github.pavelsvistunoff.microminichat.pairing;

import io.github.pavelsvistunoff.microminichat.security.PairingTls;
import io.github.pavelsvistunoff.microminichat.signal.Signal;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;

/** One bounded, temporary TLS exchange. No HTTP, content delivery, persistence or logs. */
public final class PhonePairing implements AutoCloseable {
    private static final int MAGIC=0x4d504831;
    private static final long LIFETIME_NANOS=TimeUnit.MINUTES.toNanos(10);
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(0,4,20,TimeUnit.SECONDS,
            new SynchronousQueue<>(),task->{Thread t=new Thread(task,"phone-pairing");t.setDaemon(true);return t;});
    private static final ScheduledThreadPoolExecutor CLOCK=new ScheduledThreadPoolExecutor(1,task->{
        Thread t=new Thread(task,"pairing-deadlines");t.setDaemon(true);return t;
    });
    static { CLOCK.setRemoveOnCancelPolicy(true); }
    public interface Listener {
        void onOffer(String offer);
        void onAnswer(String answer);
        void onFailure();
    }
    public final PhoneInvite invitation;
    public volatile String failureCode="";
    private final Listener listener;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final CompletableFuture<String> answer=new CompletableFuture<>();
    private volatile ServerSocket server;
    private volatile Socket active;
    private volatile ScheduledFuture<?> deadline;
    private PhonePairing(PhoneInvite invitation,Listener listener) { this.invitation=invitation;this.listener=listener; }

    public static PhonePairing host(PairingTls tls,String address,int port,String offer,Listener listener) throws Exception {
        Signal decoded=Signal.decode(offer);
        if (!decoded.offer || !PhoneInvite.validIp(address) || port<0 || port>65535) throw new IOException("INVALID_PAIRING_INPUT");
        ServerSocket server=new ServerSocket();
        PhonePairing result=null;
        try {
            server.setReuseAddress(true);server.bind(new InetSocketAddress(port),2);server.setSoTimeout(1000);
            byte[] token=new byte[32];new SecureRandom().nextBytes(token);
            result=new PhonePairing(new PhoneInvite(address,server.getLocalPort(),tls.fingerprint,token),listener);
            result.server=server;final PhonePairing owner=result;
            result.deadline=CLOCK.schedule(owner::fail,10,TimeUnit.MINUTES);
            IO.submit(()->owner.serve(offer,decoded,tls.context));return result;
        } catch (Exception e) { if(result!=null)result.close();else server.close();throw e; }
    }
    public static PhonePairing join(PhoneInvite invitation,Listener listener) {
        PhonePairing result=new PhonePairing(invitation,listener);
        result.deadline=CLOCK.schedule(result::fail,75,TimeUnit.SECONDS);
        try { IO.submit(result::connect); }
        catch (RejectedExecutionException e) { result.fail(); }
        return result;
    }
    private void serve(String offer,Signal expected,SSLContext tls) {
        long end=System.nanoTime()+LIFETIME_NANOS;
        try {
            int attempts=0;
            while (!closed.get() && System.nanoTime()<end && attempts<32) {
                Socket socket;
                try { socket=server.accept(); } catch (SocketTimeoutException e) { continue; }
                attempts++;
                boolean authorized=false;
                ScheduledFuture<?> guard=CLOCK.schedule(()->closeSocket(socket),5,TimeUnit.SECONDS);
                active=socket;
                try (Socket raw=socket;SSLSocket connection=(SSLSocket)tls.getSocketFactory().createSocket(raw,raw.getInetAddress().getHostAddress(),raw.getPort(),true)) {
                    if(closed.get())return;
                    connection.setUseClientMode(false);
                    connection.setSoTimeout(5000);connection.setEnabledProtocols(protocols(connection.getSupportedProtocols()));connection.startHandshake();
                    DataInputStream in=new DataInputStream(connection.getInputStream());
                    DataOutputStream out=new DataOutputStream(connection.getOutputStream());
                    if(in.readInt()!=MAGIC)continue;
                    byte[] supplied=new byte[32];in.readFully(supplied);
                    if(!MessageDigest.isEqual(invitation.token(),supplied))continue;
                    authorized=true;guard.cancel(false);
                    guard=CLOCK.schedule(()->closeSocket(socket),60,TimeUnit.SECONDS);
                    connection.setSoTimeout(60000);writeFrame(out,offer);
                    String payload=readFrame(in);Signal received=Signal.decode(payload);
                    if(received.offer || !expected.session.equals(received.session))throw new IOException("WRONG_ANSWER");
                    out.writeByte(1);out.flush();
                    if(!closed.get())listener.onAnswer(payload);
                    return;
                } catch (Exception e) { if(authorized)throw e; }
                finally { guard.cancel(false);active=null; }
            }
            if(!closed.get())fail();
        } catch (Exception e) { fail(); }
        finally { close(); }
    }
    private void connect() {
        String phase="CONNECT";
        try {
            Socket socket=new Socket();active=socket;
            try (Socket raw=socket) {
                if(closed.get())return;
                raw.connect(new InetSocketAddress(invitation.host,invitation.port),10000);
                try(SSLSocket connection=(SSLSocket)PairingTls.pinned(invitation.fingerprint).getSocketFactory().createSocket(raw,invitation.host,invitation.port,true)) {
                    connection.setEnabledProtocols(protocols(connection.getSupportedProtocols()));
                    phase="TLS";connection.setSoTimeout(10000);connection.startHandshake();
                    DataOutputStream out=new DataOutputStream(connection.getOutputStream());
                    DataInputStream in=new DataInputStream(connection.getInputStream());
                    out.writeInt(MAGIC);out.write(invitation.token());out.flush();
                    phase="OFFER";String offer=readFrame(in);Signal expected=Signal.decode(offer);
                    if(!expected.offer)throw new IOException("WRONG_OFFER");
                    if(closed.get())return;
                    listener.onOffer(offer);
                    phase="ANSWER";String payload=answer.get(45,TimeUnit.SECONDS);Signal received=Signal.decode(payload);
                    if(received.offer || !expected.session.equals(received.session))throw new IOException("WRONG_ANSWER");
                    writeFrame(out,payload);
                    if(in.readUnsignedByte()!=1)throw new IOException("ANSWER_NOT_ACKNOWLEDGED");
                }
            }
        } catch (Exception e) { failureCode=phase+"_"+e.getClass().getSimpleName();
            Throwable cause=e.getCause();for(int i=0;i<5 && cause!=null;i++,cause=cause.getCause())failureCode+="_"+cause.getClass().getSimpleName();
            fail(); }
        finally { close(); }
    }
    public void sendAnswer(String payload) { if(!closed.get())answer.complete(payload); }
    private static void writeFrame(DataOutputStream out,String payload) throws IOException {
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<1 || bytes.length>Signal.MAX_CHARS)throw new IOException("FRAME_TOO_LARGE");
        out.writeInt(bytes.length);out.write(bytes);out.flush();
    }
    private static String readFrame(DataInputStream in) throws IOException {
        int length=in.readInt();if(length<1 || length>Signal.MAX_CHARS)throw new IOException("INVALID_FRAME_LENGTH");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static String[] protocols(String[] supported) {
        java.util.List<String> enabled=new java.util.ArrayList<>();
        for(String name:supported)if(name.equals("TLSv1.2") || name.equals("TLSv1.3"))enabled.add(name);
        return enabled.toArray(new String[0]);
    }
    private static void closeSocket(Socket socket) {
        // Shutdown unblocks a TLS read before closing its shared underlying descriptor.
        try { socket.shutdownInput(); } catch(IOException | UnsupportedOperationException ignored) { }
        try { socket.shutdownOutput(); } catch(IOException | UnsupportedOperationException ignored) { }
        try { socket.close(); } catch(IOException ignored) { }
    }
    private void fail() {
        if(closed.compareAndSet(false,true)) { release();listener.onFailure(); }
    }
    @Override public void close() { if(closed.compareAndSet(false,true))release(); }
    private void release() {
        if(deadline!=null)deadline.cancel(false);
        try { if(server!=null)server.close(); } catch(IOException ignored) { }
        if(active!=null)closeSocket(active);
        answer.cancel(true);
        // Socket close and answer cancellation unblock work; bounded pool prevents thread accumulation.
    }
}
