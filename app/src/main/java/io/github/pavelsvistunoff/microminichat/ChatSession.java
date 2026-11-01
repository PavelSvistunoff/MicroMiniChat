package io.github.pavelsvistunoff.microminichat;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import io.github.pavelsvistunoff.microminichat.net.TransportPolicy;
import io.github.pavelsvistunoff.microminichat.net.IceConfig;
import io.github.pavelsvistunoff.microminichat.net.WebRtcTransport;
import io.github.pavelsvistunoff.microminichat.signal.Signal;
import java.util.UUID;
import android.os.SystemClock;
import io.github.pavelsvistunoff.microminichat.lifecycle.ForegroundPolicy;
import android.net.Uri;
import android.net.Network;
import android.net.ConnectivityManager;
import io.github.pavelsvistunoff.microminichat.files.*;
import io.github.pavelsvistunoff.microminichat.protocol.*;
import io.github.pavelsvistunoff.microminichat.storage.MessageDatabase;
import io.github.pavelsvistunoff.microminichat.security.*;
import org.webrtc.RtcCertificatePem;
import io.github.pavelsvistunoff.microminichat.pairing.*;

/** Single owner for a single peer. Activity recreation does not recreate the connection. */
public final class ChatSession {
    private final Context context;
    private final IdentityStore identity;
    private final TrustStore trust;
    private final MessageDatabase database;
    private final ForegroundPolicy foregroundPolicy;
    private final java.util.concurrent.atomic.AtomicBoolean terminated = new java.util.concurrent.atomic.AtomicBoolean();
    private ConnectivityManager connectivity;
    private Network currentNetwork;
    private long pairingStarted, authenticationStarted, historyGeneration;
    private long historyBefore=Long.MAX_VALUE, oldestRow=Long.MAX_VALUE;
    public volatile boolean hasOlderHistory;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            if (currentNetwork != null && !currentNetwork.equals(network)) networkChanged();
            currentNetwork=network;
        }
        @Override public void onLost(Network network) {
            if (network.equals(currentNetwork)) { currentNetwork=null; networkChanged(); }
        }
    };
    private ReliableProtocol protocol;
    private FileTransfers transfers;
    public volatile FileOffer incomingOffer;
    public volatile String fileStatus = "";
    private final java.util.concurrent.atomic.AtomicInteger inboundBytes = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean flooded = new java.util.concurrent.atomic.AtomicBoolean();
    public volatile boolean verified;
    public volatile boolean ready;
    public volatile boolean filesEnabled;
    public volatile java.util.List<String> history = java.util.Collections.emptyList();
    final Handler worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable watcher;
    private WebRtcTransport transport;
    private UUID sessionId;
    private boolean offerer;
    private boolean answerApplied;
    public volatile String status = "Disconnected";
    public volatile String localSignal = "";
    public volatile String remoteFingerprint = "";
    public volatile String localFingerprint = "";
    public volatile boolean open;

    public ChatSession(Context context) { this(context,"main"); }
    public ChatSession(Context context,String storage) { this(context,storage,120000); }
    ChatSession(Context context,String storage,long grace) {
        foregroundPolicy=new ForegroundPolicy(grace);
        this.context = context.getApplicationContext();
        identity = new IdentityStore(context,"identity-"+storage); trust = new TrustStore(context,"trust-"+storage);
        database = new MessageDatabase(context,"history-"+storage+".db");
        HandlerThread thread = new HandlerThread("chat-session"); thread.start();
        worker = new Handler(thread.getLooper());
        execute(() -> {
            database.recover(); refreshHistory();
            connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            currentNetwork=connectivity.getActiveNetwork();
            connectivity.registerDefaultNetworkCallback(networkCallback,worker);
        });
    }
    public void watch(Runnable change) { watcher = change; if (change != null) change.run(); }
    void changed() { main.post(() -> { if (watcher != null) watcher.run(); }); }
    void error(String code) { status = code; changed(); }
    interface Action { void run() throws Exception; }
    void execute(Action action) { if (terminated.get()) return; worker.post(() -> {
        try { action.run(); } catch (Exception e) { error("Operation failed; disconnect and retry"); }
    }); }

    private PhonePairing phonePairing;
    private String advertisedPhone="";
    private int phonePort;
    private IceConfig phoneIce;
    public volatile boolean phonePairingActive;
    public volatile String phoneInvitation="";
    private boolean relayAllowed;
    public void offer(String stun) { offer(IceConfig.direct(stun)); }
    public void offer(IceConfig ice) { execute(() -> {
        reset();beginOffer(ice);
    }); }
    private void beginOffer(IceConfig ice) {
        relayAllowed=ice.allowsRelay();
        offerer=true;sessionId=UUID.randomUUID();pairingStarted=SystemClock.elapsedRealtime();
        if(createTransport())transport.offer(ice);
    }
    public void offerOnPhone(IceConfig ice,String address,int port) { execute(()->{
        if(!PhoneInvite.validIp(address) || port<1 || port>65535) { error("Enter a reachable phone IP and TCP port");return; }
        reset();advertisedPhone=address;phonePort=port;phoneIce=ice;phonePairingActive=true;beginOffer(ice);
    }); }
    public void joinPhone(PhoneInvite invitation,IceConfig ice) { execute(()->{
        reset();phonePairingActive=true;status="Connecting to creator’s phone";changed();
        phonePairing=PhonePairing.join(invitation,phoneListener(ice));
    }); }
    private PhonePairing.Listener phoneListener(IceConfig ice) {
        // Bound callbacks to this pairing generation, including cancellation before a socket is assigned.
        long generation=phoneGeneration;
        return new PhonePairing.Listener() {
            @Override public void onOffer(String payload) { execute(()->{
                if(generation==phoneGeneration && phonePairingActive)applySignal(payload,ice,true);
            }); }
            @Override public void onAnswer(String payload) { execute(()->{
                if(generation==phoneGeneration && phonePairingActive)applySignal(payload,ice,true);
            }); }
            @Override public void onFailure() { execute(()->{
                if(generation!=phoneGeneration || !phonePairingActive || open)return;
                reset();error("Phone pairing failed or expired — check public IP, TCP port and forwarding; or use manual pairing");
            }); }
        };
    }
    private long phoneGeneration;

    public void apply(String value, String stun) { apply(value, IceConfig.direct(stun)); }
    public void apply(String value, IceConfig ice) { execute(() -> applySignal(value,ice,false)); }
    private void applySignal(String value,IceConfig ice,boolean keepPhone) {
        Signal signal;
        try { signal = Signal.decode(value); } catch (IllegalArgumentException e) { error("Invalid or unsupported signaling payload"); return; }
        if (signal.offer) {
            reset(keepPhone);
            relayAllowed = ice.allowsRelay();
            if (signal.hasRelayCandidates() && !relayAllowed) { error("Peer offers TURN; configure TURN to consent before pairing"); return; }
            offerer = false; sessionId = signal.session; pairingStarted=SystemClock.elapsedRealtime(); remoteFingerprint = signal.fingerprint;
            if (trust.match(remoteFingerprint) == TrustStore.Match.CHANGED) { reset(); error("Peer identity changed — blocked. Verify independently before resetting trust."); return; }
            if (createTransport()) transport.answer(ice, signal.sdp);
        } else {
            if (signal.hasRelayCandidates() && !relayAllowed) { error("Peer offers TURN; create a fresh offer with TURN enabled"); return; }
            if (!offerer || transport == null || answerApplied || SystemClock.elapsedRealtime()-pairingStarted>600000 || !signal.session.equals(sessionId)) {
                error("Answer does not match a pending offer"); return;
            }
            if (trust.match(signal.fingerprint) == TrustStore.Match.CHANGED) { reset(); error("Peer identity changed — blocked. Verify independently before resetting trust."); return; }
            answerApplied = true; remoteFingerprint = signal.fingerprint; transport.acceptAnswer(signal.sdp);
        }
    }

    private boolean createTransport() {
        flooded.set(false);
        RtcCertificatePem certificate;
        try { certificate = identity.load(); localFingerprint = IdentityStore.fingerprint(certificate); }
        catch (Exception e) { reset(); error("Identity unavailable or expired. Explicit identity reset required."); return false; }
        WebRtcTransport[] reference = new WebRtcTransport[1];
        reference[0] = new WebRtcTransport(context, certificate, new WebRtcTransport.Listener() {
            @Override public void onState(String state) { if (terminated.get()) return; worker.post(() -> {
                if (transport != reference[0]) return;
                if (state.startsWith("FAILED_") || state.equals("DISCONNECTED") || state.equals("CLOSED")) {
                    reset(); error(state.equals("FAILED_TURN_ALLOCATION_FAILED")
                            ? "TURN allocation failed — check server, credentials and network; create fresh signaling"
                            : "Connection lost — reconnect manually ("+state+")"); return;
                }
                open = state.equals("OPEN");
                if(open && phonePairing!=null) { phonePairing.close();phonePairing=null;phoneInvitation=""; }
                verified = open && trust.match(remoteFingerprint) == TrustStore.Match.VERIFIED;
                status = open ? (verified ? "Connected — verified peer" : "Connected — verify peer before messaging")
                        : state.equals("GATHERING") ? "Gathering connection details"
                        : state.equals("WAITING_FOR_ANSWER") ? "Offer ready — share it and apply the answer"
                        : state.equals("CONNECTING") ? "Connecting" : state;
                if (!open) { protocol = null; ready = false; }
                if (verified) initializeProtocol();
                changed();
            }); }
            @Override public void onLocalSdp(boolean offer, String sdp) { if (terminated.get()) return; worker.post(() -> {
                if (transport != reference[0]) return;
                try {
                    Signal signal = new Signal(offer, sessionId, sdp);
                    if (!localFingerprint.equals(signal.fingerprint)) throw new IllegalArgumentException("IDENTITY_BINDING");
                    localSignal = signal.encode();
                    if(phonePairingActive) {
                        if(offer) {
                            try {
                                PairingTls tls=PairingTls.create(context);
                                phonePairing=PhonePairing.host(tls,advertisedPhone,phonePort,localSignal,phoneListener(phoneIce));
                                phoneInvitation=phonePairing.invitation.encode();
                                status="Invitation ready — keep this phone open while your friend joins";
                            } catch(Exception e) { reset();error("Could not start phone pairing server; check the port or use manual pairing");return; }
                        } else if(phonePairing!=null) {
                            phonePairing.sendAnswer(localSignal);status="Reply sent automatically — connecting";
                        }
                    }
                    changed();
                } catch (IllegalArgumentException e) { reset(); error("Local signaling failed validation"); }
            }); }
            @Override public void onFrame(boolean file, byte[] bytes) {
                if (terminated.get()) return;
                if (inboundBytes.addAndGet(bytes.length) > 256*1024) {
                    inboundBytes.addAndGet(-bytes.length);
                    if (flooded.compareAndSet(false,true)) worker.post(() -> { reset(); error("Peer exceeded input limits"); });
                    return;
                }
                worker.post(() -> {
                    try {
                        if (transport != reference[0] || !verified || protocol == null) return;
                        if (file) {
                            Frame frame = Frame.decode(bytes);
                            if (!ready || transfers == null) throw new java.io.IOException("UNEXPECTED_FILE_FRAME");
                            transfers.receive(true,frame); return;
                        }
                        protocol.receive(bytes,SystemClock.elapsedRealtime()); scheduleTick();
                    } catch (Exception e) { reset(); error("Peer protocol error or storage failure"); }
                    finally { inboundBytes.addAndGet(-bytes.length); }
                });
            }
        });
        transport = reference[0]; return true;
    }

    public void verifyPeer() { verifyPeer(remoteFingerprint); }
    public void verifyPeer(String expected) { execute(() -> {
        if (!open || remoteFingerprint.isEmpty() || !remoteFingerprint.equals(expected)) { error("Connect before verifying the peer"); return; }
        trust.verify(remoteFingerprint); verified = true; status = "Connected — verified peer"; initializeProtocol(); changed();
    }); }
    public void resetTrust() { execute(() -> { reset(); trust.clear(); refreshHistory(); }); }
    public void resetIdentity() { clearEverything(); }

    public void sendText(String value) { execute(() -> {
        if (!ready || protocol == null) { error("Connect and verify the peer before sending"); return; }
        protocol.send(value,SystemClock.elapsedRealtime()); scheduleTick();
    }); }
    private void initializeProtocol() {
        if (!open || !verified || protocol != null) return;
        WebRtcTransport bound = transport;
        authenticationStarted=SystemClock.elapsedRealtime();
        protocol = new ReliableProtocol(sessionId,database.repository(remoteFingerprint), (bytes,callback) ->
                bound.send(false,bytes,accepted -> worker.post(() -> {
                    if (transport == bound && protocol != null) {
                        try { callback.accept(accepted); }
                        catch (RuntimeException e) { reset();error("Message state could not be persisted"); }
                    }
                })), () -> {
                    ready = protocol != null && protocol.ready() && verified && open;
                    if (ready) status = "Connected — verified peer";
                    refreshHistory(); changed();
                }, frame -> { if (transfers == null) throw new IllegalArgumentException("UNEXPECTED_FRAME"); transfers.receive(false,frame); });
        try { initializeFiles(bound); }
        catch (RuntimeException e) { filesEnabled=false;fileStatus="A previous file provider is busy; text chat remains available"; }
        try { protocol.start(SystemClock.elapsedRealtime()); scheduleTick(); }
        catch (Exception e) { reset(); error("Could not initialize message history"); }
    }
    private void initializeFiles(WebRtcTransport bound) {
        UUID boundSession = sessionId;
        String peer = remoteFingerprint;
        long generation=historyGeneration;
        FileTransfers[] instance = new FileTransfers[1];
        instance[0] = new FileTransfers(context,boundSession,(file,frame,callback) -> {
            if (terminated.get()) { callback.accept(false); return; }
            worker.post(() -> {
            if (transport != bound || !verified || !ready) { callback.accept(false); return; }
            bound.send(file,frame.encode(),callback);
        }); },new FileTransfers.Listener() {
            @Override public void onOffer(FileOffer offer) { if (terminated.get()) return; worker.post(() -> {
                if (transfers != instance[0]) return;
                incomingOffer=offer;changed();
            }); }
            @Override public void onUpdate(FileOffer offer,boolean outgoing,long progress,String state,String uri,String hash) { if (terminated.get()) return; worker.post(() -> {
                if (generation!=historyGeneration) return;
                try { database.transfer(offer.id,peer,offer.name,outgoing,offer.size,progress,state,uri,hash); }
                catch (RuntimeException e) { fileStatus="File metadata could not be saved";changed();return; }
                if (transfers == instance[0]) { fileStatus=offer.name+" — "+state+" ("+progress+"/"+offer.size+" bytes)";refreshHistory(); }
            }); }
            @Override public void onError(String state) { if (terminated.get()) return; worker.post(() -> { if (transfers == instance[0]) { fileStatus=state;changed(); } }); }
        });
        transfers=instance[0]; filesEnabled=true;
    }
    public void offerFile(Uri uri) { execute(() -> { if (ready && transfers!=null) transfers.offer(uri); }); }
    public void acceptFile(UUID id,Uri uri) { execute(() -> {
        if (ready && transfers!=null) transfers.accept(id,uri);
        else {
            boolean deleted=false;
            try { if (uri!=null && "content".equals(uri.getScheme())) deleted=android.provider.DocumentsContract.deleteDocument(context.getContentResolver(),uri); }
            catch (Exception ignored) { }
            fileStatus=deleted?"Connection expired; unused destination removed":"Connection expired; remove the unused destination manually";
            changed();
        }
    }); }
    public void cancelTransfers() { execute(() -> { if (transfers!=null) { fileStatus="Cancelling transfers; a blocked provider may delay cleanup";changed();transfers.cancel(); } }); }
    private final Runnable tick = () -> {
        if (protocol == null) return;
        try {
            if (!protocol.ready() && SystemClock.elapsedRealtime()-authenticationStarted>=300000) {
                reset(); error("Peer verification/negotiation timed out — reconnect"); return;
            }
            protocol.tick(SystemClock.elapsedRealtime()); scheduleTick();
        }
        catch (Exception e) { reset(); error("Messaging failed; reconnect explicitly"); }
    };
    private void scheduleTick() {
        worker.removeCallbacks(tick);
        if (protocol != null && protocol.needsTick()) worker.postDelayed(tick,1000);
    }
    private void refreshHistory() {
        String peer = remoteFingerprint.isEmpty()?trust.trusted():remoteFingerprint;
        try {
            MessageDatabase.Page page=database.page(peer,historyBefore);oldestRow=page.oldest;hasOlderHistory=page.more;
            java.util.List<String> rows=new java.util.ArrayList<>(page.rows);
            if (historyBefore==Long.MAX_VALUE) rows.addAll(database.transfers(peer));
            history=java.util.Collections.unmodifiableList(rows);
        } catch (RuntimeException e) { history=java.util.Collections.emptyList();status="History unavailable — storage error"; }
        changed();
    }
    public void retryMessages() { execute(() -> { if (ready && protocol != null) { protocol.retry(SystemClock.elapsedRealtime()); scheduleTick(); } }); }
    public void loadOlder() { execute(() -> { if (hasOlderHistory) { historyBefore=oldestRow;refreshHistory(); } }); }
    public void latestHistory() { execute(() -> { historyBefore=Long.MAX_VALUE;refreshHistory(); }); }
    public void clearHistory() { execute(() -> { historyGeneration++;reset();database.clearHistory(trust.trusted()); refreshHistory(); }); }
    public void clearEverything() { execute(() -> { historyGeneration++;reset();trust.clear();database.clearAll();identity.reset();localFingerprint="";history=java.util.Collections.emptyList();changed(); }); }
    void networkChanged() { execute(() -> {
        if (transport!=null || phonePairingActive) { reset();error("Network changed — reconnect manually with fresh signaling"); }
    }); }
    private final Runnable backgroundClose=this::expireBackground;
    private void expireBackground() {
        if (foregroundPolicy.expired(SystemClock.elapsedRealtime()) && (transport!=null || phonePairingActive)) {
            reset();error("Disconnected after background grace — pair again");
        }
    }
    public void background() { execute(() -> {
        foregroundPolicy.background(SystemClock.elapsedRealtime());
        worker.removeCallbacks(backgroundClose);worker.postDelayed(backgroundClose,foregroundPolicy.grace());
    }); }
    public void foreground() { execute(() -> {
        worker.removeCallbacks(backgroundClose);
        if (foregroundPolicy.foreground(SystemClock.elapsedRealtime()) && (transport!=null || phonePairingActive)) {
            reset();error("Background session expired — pair again");
        }
    }); }
    public void shutdown() {
        if (terminated.compareAndSet(false,true)) worker.post(() -> {
            reset();
            if (connectivity!=null) { try { connectivity.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) { } }
            database.close();worker.getLooper().quitSafely();
        });
    }
    public void loadNotices(java.util.function.Consumer<String> result) { execute(() -> {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        out.write("MicroMiniChat — GPL-3.0\nSource: https://github.com/PavelSvistunoff/MicroMiniChat\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String file:new String[]{"LICENSE","NOTICE"}) {
            try (java.io.InputStream input=context.getAssets().open(file)) {
                byte[] chunk=new byte[8192];int count;
                while ((count=input.read(chunk))!=-1) { if (out.size()+count>2*1024*1024) throw new java.io.IOException();out.write(chunk,0,count); }
                out.write('\n');
            }
        }
        String text=out.toString("UTF-8");main.post(() -> result.accept(text));
    }); }
    public void disconnect() { execute(() -> { reset(); changed(); }); }
    private void reset() { reset(false); }
    private void reset(boolean keepPhone) {
        if(!keepPhone) {
            phoneGeneration++;if(phonePairing!=null)phonePairing.close();phonePairing=null;
            phoneInvitation="";phonePairingActive=false;advertisedPhone="";phoneIce=null;
        }
        WebRtcTransport previous=transport;transport=null;
        String peer=remoteFingerprint;
        worker.removeCallbacks(tick);protocol=null;
        if (previous!=null) previous.close();
        if (transfers!=null) { transfers.close();transfers=null; }
        incomingOffer=null;filesEnabled=false;fileStatus="";
        relayAllowed=false;sessionId=null;answerApplied=false;open=false;verified=false;ready=false;
        localSignal="";remoteFingerprint="";status="Disconnected";historyBefore=Long.MAX_VALUE;
        if (previous!=null && !peer.isEmpty()) {
            try { database.interrupt(peer); } catch (RuntimeException e) { status="Disconnected — storage recovery failed"; }
        }
        refreshHistory();
    }
}
