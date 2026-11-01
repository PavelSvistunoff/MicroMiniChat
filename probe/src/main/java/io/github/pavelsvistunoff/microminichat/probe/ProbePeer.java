package io.github.pavelsvistunoff.microminichat.probe;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

/** Disposable, unauthenticated transport experiment. Never sends user content. */
public final class ProbePeer implements AutoCloseable {
    public interface Listener {
        void onState(String state);
        void onLocalSdp(boolean offer, String sdp);
        void onRoundTrip();
    }

    private static final byte[] REQUEST = new byte[]{77, 77, 67, 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final byte[] RESPONSE = new byte[]{77, 77, 67, 2, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static boolean initialized;
    private final HandlerThread thread = new HandlerThread("p2p-probe");
    private final Handler owner;
    private final Context context;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger queuedFrames = new AtomicInteger();
    private final AtomicBoolean badFrame = new AtomicBoolean();
    private final CountDownLatch disposed = new CountDownLatch(1);
    private PeerConnectionFactory factory;
    private PeerConnection connection;
    private DataChannel channel;
    private boolean started;
    private boolean offerer;
    private boolean remoteApplied;
    private boolean localSet;
    private boolean gatheringComplete;
    private boolean exported;
    private boolean awaitingResponse;
    private boolean openReported;
    private Runnable deadline;

    public ProbePeer(Context context, Listener listener) {
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

    public void offer(String stun) { post(() -> start(true, stun, null)); }
    public void answer(String stun, String sdp) { post(() -> start(false, stun, sdp)); }

    public void acceptAnswer(String sdp) {
        post(() -> {
            if (!offerer || !exported || remoteApplied || !ProbeRules.validSdp(sdp)) {
                fail("INVALID_ANSWER_OR_STATE");
                return;
            }
            remoteApplied = true;
            timeout(30_000, "CONNECT_TIMEOUT");
            connection.setRemoteDescription(observer(() -> listener.onState("CONNECTING")),
                    new SessionDescription(SessionDescription.Type.ANSWER, sdp));
        });
    }

    private static synchronized void initialize(Context context) {
        if (initialized) return;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .setNativeLibraryName("jingle_peerconnection_so")
                .setInjectableLogger((message, severity, tag) -> { }, Logging.Severity.LS_NONE)
                .createInitializationOptions());
        initialized = true;
    }

    private void start(boolean makeOffer, String stun, String sdp) {
        if (started || !ProbeRules.validStun(stun) || (!makeOffer && !ProbeRules.validSdp(sdp))) {
            fail("INVALID_INPUT_OR_STATE");
            return;
        }
        started = true;
        offerer = makeOffer;
        initialize(context);
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        java.util.List<PeerConnection.IceServer> servers = stun.isEmpty()
                ? Collections.emptyList()
                : Collections.singletonList(PeerConnection.IceServer.builder(stun).createIceServer());
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
        connection = factory.createPeerConnection(config, new PeerObserver());
        if (connection == null) { fail("CREATE_PEER_FAILED"); return; }
        timeout(30_000, "GATHER_TIMEOUT");
        listener.onState("GATHERING");
        if (makeOffer) {
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            init.protocol = "microminichat-probe-v1";
            attach(connection.createDataChannel("probe", init));
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
        if (sdp == null || !ProbeRules.validSdp(sdp.description)) { fail("INVALID_LOCAL_SDP"); return; }
        exported = true;
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            timeout(120_000, "PAIRING_TIMEOUT");
            listener.onState(offerer ? "WAITING_FOR_ANSWER" : "CONNECTING");
        }
        listener.onLocalSdp(offerer, sdp.description);
        if (channel != null) channelState();
    }

    private void attach(DataChannel next) {
        if (next == null) { fail("CREATE_CHANNEL_FAILED"); return; }
        if (channel != null || !"probe".equals(next.label())) {
            next.close();
            next.dispose();
            fail("UNEXPECTED_CHANNEL");
            return;
        }
        channel = next;
        channel.registerObserver(new DataChannel.Observer() {
            @Override public void onBufferedAmountChange(long previous) { }
            @Override public void onStateChange() { post(ProbePeer.this::channelState); }
            @Override public void onMessage(DataChannel.Buffer buffer) {
                // Callback-owned ByteBuffer must be copied before returning, with a strict bound.
                if (!buffer.binary || buffer.data.remaining() != REQUEST.length) {
                    rejectFrame();
                    return;
                }
                if (queuedFrames.incrementAndGet() > 32) {
                    queuedFrames.decrementAndGet();
                    rejectFrame();
                    return;
                }
                byte[] copy = new byte[REQUEST.length];
                buffer.data.get(copy);
                post(() -> {
                    try { receive(copy); }
                    finally { queuedFrames.decrementAndGet(); }
                });
            }
        });
        channelState();
    }

    private void rejectFrame() {
        if (badFrame.compareAndSet(false, true)) post(() -> fail("INVALID_OR_FLOODED_PROBE_FRAME"));
    }

    private void channelState() {
        if (channel.state() == DataChannel.State.OPEN && exported && !openReported) {
            openReported = true;
            if (!awaitingResponse) cancelTimeout();
            listener.onState("OPEN_UNAUTHENTICATED");
        } else if (channel.state() == DataChannel.State.CLOSED) {
            fail("REMOTE_CLOSED");
        }
    }

    public void sendSynthetic() {
        post(() -> {
            if (awaitingResponse) { listener.onState("PROBE_ALREADY_PENDING"); return; }
            if (send(REQUEST)) {
                awaitingResponse = true;
                timeout(10_000, "PROBE_RESPONSE_TIMEOUT");
            } else listener.onState("NOT_OPEN_OR_BACKPRESSURE");
        });
    }

    private boolean send(byte[] data) {
        return channel != null && channel.state() == DataChannel.State.OPEN
                && ProbeRules.canSend(channel.bufferedAmount(), data.length)
                && channel.send(new DataChannel.Buffer(ByteBuffer.wrap(data), true));
    }

    private void receive(byte[] data) {
        if (Arrays.equals(REQUEST, data)) {
            if (!send(RESPONSE)) fail("RESPONSE_BACKPRESSURE");
        } else if (Arrays.equals(RESPONSE, data) && awaitingResponse) {
            awaitingResponse = false;
            cancelTimeout();
            listener.onRoundTrip();
        } else fail("INVALID_PROBE_FRAME");
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
