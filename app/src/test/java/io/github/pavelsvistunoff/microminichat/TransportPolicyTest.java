package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.net.TransportPolicy;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TransportPolicyTest {
    private static final String SDP = "v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";

    @Test public void rejectsOversizeMultibyteAndMediaPayloads() {
        assertTrue(TransportPolicy.validSdp(SDP));
        assertFalse(TransportPolicy.validSdp(null));
        assertFalse(TransportPolicy.validSdp("malformed"));
        assertFalse(TransportPolicy.validSdp(SDP + "\r\nm=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertFalse(TransportPolicy.validSdp(SDP + "\u0000"));
        assertFalse(TransportPolicy.validSdp(SDP + "é".repeat(TransportPolicy.MAX_SDP_BYTES / 2)));
    }

    @Test public void sizeBoundariesAreInUtf8Bytes() {
        String exact = SDP + "x".repeat(TransportPolicy.MAX_SDP_BYTES - SDP.length());
        assertTrue(TransportPolicy.validSdp(exact));
        assertFalse(TransportPolicy.validSdp(exact + "x"));
    }

    @Test public void stunRequiresExplicitSingleDiscoveryEndpoint() {
        assertTrue(TransportPolicy.validStun(""));
        assertTrue(TransportPolicy.validStun("stun:example.org:3478"));
        assertTrue(TransportPolicy.validStun("stun:[2001:db8::1]:3478"));
        for (String invalid : new String[]{"turn:example.org", "https://example.org", "stun:user@host",
                "stun:example.org:0", "stun:example.org:65536", "stun:example.org\r\n", "stun:"}) {
            assertFalse(invalid, TransportPolicy.validStun(invalid));
        }
    }

    @Test public void backpressureRejectsOverflowAndInvalidSizes() {
        assertTrue(TransportPolicy.canSend(TransportPolicy.MAX_BUFFERED_BYTES - 16, 16));
        assertFalse(TransportPolicy.canSend(TransportPolicy.MAX_BUFFERED_BYTES - 15, 16));
        assertFalse(TransportPolicy.canSend(Long.MAX_VALUE, 16));
        assertFalse(TransportPolicy.canSend(-1, 16));
        assertFalse(TransportPolicy.canSend(0, 0));
        assertFalse(TransportPolicy.canSend(0, 65537));
    }
}
