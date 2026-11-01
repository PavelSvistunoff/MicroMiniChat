package io.github.pavelsvistunoff.microminichat.net;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.webrtc.RtcCertificatePem;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/** Direct-only WebRTC adapter. Authentication and framing belong to its caller. */
public final class WebRtcTransport implements AutoCloseable {
    public interface Listener {
        void onState(String state);
        void onLocalSdp(boolean offer, String sdp);
        void onFrame(boolean fileChannel, byte[] bytes);
    }

    private static boolean initialized;
    private final HandlerThread thread = new HandlerThread("p2p-probe");
    private final Handler owner;
    private final Context context;
    private final RtcCertificatePem certificate;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger queuedFrames = new AtomicInteger();
    private final AtomicBoolean badFrame = new AtomicBoolean();
    private final CountDownLatch disposed = new CountDownLatch(1);
    private PeerConnectionFactory factory;
    private PeerConnection connection;
    private DataChannel channel;
    private DataChannel files;
    private boolean started;
    private boolean offerer;
    private boolean remoteApplied;
    private boolean localSet;
    private boolean gatheringComplete;
    private boolean exported;
    private boolean openReported;
    private Runnable deadline;

    public WebRtcTransport(Context context, RtcCertificatePem certificate, Listener listener) {
        this.certificate = certificate;
        this.context = context.getApplicationContext();
        this.listener = listener;
        thread.start();
        owner = new Handler(thread.getLooper());
    }

    private void post(Runnable action) {
        if (!closed.get()) owner.post(() -> {
            if (!closed.get()) {
                try { action.run(); }
                catch (RuntimeException | LinkageError error) { fail("NATIVE_OR_STATE_ERROR_" + error.getClass().getSimpleName()); }
            }
        });
    }

    private boolean relayRequired;

    public void offer(String stun) { offer(IceConfig.direct(stun)); }
    public void offer(IceConfig ice) { post(() -> start(true, ice, null)); }
    public void answer(String stun, String sdp) { answer(IceConfig.direct(stun), sdp); }
    public void answer(IceConfig ice, String sdp) { post(() -> start(false, ice, sdp)); }

    public void acceptAnswer(String sdp) {
        post(() -> {
            if (!offerer || !exported || remoteApplied || !TransportPolicy.validSdp(sdp)) {
                fail("INVALID_ANSWER_OR_STATE");
                return;
            }
            remoteApplied = true;
            timeout(30_000, "CONNECT_TIMEOUT");
            connection.setRemoteDescription(observer(() -> listener.onState("CONNECTING")),
                    new SessionDescription(SessionDescription.Type.ANSWER, sdp));
        });
    }

    public static synchronized void initialize(Context context) {
        if (initialized) return;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .setNativeLibraryName("jingle_peerconnection_so")
                .setInjectableLogger((message, severity, tag) -> { }, Logging.Severity.LS_NONE)
                .createInitializationOptions());
        initialized = true;
    }

    private void start(boolean makeOffer, IceConfig ice, String sdp) {
        if (started || ice == null || (!makeOffer && !TransportPolicy.validSdp(sdp))) {
            fail("INVALID_INPUT_OR_STATE");
            return;
        }
        started = true;
        offerer = makeOffer;
        initialize(context);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        java.util.List<PeerConnection.IceServer> servers = new java.util.ArrayList<>();
        if (!ice.stun.isEmpty()) servers.add(PeerConnection.IceServer.builder(ice.stun).createIceServer());
        if (ice.allowsRelay()) servers.add(PeerConnection.IceServer.builder(ice.turn)
                .setUsername(ice.username).setPassword(ice.password).createIceServer());
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(servers);
        config.certificate = certificate;
        relayRequired = ice.relayOnly;
        config.iceTransportsType = ice.relayOnly ? PeerConnection.IceTransportsType.RELAY : PeerConnection.IceTransportsType.ALL;
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
        connection = factory.createPeerConnection(config, new PeerObserver());
        if (connection == null) { fail("CREATE_PEER_FAILED"); return; }
        timeout(30_000, "GATHER_TIMEOUT");
        listener.onState("GATHERING");
        if (makeOffer) {
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            init.protocol = "microminichat-v1";
            attach(connection.createDataChannel("control", init));
            attach(connection.createDataChannel("files", init));
            connection.createOffer(createObserver(), new MediaConstraints());
        } else {
            remoteApplied = true;
            connection.setRemoteDescription(observer(() ->
                    connection.createAnswer(createObserver(), new MediaConstraints())),
                    new SessionDescription(SessionDescription.Type.OFFER, sdp));
        }
    }

    private SdpObserver createObserver() {
        return new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                post(() -> connection.setLocalDescription(observer(() -> {
                    localSet = true;
                    exportIfComplete();
                }), sdp));
            }
            @Override public void onSetSuccess() { }
            @Override public void onCreateFailure(String ignored) { post(() -> fail("SDP_CREATE_FAILED")); }
            @Override public void onSetFailure(String ignored) { post(() -> fail("SDP_SET_FAILED")); }
        };
    }

    private SdpObserver observer(Runnable success) {
        return new SdpObserver() {
            @Override public void onSetSuccess() { post(success); }
            @Override public void onCreateSuccess(SessionDescription ignored) { }
            @Override public void onCreateFailure(String ignored) { post(() -> fail("SDP_CREATE_FAILED")); }
            @Override public void onSetFailure(String ignored) { post(() -> fail("SDP_SET_FAILED")); }
        };
    }

    private void exportIfComplete() {
        if (!localSet || !gatheringComplete || exported) return;
        SessionDescription sdp = connection.getLocalDescription();
        if (sdp == null || !TransportPolicy.validSdp(sdp.description)) { fail("INVALID_LOCAL_SDP"); return; }
        if (relayRequired && !java.util.regex.Pattern.compile("(?m)^a=candidate:.* typ relay(?: |\r?$)").matcher(sdp.description).find()) {
            fail("TURN_ALLOCATION_FAILED"); return;
        }
        exported = true;
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            timeout(600_000, "PAIRING_TIMEOUT");
            listener.onState(offerer ? "WAITING_FOR_ANSWER" : "CONNECTING");
        }
        listener.onLocalSdp(offerer, sdp.description);
        if (channel != null) channelState();
    }

    private void attach(DataChannel next) {
        if (next == null) { fail("CREATE_CHANNEL_FAILED"); return; }
        boolean isFile = "files".equals(next.label());
        if ((!isFile && !"control".equals(next.label())) || (isFile ? files != null : channel != null)) {
            next.close(); next.dispose(); fail("UNEXPECTED_CHANNEL"); return;
        }
        if (isFile) files = next; else channel = next;
        next.registerObserver(new DataChannel.Observer() {
            @Override public void onBufferedAmountChange(long previous) { }
            @Override public void onStateChange() { post(WebRtcTransport.this::channelState); }
            @Override public void onMessage(DataChannel.Buffer buffer) {
                int length = buffer.data.remaining();
                if (!buffer.binary || length < 1 || length > 65536) { rejectFrame(); return; }
                if (queuedFrames.addAndGet(length) > TransportPolicy.MAX_BUFFERED_BYTES) {
                    queuedFrames.addAndGet(-length); rejectFrame(); return;
                }
                byte[] copy = new byte[length];
                buffer.data.get(copy);
                post(() -> {
                    try { listener.onFrame(isFile, copy); }
                    finally { queuedFrames.addAndGet(-length); }
                });
            }
        });
        channelState();
    }

    private void rejectFrame() {
        if (badFrame.compareAndSet(false, true)) post(() -> fail("INVALID_OR_FLOODED_FRAME"));
    }

    private void channelState() {
        if (channel == null || files == null) return;
        if (channel.state() == DataChannel.State.OPEN && files.state() == DataChannel.State.OPEN
                && exported && !openReported) {
            openReported = true;
            cancelTimeout();
            listener.onState("OPEN");
        } else if (channel.state() == DataChannel.State.CLOSED || files.state() == DataChannel.State.CLOSED) {
            fail("REMOTE_CLOSED");
        }
    }

    public void send(boolean file, byte[] data, Consumer<Boolean> result) {
        if (closed.get() || data == null || data.length < 1 || data.length > 65536) {
            result.accept(false); return;
        }
        post(() -> {
            DataChannel target = file ? files : channel;
            long limit = file ? 128 * 1024 : 64 * 1024;
            boolean accepted = target != null && target.state() == DataChannel.State.OPEN
                    && target.bufferedAmount() <= limit - data.length
                    && target.send(new DataChannel.Buffer(ByteBuffer.wrap(data), true));
            result.accept(accepted);
        });
    }

    private void timeout(long millis, String code) {
        cancelTimeout();
        deadline = () -> fail(code);
        owner.postDelayed(deadline, millis);
    }

    private void cancelTimeout() {
        if (deadline != null) owner.removeCallbacks(deadline);
        deadline = null;
    }

    private void fail(String code) {
        if (closed.compareAndSet(false, true)) {
            listener.onState("FAILED_" + code);
            dispose();
        }
    }

    private void dispose() {
        cancelTimeout();
        try {
            if (channel != null) { channel.unregisterObserver(); channel.close(); channel.dispose(); channel = null; }
            if (files != null) { files.unregisterObserver(); files.close(); files.dispose(); files = null; }
            if (connection != null) { connection.close(); connection.dispose(); connection = null; }
            if (factory != null) { factory.dispose(); factory = null; }
        } finally {
            owner.removeCallbacksAndMessages(null);
            thread.quitSafely();
            disposed.countDown();
        }
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) owner.post(() -> {
            try { dispose(); } finally { listener.onState("CLOSED"); }
        });
    }

    public boolean awaitDisposed(long seconds) throws InterruptedException {
        return disposed.await(seconds, TimeUnit.SECONDS);
    }

    private final class PeerObserver implements PeerConnection.Observer {
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
            if (state == PeerConnection.PeerConnectionState.FAILED) post(() -> fail("DTLS_OR_CONNECTION_FAILED"));
        }
        @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
            post(() -> {
                switch (state) {
                    case FAILED: fail("ICE_FAILED"); break;
                    case DISCONNECTED:
                        openReported = false;
                        listener.onState("DISCONNECTED");
                        timeout(10_000, "DISCONNECTED_TIMEOUT");
                        break;
                    case CONNECTED:
                    case COMPLETED:
                        if (channel != null && channel.state() == DataChannel.State.OPEN) channelState();
                        break;
                    case CLOSED: fail("ICE_CLOSED"); break;
                    default: break;
                }
            });
        }
        @Override public void onIceConnectionReceivingChange(boolean receiving) { }
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) post(() -> {
                gatheringComplete = true;
                exportIfComplete();
            });
        }
        @Override public void onIceCandidate(IceCandidate ignored) { }
        @Override public void onIceCandidatesRemoved(IceCandidate[] ignored) { }
        @Override public void onAddStream(MediaStream stream) { }
        @Override public void onRemoveStream(MediaStream stream) { }
        @Override public void onDataChannel(DataChannel next) { post(() -> attach(next)); }
        @Override public void onRenegotiationNeeded() { }
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) { }
    }
}
