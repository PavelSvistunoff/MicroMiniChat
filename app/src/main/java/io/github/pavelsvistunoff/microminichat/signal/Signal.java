package io.github.pavelsvistunoff.microminichat.signal;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/** Versioned independent manual signaling. Contains sensitive network addresses. */
public final class Signal {
    public static final int MAX_CHARS = 256 * 1024;
    private static final int MAX_SDP = 180 * 1024;
    private static final Pattern FP = Pattern.compile("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}");
    public final boolean offer;
    public final UUID session;
    public final String sdp;
    public final String fingerprint;

    public Signal(boolean offer, UUID session, String sdp) {
        if (session == null) throw new IllegalArgumentException("INVALID_SESSION");
        this.offer = offer;
        this.session = session;
        this.fingerprint = fingerprint(sdp);
        this.sdp = sdp;
    }

    public static String fingerprint(String sdp) {
        if (sdp == null || sdp.length() > MAX_SDP || sdp.getBytes(StandardCharsets.UTF_8).length > MAX_SDP
                || !sdp.startsWith("v=0\r\n") || !sdp.endsWith("\r\n") || sdp.indexOf(0) >= 0) {
            throw new IllegalArgumentException("INVALID_SDP");
        }
        String stripped = sdp.replace("\r\n", "");
        if (stripped.contains("\r") || stripped.contains("\n")) throw new IllegalArgumentException("INVALID_LINES");
        int media = 0, candidates = 0;
        String fingerprint = null;
        for (String line : sdp.split("\r\n")) {
            if (line.startsWith("m=")) {
                if (!line.startsWith("m=application ")) throw new IllegalArgumentException("MEDIA_NOT_SUPPORTED");
                media++;
            }
            if (line.startsWith("a=candidate:")) {
                String[] parts=line.substring("a=candidate:".length()).trim().split("\\s+");
                if (++candidates>128 || parts.length<8 || !parts[6].equals("typ")
                        || !(parts[7].equals("host") || parts[7].equals("srflx") || parts[7].equals("prflx") || parts[7].equals("relay")))
                    throw new IllegalArgumentException("INVALID_CANDIDATE");
            }
            if (line.startsWith("a=fingerprint:")) {
                String prefix = "a=fingerprint:sha-256 ";
                if (!line.startsWith(prefix)) throw new IllegalArgumentException("FINGERPRINT_ALGORITHM");
                String found = line.substring(prefix.length()).toUpperCase(java.util.Locale.ROOT);
                if (!FP.matcher(found).matches() || (fingerprint != null && !fingerprint.equals(found)))
                    throw new IllegalArgumentException("CONFLICTING_FINGERPRINT");
                fingerprint = found;
            }
        }
        if (media != 1 || fingerprint == null) throw new IllegalArgumentException("MISSING_DTLS_IDENTITY");
        return fingerprint;
    }

    public boolean hasRelayCandidates() {
        for (String line : sdp.split("\r\n")) {
            if (line.startsWith("a=candidate:")) {
                String[] parts=line.substring("a=candidate:".length()).trim().split("\\s+");
                if (parts.length>=8 && parts[7].equals("relay")) return true;
            }
        }
        return false;
    }

    public String encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x4d4d4301);
            out.writeByte(offer ? 1 : 2);
            out.writeLong(session.getMostSignificantBits());
            out.writeLong(session.getLeastSignificantBits());
            byte[] body = sdp.getBytes(StandardCharsets.UTF_8);
            out.writeInt(body.length); out.write(body);
            String encoded = "MMC1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (encoded.length() > MAX_CHARS) throw new IllegalArgumentException("SIGNAL_TOO_LARGE");
            return encoded;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static Signal decode(String payload) {
        if (payload == null || payload.length() > MAX_CHARS) throw new IllegalArgumentException("SIGNAL_TOO_LARGE");
        String value = payload.trim();
        if (!value.startsWith("MMC1:")) throw new IllegalArgumentException("UNSUPPORTED_SIGNAL");
        try {
            byte[] raw = Base64.getUrlDecoder().decode(value.substring(5));
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readInt() != 0x4d4d4301) throw new IOException();
            int role = in.readUnsignedByte();
            if (role != 1 && role != 2) throw new IOException();
            UUID id = new UUID(in.readLong(), in.readLong());
            int length = in.readInt();
            if (length < 1 || length > MAX_SDP || length != in.available()) throw new IOException();
            byte[] body = new byte[length]; in.readFully(body);
            String sdp = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            return new Signal(role == 1, id, sdp);
        } catch (IOException | IllegalArgumentException e) { throw new IllegalArgumentException("INVALID_SIGNAL"); }
    }
}
