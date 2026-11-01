package io.github.pavelsvistunoff.microminichat;

import io.github.pavelsvistunoff.microminichat.signal.Signal;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public final class SignalTest {
    public static final String FINGERPRINT = "AB:".repeat(31) + "AB";
    public static final String SDP = "v=0\r\na=fingerprint:sha-256 " + FINGERPRINT
            + "\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";
    @Test public void roundtripPreservesRoleIdentityAndSession() {
        for (boolean offer : new boolean[]{true, false}) {
            Signal signal = new Signal(offer, UUID.randomUUID(), SDP);
            Signal decoded = Signal.decode(signal.encode());
            assertEquals(signal.session, decoded.session);
            assertEquals(offer, decoded.offer);
            assertEquals(SDP, decoded.sdp);
            assertEquals(FINGERPRINT, decoded.fingerprint);
        }
    }
    @Test public void rejectsMalformedAndUnboundedPayloads() {
        for (String bad : new String[]{"", "MMC2:AAAA", "MMC1:A", "MMC1:@@@", "x".repeat(Signal.MAX_CHARS + 1)}) {
            assertThrows(IllegalArgumentException.class, () -> Signal.decode(bad));
        }
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 1000; i++) {
            byte[] bytes = new byte[random.nextInt(512)]; random.nextBytes(bytes);
            String payload = "MMC1:" + java.util.Base64.getUrlEncoder().encodeToString(bytes);
            assertThrows(IllegalArgumentException.class, () -> Signal.decode(payload));
        }
    }
    @Test public void neverAcceptsAdvertisedTurnRelayCandidates() {
        assertThrows(IllegalArgumentException.class, () -> Signal.fingerprint(SDP+"a=candidate:1 1 UDP 1 192.0.2.1 1234 typ relay\r\n"));
        assertEquals(FINGERPRINT,Signal.fingerprint(SDP+"a=candidate:1 1 UDP 1 192.0.2.1 1234 typ host\r\n"));
    }
    @Test public void rejectsFingerprintAmbiguityAndExtraMedia() {
        assertThrows(IllegalArgumentException.class, () -> Signal.fingerprint(SDP + "a=fingerprint:sha-256 " + "CD:".repeat(31) + "CD\r\n"));
        assertThrows(IllegalArgumentException.class, () -> Signal.fingerprint(SDP + "m=video 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertThrows(IllegalArgumentException.class, () -> Signal.fingerprint(SDP.replace("sha-256", "sha-1")));
        assertThrows(IllegalArgumentException.class, () -> Signal.fingerprint(SDP.replace("\r\n", "\n")));
    }
}
