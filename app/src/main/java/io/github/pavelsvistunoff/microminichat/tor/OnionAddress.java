package io.github.pavelsvistunoff.microminichat.tor;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

/** Canonical v3 address including version and SHA3 checksum validation. Never resolves DNS. */
public final class OnionAddress {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
    private OnionAddress() { }
    public static String require(String address) {
        if (address == null || !address.matches("[a-z2-7]{56}\\.onion")) throw new IllegalArgumentException("ONION_ADDRESS");
        byte[] decoded = new byte[35];
        int buffer = 0, bits = 0, offset = 0;
        for (int i = 0; i < 56; i++) {
            buffer = (buffer << 5) | ALPHABET.indexOf(address.charAt(i));
            bits += 5;
            if (bits >= 8) { bits -= 8; decoded[offset++] = (byte) (buffer >>> bits); }
        }
        if (decoded[34] != 3) throw new IllegalArgumentException("ONION_VERSION");
        org.bouncycastle.crypto.digests.SHA3Digest hash = new org.bouncycastle.crypto.digests.SHA3Digest(256);
        byte[] domain = ".onion checksum".getBytes(StandardCharsets.US_ASCII);
        hash.update(domain, 0, domain.length);
        hash.update(decoded, 0, 32);
        hash.update((byte) 3);
        byte[] checksum = new byte[32];
        hash.doFinal(checksum, 0);
        if (!MessageDigest.isEqual(Arrays.copyOfRange(decoded, 32, 34), Arrays.copyOf(checksum, 2)))
            throw new IllegalArgumentException("ONION_CHECKSUM");
        return address;
    }
}
