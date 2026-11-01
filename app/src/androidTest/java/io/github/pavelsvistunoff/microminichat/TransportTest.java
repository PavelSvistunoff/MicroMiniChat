package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.net.WebRtcTransport;

import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

/** In-process native integration only: not evidence of two-device internet connectivity. */
public final class TransportTest {
    @Test public void malformedOfferFailsAndDisposalIsIdempotent() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<String> state = new AtomicReference<>();
        WebRtcTransport peer = new WebRtcTransport(InstrumentationRegistry.getInstrumentation().getTargetContext(), null,
                new WebRtcTransport.Listener() {
                    @Override public void onState(String value) { state.set(value); failed.countDown(); }
                    @Override public void onLocalSdp(boolean offer, String sdp) { fail("Must not export malformed input"); }
                    @Override public void onFrame(boolean file, byte[] data) { fail("Must not exchange data"); }
                });
        try {
            peer.answer("", "malformed");
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertEquals("FAILED_INVALID_INPUT_OR_STATE", state.get());
        } finally {
            peer.close();
            peer.close();
            assertTrue(peer.awaitDisposed(5));
        }
    }

    @Test public void wrongDtlsFingerprintNeverOpensDataChannels() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WebRtcTransport[] peers = new WebRtcTransport[2];
        CountDownLatch rejected = new CountDownLatch(1);
        AtomicBoolean opened = new AtomicBoolean();
        for (int i = 0; i < 2; i++) {
            peers[i] = new WebRtcTransport(context, null, new WebRtcTransport.Listener() {
                @Override public void onState(String state) {
                    if (state.equals("OPEN")) { opened.set(true); rejected.countDown(); }
                    if (state.startsWith("FAILED_")) rejected.countDown();
                }
                @Override public void onLocalSdp(boolean offer, String sdp) {
                    if (offer) peers[1].answer("", sdp);
                    else {
                        String actual = io.github.pavelsvistunoff.microminichat.signal.Signal.fingerprint(sdp);
                        String wrong = actual.startsWith("00:") ? "FF" + actual.substring(2) : "00" + actual.substring(2);
                        peers[0].acceptAnswer(sdp.replace(actual, wrong));
                    }
                }
                @Override public void onFrame(boolean file, byte[] bytes) { opened.set(true); rejected.countDown(); }
            });
        }
        try {
            peers[0].offer("");
            assertTrue("DTLS mismatch must fail", rejected.await(40, TimeUnit.SECONDS));
            assertFalse("Mismatched DTLS certificate must never open", opened.get());
        } finally {
            for (WebRtcTransport peer : peers) if (peer != null) peer.close();
            for (WebRtcTransport peer : peers) if (peer != null) assertTrue(peer.awaitDisposed(10));
        }
    }

    @Test public void nativeDataChannelRoundTripAndRepeatCleanup() throws Exception {
        for (int run = 0; run < 2; run++) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch response = new CountDownLatch(1);
            AtomicReference<String> failure = new AtomicReference<>();
            WebRtcTransport[] peers = new WebRtcTransport[2];
            for (int index = 0; index < 2; index++) {
                final int side = index;
                AtomicBoolean opened = new AtomicBoolean();
                peers[index] = new WebRtcTransport(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        new io.github.pavelsvistunoff.microminichat.security.IdentityStore(
                            InstrumentationRegistry.getInstrumentation().getTargetContext(), "transport-test-" + side).load(),
                        new WebRtcTransport.Listener() {
                            @Override public void onState(String state) {
                                if (state.equals("OPEN") && opened.compareAndSet(false, true)) ready.countDown();
                                if (state.startsWith("FAILED_")) {
                                    failure.compareAndSet(null, state);
                                    while (ready.getCount() > 0) ready.countDown();
                                    response.countDown();
                                }
                            }
                            @Override public void onLocalSdp(boolean offer, String sdp) {
                                io.github.pavelsvistunoff.microminichat.signal.Signal payload =
                                        io.github.pavelsvistunoff.microminichat.signal.Signal.decode(
                                            new io.github.pavelsvistunoff.microminichat.signal.Signal(offer, new java.util.UUID(1,2), sdp).encode());
                                if (payload.offer) peers[1].answer("", payload.sdp);
                                else peers[0].acceptAnswer(payload.sdp);
                            }
                            @Override public void onFrame(boolean file, byte[] data) {
                                if (side == 1) peers[1].send(false, data, accepted -> { });
                                else if (java.util.Arrays.equals(data, new byte[]{1,2,3,4})) response.countDown();
                            }
                        });
            }
            try {
                peers[0].offer("");
                assertTrue("Both channels must open", ready.await(45, TimeUnit.SECONDS));
                assertNull(failure.get(), failure.get());
                peers[0].send(false, new byte[]{1,2,3,4}, accepted -> { });
                assertTrue("Synthetic response must arrive", response.await(15, TimeUnit.SECONDS));
                assertNull(failure.get(), failure.get());
            } finally {
                for (WebRtcTransport peer : peers) if (peer != null) peer.close();
                for (WebRtcTransport peer : peers) if (peer != null) assertTrue("Native disposal", peer.awaitDisposed(10));
            }
        }
    }
}
