package io.github.pavelsvistunoff.microminichat.probe;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProbeRulesTest {
    private static final String SDP = "v=0\r\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";

    @Test public void rejectsOversizeMultibyteAndMediaPayloads() {
        assertTrue(ProbeRules.validSdp(SDP));
        assertFalse(ProbeRules.validSdp(null));
        assertFalse(ProbeRules.validSdp("malformed"));
        assertFalse(ProbeRules.validSdp(SDP + "\r\nm=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertFalse(ProbeRules.validSdp(SDP + "\u0000"));
        assertFalse(ProbeRules.validSdp(SDP + "é".repeat(ProbeRules.MAX_SDP_BYTES / 2)));
    }

    @Test public void sizeBoundariesAreInUtf8Bytes() {
        String exact = SDP + "x".repeat(ProbeRules.MAX_SDP_BYTES - SDP.length());
        assertTrue(ProbeRules.validSdp(exact));
        assertFalse(ProbeRules.validSdp(exact + "x"));
    }

    @Test public void stunRequiresExplicitSingleDiscoveryEndpoint() {
        assertTrue(ProbeRules.validStun(""));
        assertTrue(ProbeRules.validStun("stun:example.org:3478"));
        assertTrue(ProbeRules.validStun("stun:[2001:db8::1]:3478"));
        for (String invalid : new String[]{"turn:example.org", "https://example.org", "stun:user@host",
                "stun:example.org:0", "stun:example.org:65536", "stun:example.org\r\n", "stun:"}) {
            assertFalse(invalid, ProbeRules.validStun(invalid));
        }
    }

    @Test public void backpressureRejectsOverflowAndInvalidSizes() {
        assertTrue(ProbeRules.canSend(ProbeRules.MAX_BUFFERED_BYTES - 16, 16));
        assertFalse(ProbeRules.canSend(ProbeRules.MAX_BUFFERED_BYTES - 15, 16));
        assertFalse(ProbeRules.canSend(Long.MAX_VALUE, 16));
        assertFalse(ProbeRules.canSend(-1, 16));
        assertFalse(ProbeRules.canSend(0, 0));
        assertFalse(ProbeRules.canSend(0, 33));
    }
}
