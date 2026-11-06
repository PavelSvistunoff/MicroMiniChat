package io.github.pavelsvistunoff.microminichat.tor;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Tor manages onion keys in no-backup private storage; they are not hardware-backed. */
public final class OnionIdentity {
    private final File directory;
    private final SharedPreferences flags;
    public OnionIdentity(Context context) {
        directory = new File(context.getNoBackupFilesDir(), "onion-v3");
        flags = context.getSharedPreferences("onion_identity_v1", Context.MODE_PRIVATE);
    }
    public File prepare() throws IOException {
        if (flags.getBoolean("created", false) && !new File(directory, "hs_ed25519_secret_key").isFile())
            throw new IOException("ONION_IDENTITY_MISSING_RESET_REQUIRED");
        if (!directory.isDirectory() && !directory.mkdir()) throw new IOException("ONION_STORAGE");
        if (!directory.setReadable(false, false) || !directory.setWritable(false, false)
                || !directory.setExecutable(false, false) || !directory.setReadable(true, true)
                || !directory.setWritable(true, true) || !directory.setExecutable(true, true))
            throw new IOException("ONION_PERMISSIONS");
        return directory;
    }
    /** Caller must obtain explicit user confirmation before calling this operation. */
    public void resetAfterConfirmation() throws IOException {
        if (TorRuntime.isNativeRunning()) throw new IOException("STOP_TOR_BEFORE_RESET");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File entry : files) {
                if (!entry.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())
                        || entry.isDirectory() || !entry.delete()) throw new IOException("ONION_RESET_FAILED");
            }
        }
        if (!flags.edit().remove("created").commit()) throw new IOException("ONION_RESET_COMMIT");
    }
    public String address() throws IOException {
        File hostname = new File(directory, "hostname");
        if (!hostname.isFile()) return "";
        if (hostname.length() > 64 || !new File(directory, "hs_ed25519_secret_key").isFile())
            throw new IOException("ONION_IDENTITY_CORRUPT");
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(hostname)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1) {
                if (out.size() >= 64) throw new IOException("ONION_HOSTNAME_SIZE");
                out.write(value);
            }
            bytes = out.toByteArray();
        }
        String address = OnionAddress.require(new String(bytes, StandardCharsets.US_ASCII).trim());
        if (!flags.edit().putBoolean("created", true).commit()) throw new IOException("ONION_IDENTITY_COMMIT");
        return address;
    }
}
