package io.github.pavelsvistunoff.microminichat.net;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Bounds for the disposable raw-SDP experiment, not the product signaling codec. */
public final class TransportPolicy {
    public static final int MAX_SDP_BYTES = 256 * 1024;
    public static final int MAX_BUFFERED_BYTES = 256 * 1024;
    private static final Pattern STUN = Pattern.compile(
            "stun:(?:[a-zA-Z0-9](?:[a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?|\\[[0-9a-fA-F:]+\\])(?::([0-9]{1,5}))?");

    private TransportPolicy() { }

    public static boolean validSdp(String sdp) {
        return sdp != null && sdp.length() <= MAX_SDP_BYTES
                && sdp.getBytes(StandardCharsets.UTF_8).length <= MAX_SDP_BYTES
                && sdp.startsWith("v=0\r\n") && sdp.contains("\r\nm=application ")
                && !sdp.contains("\r\nm=audio ") && !sdp.contains("\r\nm=video ")
                && !sdp.contains("\u0000");
    }

    public static boolean validStun(String value) {
        if (value == null || value.length() > 300) return false;
        if (value.isEmpty()) return true; // Host candidates only; no silent STUN service.
        java.util.regex.Matcher match = STUN.matcher(value);
        if (!match.matches()) return false;
        String port = match.group(1);
        return port == null || (Integer.parseInt(port) >= 1 && Integer.parseInt(port) <= 65535);
    }

    public static boolean canSend(long buffered, int bytes) {
        return buffered >= 0 && bytes > 0 && bytes <= 64 * 1024
                && buffered <= MAX_BUFFERED_BYTES - bytes;
    }
}
