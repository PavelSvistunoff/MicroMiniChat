package io.github.pavelsvistunoff.microminichat.pairing;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.security.*;
import java.util.Base64;

/** Durable single-use capability consumption; the exported secret is never stored here. */
public final class PairingCapabilities {
    private final SharedPreferences preferences;
    public PairingCapabilities(Context context, String name) { preferences = context.getSharedPreferences(name, 0); }
    private static byte[] hash(byte[] secret) throws IOException {
        if (secret == null || secret.length != 32) throw new IOException("CAPABILITY_SIZE");
        try { return MessageDigest.getInstance("SHA-256").digest(secret); }
        catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    public synchronized void activate(TorInvite invite) throws IOException {
        if (!preferences.edit().putString("hash", Base64.getEncoder().encodeToString(hash(invite.capability())))
                .putLong("expires", invite.expires).putLong("elapsedDeadline", android.os.SystemClock.elapsedRealtime() + TorInvite.LIFETIME).commit()) throw new IOException("CAPABILITY_COMMIT");
    }
    public synchronized boolean consume(byte[] secret, long now) throws IOException {
        String saved = preferences.getString("hash", "");
        if (saved.isEmpty() || now < preferences.getLong("expires", 0) - TorInvite.LIFETIME
                || now >= preferences.getLong("expires", 0)
                || android.os.SystemClock.elapsedRealtime() >= preferences.getLong("elapsedDeadline", 0)
                || !MessageDigest.isEqual(hash(secret), Base64.getDecoder().decode(saved))) return false;
        // Remove durably before returning success, including after a process crash/lost response.
        cancel();
        return true;
    }
    public synchronized void cancel() throws IOException {
        if (!preferences.edit().clear().commit()) throw new IOException("CAPABILITY_CANCEL");
    }
}
