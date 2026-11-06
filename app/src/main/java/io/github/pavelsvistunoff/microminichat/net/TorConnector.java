package io.github.pavelsvistunoff.microminichat.net;

import io.github.pavelsvistunoff.microminichat.pairing.*;
import io.github.pavelsvistunoff.microminichat.security.*;
import io.github.pavelsvistunoff.microminichat.tor.OnionAddress;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;

/** Only loopback is dialed directly. Remote names are canonical onion domains sent through SOCKS. */
public final class TorConnector implements AutoCloseable {
    public interface Listener {
        void connected(SSLSocket socket, UUID session, String fingerprint, String onion, boolean outgoing);
        void failed();
        default void authenticating() { }
    }
    private final TlsIdentity identity;
    private final TrustStore trust;
    private final PairingCapabilities capabilities;
    private final Listener listener;
    private final ServerSocket server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> new Thread(r, "tls-authentication"));
    private final Set<Socket> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(), dialing = new AtomicBoolean();
    public volatile String localOnion = "";
    public volatile String failureCode = "";
    public TorConnector(TlsIdentity identity, TrustStore trust, PairingCapabilities capabilities, Listener listener) throws IOException {
        this.identity = identity; this.trust = trust; this.capabilities = capabilities; this.listener = listener;
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        new Thread(() -> {
            while (!closed.get()) {
                try {
                    Socket socket = server.accept();
                    pending.add(socket);
                    try { workers.execute(() -> accept(socket)); }
                    catch (RejectedExecutionException e) { pending.remove(socket); closeSocket(socket); }
                } catch (IOException e) { if (!closed.get()) listener.failed(); break; }
            }
        }, "onion-listener").start();
    }
    public int port() { return server.getLocalPort(); }
    private static String readOnion(DataInputStream in) throws IOException {
        byte[] address = new byte[62]; in.readFully(address);
        try { return OnionAddress.require(new String(address, StandardCharsets.US_ASCII)); }
        catch (IllegalArgumentException e) { throw new IOException("PEER_ROUTE"); }
    }
    private void accept(Socket raw) {
        SSLSocket tls = null;
        try {
            if (localOnion.isEmpty() || closed.get()) throw new IOException("NOT_READY");
            raw.setSoTimeout(60000);
            tls = (SSLSocket) identity.tls(trust.trusted()).getSocketFactory().createSocket(raw, null, server.getLocalPort(), true);
            pending.add(tls);
            listener.authenticating();
            TlsIdentity.configure(tls, false);
            String fingerprint = TlsIdentity.authenticate(tls);
            DataInputStream in = new DataInputStream(tls.getInputStream());
            if (in.readInt() != 0x4d544802) throw new IOException("AUTH_VERSION");
            int mode = in.readUnsignedByte();
            UUID session = new UUID(in.readLong(), in.readLong());
            String route = readOnion(in);
            if (route.equals(localOnion) || identity.fingerprint().equals(fingerprint)) throw new IOException("SELF_CONNECTION");
            byte[] token = new byte[32]; in.readFully(token);
            boolean authorized;
            if (mode == 1) authorized = capabilities.consume(token, System.currentTimeMillis());
            else if (mode == 2) authorized = fingerprint.equals(trust.trusted()) && route.equals(trust.route());
            else authorized = false;
            Arrays.fill(token, (byte) 0);
            if (!authorized || trust.match(fingerprint) == TrustStore.Match.CHANGED || closed.get()) throw new IOException("PEER_UNAUTHORIZED");
            DataOutputStream out = new DataOutputStream(tls.getOutputStream());
            out.writeInt(0x4d544802); out.write(localOnion.getBytes(StandardCharsets.US_ASCII)); out.flush();
            pending.remove(tls); pending.remove(raw);
            listener.connected(tls, session, fingerprint, route, false); tls = null;
        } catch (Exception e) { failureCode = "INCOMING_" + failure(e); closeSocket(tls == null ? raw : tls); }
        finally { pending.remove(raw); if (tls != null) pending.remove(tls); }
    }
    public void dial(int socksPort, String route, String pin, TorInvite invite) {
        OnionAddress.require(route);
        if (closed.get() || !dialing.compareAndSet(false, true)) return;
        try { workers.execute(() -> {
            Socket raw = null; SSLSocket tls = null;
            try {
                if (localOnion.isEmpty() || socksPort < 1 || socksPort > 65535) throw new IOException("NOT_READY");
                if (invite != null) TorInvite.decode(invite.encode(), System.currentTimeMillis());
                raw = new Socket();
                pending.add(raw);
                OnionSocket.connect(raw, socksPort, route);
                raw.setSoTimeout(60000);
                tls = (SSLSocket) identity.tls(pin).getSocketFactory().createSocket(raw, route, 443, true);
                pending.add(tls); listener.authenticating(); TlsIdentity.configure(tls, true);
                String fingerprint = TlsIdentity.authenticate(tls);
                UUID session = UUID.randomUUID();
                DataOutputStream out = new DataOutputStream(tls.getOutputStream());
                out.writeInt(0x4d544802); out.writeByte(invite == null ? 2 : 1);
                out.writeLong(session.getMostSignificantBits()); out.writeLong(session.getLeastSignificantBits());
                out.write(localOnion.getBytes(StandardCharsets.US_ASCII)); out.write(invite == null ? new byte[32] : invite.capability()); out.flush();
                DataInputStream in = new DataInputStream(tls.getInputStream());
                if (in.readInt() != 0x4d544802 || !route.equals(readOnion(in)) || closed.get()) throw new IOException("PEER_ROUTE_BINDING");
                pending.remove(tls); pending.remove(raw);
                listener.connected(tls, session, fingerprint, route, true); tls = null; raw = null;
            } catch (Exception e) { failureCode = "OUTGOING_" + failure(e); if (!closed.get()) listener.failed(); }
            finally {
                if (tls != null) { pending.remove(tls); closeSocket(tls); }
                if (raw != null) { pending.remove(raw); closeSocket(raw); }
                dialing.set(false);
            }
        }); } catch (RejectedExecutionException e) { dialing.set(false); listener.failed(); }
    }
    private static String failure(Exception error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if ("PEER_IDENTITY_CHANGED".equals(cause.getMessage())) return "IDENTITY_CHANGED";
        }
        return error.getClass().getSimpleName();
    }
    private static void closeSocket(Socket socket) { if (socket != null) try { socket.close(); } catch (IOException ignored) { } }
    public void cancelPending() { for (Socket socket : pending) closeSocket(socket); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        for (Socket socket : pending) closeSocket(socket);
        pending.clear(); workers.shutdownNow();
    }
}
