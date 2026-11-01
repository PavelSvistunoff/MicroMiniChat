package io.github.pavelsvistunoff.microminichat.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import io.github.pavelsvistunoff.microminichat.net.WebRtcTransport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.webrtc.PeerConnection;
import org.webrtc.RtcCertificatePem;

/** DTLS identity encrypted at rest by a nonexportable Android Keystore AES key. */
public final class IdentityStore {
    private final Context context;
    private final String name;
    private final String alias;
    private final AtomicFile file;
    private final SharedPreferences flags;
    public IdentityStore(Context context) { this(context, "identity-v1"); }
    public IdentityStore(Context context, String name) {
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException();
        this.context = context.getApplicationContext(); this.name = name;
        alias = "MicroMiniChat." + name;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".enc"));
        flags = context.getSharedPreferences("identity_flags", Context.MODE_PRIVATE);
    }
    public synchronized RtcCertificatePem load() throws Exception {
        WebRtcTransport.initialize(context);
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (file.getBaseFile().exists()) {
            if (!store.containsAlias(alias) || file.getBaseFile().length() > 16384) throw new GeneralSecurityException("IDENTITY_UNAVAILABLE");
            byte[] encrypted = file.readFully();
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encrypted));
            if (input.readInt() != 0x4d494401) throw new GeneralSecurityException("IDENTITY_FORMAT");
            byte[] nonce = new byte[12]; input.readFully(nonce);
            byte[] cipherText = new byte[input.available()]; input.readFully(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, store.getKey(alias,null), new GCMParameterSpec(128,nonce));
            byte[] plain = cipher.doFinal(cipherText);
            try {
                DataInputStream data = new DataInputStream(new ByteArrayInputStream(plain));
                RtcCertificatePem identity = new RtcCertificatePem(data.readUTF(),data.readUTF());
                if (data.available() != 0) throw new GeneralSecurityException("IDENTITY_FORMAT");
                certificate(identity).checkValidity(); return identity;
            } finally { Arrays.fill(plain,(byte)0); }
        }
        if (flags.getBoolean(name,false)) throw new GeneralSecurityException("IDENTITY_MISSING_EXPLICIT_RESET_REQUIRED");
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey();
        }
        RtcCertificatePem identity = RtcCertificatePem.generateCertificate(PeerConnection.KeyType.ECDSA,java.util.concurrent.TimeUnit.DAYS.toMillis(365));
        if (identity == null) throw new GeneralSecurityException("IDENTITY_GENERATION_FAILED");
        certificate(identity).checkValidity();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(encoded); output.writeUTF(identity.privateKey); output.writeUTF(identity.certificate);
        byte[] plain = encoded.toByteArray();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,store.getKey(alias,null));
        byte[] cipherText;
        try { cipherText = cipher.doFinal(plain); } finally { Arrays.fill(plain,(byte)0); }
        FileOutputStream stream = null;
        try {
            stream = file.startWrite(); DataOutputStream data = new DataOutputStream(stream);
            data.writeInt(0x4d494401); data.write(cipher.getIV()); data.write(cipherText); data.flush();
            file.finishWrite(stream);
        } catch (IOException e) { file.failWrite(stream); throw e; }
        if (!flags.edit().putBoolean(name,true).commit()) throw new IOException("IDENTITY_FLAG_FAILED");
        return identity;
    }
    public static X509Certificate certificate(RtcCertificatePem identity) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(identity.certificate.getBytes(StandardCharsets.US_ASCII)));
    }
    public static String fingerprint(RtcCertificatePem identity) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(certificate(identity).getEncoded());
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) { if (result.length() > 0) result.append(':'); result.append(String.format(java.util.Locale.ROOT,"%02X",b & 255)); }
        return result.toString();
    }
    public synchronized void reset() throws Exception {
        file.delete(); KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(alias);
        if (!flags.edit().remove(name).commit()) throw new IOException("IDENTITY_RESET_FAILED");
    }
    public File encryptedFile() { return file.getBaseFile(); }
}
