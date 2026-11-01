package io.github.pavelsvistunoff.microminichat.probe;

import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

/** In-process native integration only: not evidence of two-device internet connectivity. */
public final class ProbeIntegrationTest {
    @Test public void malformedOfferFailsAndDisposalIsIdempotent() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<String> state = new AtomicReference<>();
        ProbePeer peer = new ProbePeer(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new ProbePeer.Listener() {
                    @Override public void onState(String value) { state.set(value); failed.countDown(); }
                    @Override public void onLocalSdp(boolean offer, String sdp) { fail("Must not export malformed input"); }
                    @Override public void onRoundTrip() { fail("Must not exchange data"); }
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

    @Test public void nativeDataChannelRoundTripAndRepeatCleanup() throws Exception {
        for (int run = 0; run < 2; run++) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch response = new CountDownLatch(1);
            AtomicReference<String> failure = new AtomicReference<>();
            ProbePeer[] peers = new ProbePeer[2];
            for (int index = 0; index < 2; index++) {
                final int side = index;
                AtomicBoolean opened = new AtomicBoolean();
                peers[index] = new ProbePeer(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        new ProbePeer.Listener() {
                            @Override public void onState(String state) {
                                if (state.equals("OPEN_UNAUTHENTICATED") && opened.compareAndSet(false, true)) ready.countDown();
                                if (state.startsWith("FAILED_")) {
                                    failure.compareAndSet(null, state);
                                    while (ready.getCount() > 0) ready.countDown();
                                    response.countDown();
                                }
                            }
                            @Override public void onLocalSdp(boolean offer, String sdp) {
                                if (offer) peers[1].answer("", sdp);
                                else peers[0].acceptAnswer(sdp);
                            }
                            @Override public void onRoundTrip() { if (side == 0) response.countDown(); }
                        });
            }
            try {
                peers[0].offer("");
                assertTrue("Both channels must open", ready.await(45, TimeUnit.SECONDS));
                assertNull(failure.get(), failure.get());
                peers[0].sendSynthetic();
                assertTrue("Synthetic response must arrive", response.await(15, TimeUnit.SECONDS));
                assertNull(failure.get(), failure.get());
            } finally {
                for (ProbePeer peer : peers) if (peer != null) peer.close();
                for (ProbePeer peer : peers) if (peer != null) assertTrue("Native disposal", peer.awaitDisposed(10));
            }
        }
    }
}
