package io.github.pavelsvistunoff.microminichat.security;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

public final class TrustStore {
    public enum Match { UNVERIFIED, VERIFIED, CHANGED }
    private final SharedPreferences preferences;
    public TrustStore(Context context) { this(context,"peer_trust"); }
    public TrustStore(Context context, String name) { preferences = context.getSharedPreferences(name,0); }
    public synchronized String trusted() { return preferences.getString("fingerprint", ""); }
    public synchronized Match match(String fingerprint) {
        String trusted = preferences.getString("fingerprint", "");
        if (trusted.isEmpty()) return Match.UNVERIFIED;
        return trusted.equals(fingerprint) ? Match.VERIFIED : Match.CHANGED;
    }
    public synchronized void verify(String fingerprint) throws IOException {
        if (fingerprint == null || !fingerprint.matches("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}") || match(fingerprint) == Match.CHANGED)
            throw new IOException("IDENTITY_CHANGE_BLOCKED");
        if (!preferences.edit().putString("fingerprint",fingerprint).commit()) throw new IOException("TRUST_WRITE_FAILED");
    }
    public synchronized void clear() throws IOException {
        if (!preferences.edit().clear().commit()) throw new IOException("TRUST_DELETE_FAILED");
    }
}
