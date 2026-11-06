package io.github.pavelsvistunoff.microminichat;

import android.content.Context;
import android.os.*;
import io.github.pavelsvistunoff.microminichat.net.*;
import io.github.pavelsvistunoff.microminichat.pairing.*;
import io.github.pavelsvistunoff.microminichat.protocol.*;
import io.github.pavelsvistunoff.microminichat.security.*;
import io.github.pavelsvistunoff.microminichat.storage.MessageDatabase;
import io.github.pavelsvistunoff.microminichat.tor.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.SSLSocket;

/** Serialized Tor-native conversation coordinator, owned across Activity recreation. */
public final class TorChatSession {
    public enum State { OFFLINE, STARTING_TOR, CONNECTING_TOR, ONION_READY, CONNECTING_PEER, AUTHENTICATING, CONNECTED, RECONNECTING, FAILED }
    public enum Problem { NONE, IDENTITY, PAIRING, IDENTITY_CHANGED, STORAGE, CONNECTION, PROTOCOL }
    private final Context context;
    private final String storage;
    final Handler worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TlsIdentity identity;
    private final TrustStore trust;
    private final PairingCapabilities capabilities;
    private final MessageDatabase database;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final AtomicInteger inputBytes = new AtomicInteger();
    private TorRuntime tor;
    private TorConnector connector;
    private FramedLink link;
    private ReliableProtocol protocol;
    private UUID session;
    private boolean canonical, pairing, createRequested;
    private TorInvite joining;
    private long generation, before = Long.MAX_VALUE;
    private volatile Runnable watcher;
    public volatile State state = State.OFFLINE;
    public volatile Problem problem = Problem.NONE;
    public volatile int bootstrap;
    public volatile boolean ready, verified, hasOlder;
    public volatile String invitation = "", localFingerprint = "", remoteFingerprint = "", localOnion = "", remoteOnion = "";
    public volatile String draft = "", diagnostic = "";
    public volatile List<MessageDatabase.Row> history = Collections.emptyList();
    public TorChatSession(Context context) { this(context, "main"); }
    public TorChatSession(Context context,String storage) {
        this.context = context.getApplicationContext(); this.storage = storage;
        identity = new TlsIdentity(context,"identity-"+storage); trust = new TrustStore(context,"trust-"+storage);
        capabilities = new PairingCapabilities(context,"tor-capability-"+storage);
        database = new MessageDatabase(context,"history-"+storage+".db");
        HandlerThread thread = new HandlerThread("tor-chat-session"); thread.start(); worker = new Handler(thread.getLooper());
        execute(() -> { database.recover(); capabilities.cancel(); localFingerprint = identity.fingerprint(); refreshHistory(); });
    }
    interface Action { void run() throws Exception; }
    private void execute(Action action) {
        if (terminated.get()) return;
        worker.post(() -> { if (terminated.get()) return; try { action.run(); } catch (Exception e) { fail(Problem.STORAGE); } });
    }
    public void watch(Runnable watcher) { this.watcher = watcher; changed(); }
    private void changed() { main.post(() -> { Runnable current = watcher; if (current != null) current.run(); }); }
    private void fail(Problem problem) { this.problem = problem; state = State.FAILED; changed(); }
    public void start() { execute(this::startTor); }
    private void startTor() throws Exception {
        if (tor != null) return;
        try { localFingerprint = identity.fingerprint(); } catch (Exception e) { fail(Problem.IDENTITY); return; }
        problem = Problem.NONE; state = State.STARTING_TOR; changed();
        long token = ++generation;
        connector = new TorConnector(identity, trust, capabilities, new TorConnector.Listener() {
            @Override public void failed() { execute(() -> { if (token == generation && link == null) { diagnostic = connector.failureCode; fail(Problem.CONNECTION); } }); }
            @Override public void connected(SSLSocket socket,UUID session,String pin,String route,boolean outgoing) {
                if (terminated.get()) { try { socket.close(); } catch (Exception ignored) { } return; }
                worker.post(() -> {
                    try {
                        if (terminated.get() || token != generation) { socket.close(); return; }
                        attach(socket,session,pin,route,outgoing);
                    } catch (Exception e) { try { socket.close(); } catch (Exception ignored) { } diagnostic = "ATTACH_" + e.getClass().getSimpleName(); fail(Problem.CONNECTION); }
                });
            }
        });
        tor = new TorRuntime(context, new TorRuntime.Listener() {
            @Override public void state(int progress,boolean bootstrapped,boolean error) { execute(() -> {
                if (token != generation) return;
                bootstrap = progress;
                if (error) { stopTor(); fail(Problem.CONNECTION); return; }
                if (link == null && localOnion.isEmpty()) state = State.CONNECTING_TOR;
                changed();
            }); }
            @Override public void onionReady(String address) { execute(() -> {
                if (token != generation) return;
                localOnion = address; connector.localOnion = address;
                if (link == null) state = State.ONION_READY;
                if (createRequested) makeInvitation();
                if (joining != null) dial();
                changed();
            }); }
        },connector.port(),storage);
        tor.start();
    }
    public void createChat() { execute(() -> {
        if (!trust.trusted().isEmpty()) { fail(Problem.PAIRING); return; }
        cancelPairingInternal(); closeLink(); pairing = true; createRequested = true;
        startTor(); if (!localOnion.isEmpty()) makeInvitation();
    }); }
    private void makeInvitation() throws Exception {
        TorInvite invite = TorInvite.create(localOnion, localFingerprint, System.currentTimeMillis());
        capabilities.activate(invite); invitation = invite.encode(); createRequested = false; changed();
    }
    public void joinChat(String encoded) { execute(() -> {
        TorInvite invite;
        try { invite = TorInvite.decode(encoded,System.currentTimeMillis()); } catch (IllegalArgumentException e) { fail(Problem.PAIRING); return; }
        if (trust.match(invite.fingerprint) == TrustStore.Match.CHANGED) { fail(Problem.IDENTITY_CHANGED); return; }
        if (!trust.route().isEmpty() && !trust.route().equals(invite.onion)) { fail(Problem.IDENTITY_CHANGED); return; }
        cancelPairingInternal(); closeLink(); pairing = true; joining = invite;
        startTor(); if (!localOnion.isEmpty()) dial();
    }); }
    private void dial() throws Exception {
        if (joining == null || tor == null || localOnion.isEmpty()) return;
        state = State.CONNECTING_PEER; changed();
        connector.dial(tor.socksPort(),joining.onion,joining.fingerprint,joining);
    }
    private void attach(SSLSocket socket,UUID session,String pin,String route,boolean outgoing) throws Exception {
        if (trust.match(pin) == TrustStore.Match.CHANGED || (!trust.route().isEmpty() && !trust.route().equals(route))) {
            socket.close(); fail(Problem.IDENTITY_CHANGED); return;
        }
        if (!pairing && !route.equals(trust.route())) { socket.close(); return; }
        boolean preferred = outgoing == (localOnion.compareTo(route) < 0);
        if (link != null && (canonical || !preferred)) { socket.close(); return; }
        closeLink(); canonical = preferred; this.session = session;
        remoteFingerprint = pin; remoteOnion = route; invitation = ""; joining = null; createRequested = false;
        state = State.AUTHENTICATING; problem = Problem.NONE;
        FramedLink[] bound = new FramedLink[1];
        bound[0] = new FramedLink(socket,new FramedLink.Listener() {
            @Override public void onReady() { execute(() -> { if (link == bound[0]) initializeProtocol(); }); }
            @Override public void onClosed() { execute(() -> {
                if (link != bound[0]) return;
                diagnostic = bound[0].failureCode; closeLink(); state = State.OFFLINE; changed();
            }); }
            @Override public void onFrame(boolean file,byte[] bytes) {
                if (inputBytes.addAndGet(bytes.length) > 256 * 1024) { inputBytes.addAndGet(-bytes.length); bound[0].close(); return; }
                if (terminated.get()) { inputBytes.addAndGet(-bytes.length); return; }
                worker.post(() -> {
                    try {
                        if (link != bound[0]) return;
                        if (protocol == null && verified) initializeProtocol();
                        if (file || protocol == null || !verified) throw new java.io.IOException("UNVERIFIED_DATA");
                        protocol.receive(bytes,SystemClock.elapsedRealtime()); scheduleTick();
                    } catch (Exception e) { closeLink(); fail(Problem.PROTOCOL); }
                    finally { inputBytes.addAndGet(-bytes.length); }
                });
            }
        });
        link = bound[0]; link.start();
        verified = trust.match(pin) == TrustStore.Match.VERIFIED && route.equals(trust.route());
        if (verified) link.verify();
        changed();
    }
    public void verifyPeer(String expected) { execute(() -> {
        if (link == null || remoteFingerprint.isEmpty() || !remoteFingerprint.equals(expected)) return;
        trust.verifyRoute(expected,remoteOnion); verified = true; link.verify(); changed();
    }); }
    private void initializeProtocol() throws Exception {
        if (link == null || !verified || protocol != null) return;
        FramedLink bound = link;
        protocol = new ReliableProtocol(session,database.repository(remoteFingerprint),(bytes,callback) ->
                bound.send(false,bytes,written -> execute(() -> { if (link == bound) callback.accept(written); })), () -> {
                    ready = protocol != null && protocol.ready() && verified;
                    if (ready) { state = State.CONNECTED; pairing = false; }
                    refreshHistory(); changed();
                }, frame -> { throw new IllegalArgumentException("UNEXPECTED_FILE_FRAME"); },60000);
        protocol.start(SystemClock.elapsedRealtime()); scheduleTick();
    }
    private final Runnable tick = () -> {
        if (protocol == null) return;
        try { protocol.tick(SystemClock.elapsedRealtime()); scheduleTick(); }
        catch (Exception e) { closeLink(); fail(Problem.STORAGE); }
    };
    private void scheduleTick() { worker.removeCallbacks(tick); if (protocol != null && protocol.needsTick()) worker.postDelayed(tick,1000); }
    public boolean canQueue() { return !trust.trusted().isEmpty() && !trust.route().isEmpty(); }
    public void sendText(String text) { execute(() -> {
        Frame.textBytes(text);
        if (!canQueue()) { fail(Problem.PAIRING); return; }
        if (ready && protocol != null) { protocol.send(text,SystemClock.elapsedRealtime()); scheduleTick(); }
        else { database.repository(trust.trusted()).enqueue(text); refreshHistory(); }
    }); }
    public void retryMessages() { execute(() -> { if (protocol != null && ready) { protocol.retry(SystemClock.elapsedRealtime()); scheduleTick(); } }); }
    private void refreshHistory() {
        String peer = remoteFingerprint.isEmpty() ? trust.trusted() : remoteFingerprint;
        MessageDatabase.Page page = database.page(peer,before); hasOlder = page.more;
        history = database.rows(peer,before); changed();
    }
    public void loadOlder() { execute(() -> { if (hasOlder && !history.isEmpty()) { before = history.get(0).rowId; refreshHistory(); } }); }
    public void latestHistory() { execute(() -> { before = Long.MAX_VALUE; refreshHistory(); }); }
    public void clearHistory() { execute(() -> { database.clearHistory(trust.trusted()); refreshHistory(); }); }
    public void resetTrust() { execute(() -> { stopTor(); trust.clear(); refreshHistory(); }); }
    public void resetIdentity() { execute(() -> {
        stopTor();
        worker.postDelayed(() -> execute(() -> {
            if (TorRuntime.isNativeRunning()) { fail(Problem.IDENTITY); return; }
            identity.resetAfterConfirmation(); new OnionIdentity(context,storage).resetAfterConfirmation();
            localFingerprint = identity.fingerprint(); changed();
        }),5500);
    }); }
    public void cancelPairing() { execute(() -> { cancelPairingInternal(); closeLink(); changed(); }); }
    private void cancelPairingInternal() throws Exception { capabilities.cancel(); pairing = false; createRequested = false; joining = null; invitation = ""; }
    private void closeLink() {
        FramedLink old = link; link = null; worker.removeCallbacks(tick); protocol = null; ready = false; verified = false;
        if (old != null) { old.close(); database.interrupt(remoteFingerprint); }
        remoteFingerprint = ""; remoteOnion = ""; session = null; refreshHistory();
    }
    private void stopTor() throws Exception {
        generation++; cancelPairingInternal(); closeLink();
        if (connector != null) connector.close(); connector = null;
        if (tor != null) tor.close(); tor = null; localOnion = ""; bootstrap = 0; state = State.OFFLINE; changed();
    }
    public void disconnect() { execute(this::stopTor); }
    public void foreground() { start(); }
    public void background() { disconnect(); }
    public void shutdown() {
        if (!terminated.compareAndSet(false,true)) return;
        worker.post(() -> { try { stopTor(); } catch (Exception ignored) { } database.close(); worker.getLooper().quitSafely(); });
    }
}
