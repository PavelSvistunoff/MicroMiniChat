package io.github.pavelsvistunoff.microminichat.pairing;

import io.github.pavelsvistunoff.microminichat.tor.OnionAddress;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Fixed-size versioned capability; parsing performs no network or trust operation. */
public final class TorInvite {
    public static final String PREFIX = "microminichat://pair/v2#";
    public static final long LIFETIME = 600000;
    public final String onion, fingerprint;
    public final long expires;
    private final byte[] capability;
    public TorInvite(String onion, String fingerprint, byte[] capability, long expires) {
        this.onion = OnionAddress.require(onion);
        if (fingerprint == null || !fingerprint.matches("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")
                || capability == null || capability.length != 32 || expires < 1) throw new IllegalArgumentException("INVITE_FIELDS");
        this.fingerprint = fingerprint;
        this.capability = capability.clone();
        this.expires = expires;
    }
    public static TorInvite create(String onion, String fingerprint, long now) {
        byte[] capability = new byte[32]; new SecureRandom().nextBytes(capability);
        return new TorInvite(onion, fingerprint, capability, Math.addExact(now, LIFETIME));
    }
    public byte[] capability() { return capability.clone(); }
    public String encode() {
        ByteBuffer out = ByteBuffer.allocate(138);
        out.putInt(0x4d544902).put(onion.getBytes(StandardCharsets.US_ASCII));
        for (String octet : fingerprint.split(":")) out.put((byte) Integer.parseInt(octet, 16));
        out.put(capability).putLong(expires);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.array());
    }
    public static TorInvite decode(String text, long now) {
        if (text == null || text.length() > 512 || !text.startsWith(PREFIX)) throw new IllegalArgumentException("INVITE_FORMAT");
        String body = text.substring(PREFIX.length());
        if (!body.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("INVITE_ENCODING");
        byte[] bytes = Base64.getUrlDecoder().decode(body);
        if (bytes.length != 138 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(body))
            throw new IllegalArgumentException("INVITE_LENGTH");
        ByteBuffer in = ByteBuffer.wrap(bytes);
        if (in.getInt() != 0x4d544902) throw new IllegalArgumentException("INVITE_VERSION");
        byte[] address = new byte[62]; in.get(address);
        StringBuilder fingerprint = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            if (i > 0) fingerprint.append(':');
            fingerprint.append(String.format(Locale.ROOT, "%02X", in.get() & 255));
        }
        byte[] capability = new byte[32]; in.get(capability);
        long expires = in.getLong();
        if (expires <= now || expires - now > LIFETIME || now < 0) throw new IllegalArgumentException("INVITE_EXPIRED");
        return new TorInvite(new String(address, StandardCharsets.US_ASCII), fingerprint.toString(), capability, expires);
    }
}
