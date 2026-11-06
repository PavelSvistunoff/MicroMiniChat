package io.github.pavelsvistunoff.microminichat.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.conscrypt.Conscrypt;

/** Standard TLS 1.3 identity. Existing wrapped PEM is read without changing trust or history. */
public final class TlsIdentity {
    private final Context context;
    private final String name, alias;
    private final SharedPreferences flags;
    private PrivateKey key;
    private X509Certificate certificate;
    public TlsIdentity(Context context, String name) {
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException();
        this.context = context.getApplicationContext(); this.name = name;
        alias = "MicroMiniChat." + name + ".tls-v2";
        flags = context.getSharedPreferences("identity_flags", 0);
    }
    public synchronized void load() throws Exception {
        if (key != null) { certificate.checkValidity(); return; }
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        AtomicFile old = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".enc"));
        if (old.getBaseFile().exists()) {
            if (old.getBaseFile().length() > 16384 || !store.containsAlias("MicroMiniChat." + name))
                throw new GeneralSecurityException("LEGACY_IDENTITY_UNAVAILABLE");
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(old.readFully()))) {
                if (input.readInt() != 0x4d494401) throw new GeneralSecurityException("IDENTITY_FORMAT");
                byte[] nonce = new byte[12]; input.readFully(nonce);
                byte[] encrypted = new byte[input.available()]; input.readFully(encrypted);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, store.getKey("MicroMiniChat." + name, null), new GCMParameterSpec(128, nonce));
                byte[] plain = cipher.doFinal(encrypted);
                try (DataInputStream fields = new DataInputStream(new ByteArrayInputStream(plain))) {
                    String pemKey = fields.readUTF(), pemCertificate = fields.readUTF();
                    if (fields.available() != 0) throw new GeneralSecurityException("IDENTITY_FORMAT");
                    certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                            new ByteArrayInputStream(pemCertificate.getBytes(StandardCharsets.US_ASCII)));
                    byte[] encoded = Base64.getMimeDecoder().decode(pemKey.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
                    try { key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded)); }
                    finally { Arrays.fill(encoded, (byte) 0); }
                } finally { Arrays.fill(plain, (byte) 0); }
            }
        } else {
            if (flags.getBoolean(name, false)) throw new GeneralSecurityException("LEGACY_IDENTITY_MISSING");
            if (!store.containsAlias(alias)) {
                if (flags.getBoolean(alias, false)) throw new GeneralSecurityException("IDENTITY_MISSING");
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
                long now = System.currentTimeMillis();
                generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                        .setCertificateSubject(new X500Principal("CN=MicroMiniChat"))
                        .setCertificateSerialNumber(new BigInteger(128, new SecureRandom()).add(BigInteger.ONE))
                        .setCertificateNotBefore(new Date(now - 86400000L))
                        .setCertificateNotAfter(new Date(now + 20L * 365 * 86400000L)).build());
                generator.generateKeyPair();
            }
            certificate = (X509Certificate) store.getCertificate(alias);
            key = (PrivateKey) store.getKey(alias, null);
            if (!flags.edit().putBoolean(alias, true).commit()) throw new IOException("IDENTITY_COMMIT");
        }
        certificate.checkValidity();
    }
    public static String fingerprint(X509Certificate certificate) throws GeneralSecurityException {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())) {
            if (result.length() > 0) result.append(':');
            result.append(String.format(Locale.ROOT, "%02X", value & 255));
        }
        return result.toString();
    }
    public synchronized String fingerprint() throws Exception { load(); return fingerprint(certificate); }
    public synchronized SSLContext tls(String expected) throws Exception {
        load();
        if (expected == null || (!expected.isEmpty() && !expected.matches("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")))
            throw new GeneralSecurityException("INVALID_PIN");
        X509ExtendedKeyManager manager = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String type, Principal[] issuers) { return type.startsWith("EC") ? new String[]{"identity"} : null; }
            @Override public String[] getServerAliases(String type, Principal[] issuers) { return getClientAliases(type, issuers); }
            @Override public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                for (String type : types) if (type.startsWith("EC")) return "identity";
                return null;
            }
            @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return type.startsWith("EC") ? "identity" : null; }
            @Override public X509Certificate[] getCertificateChain(String alias) { return new X509Certificate[]{certificate}; }
            @Override public PrivateKey getPrivateKey(String alias) { return key; }
        };
        X509TrustManager trust = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            private void check(X509Certificate[] chain) throws CertificateException {
                try {
                    if (chain == null || chain.length != 1 || !(chain[0].getPublicKey() instanceof java.security.interfaces.ECPublicKey)
                            || ((java.security.interfaces.ECPublicKey) chain[0].getPublicKey()).getParams().getOrder().bitLength() != 256)
                        throw new CertificateException("PEER_CERTIFICATE");
                    chain[0].checkValidity();
                    chain[0].verify(chain[0].getPublicKey());
                    if (!expected.isEmpty() && !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                            fingerprint(chain[0]).getBytes(StandardCharsets.US_ASCII))) throw new CertificateException("PEER_IDENTITY_CHANGED");
                    // An unpinned self-signed client is only a candidate; capability and human verification still gate all product data.
                } catch (GeneralSecurityException e) { throw new CertificateException("PEER_CERTIFICATE_REJECTED", e); }
            }
            @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException { check(chain); }
            @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException { check(chain); }
        };
        Conscrypt.setUseEngineSocketByDefault(true);
        SSLContext tls = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        tls.init(new KeyManager[]{manager}, new TrustManager[]{trust}, new SecureRandom());
        return tls;
    }
    public static void configure(SSLSocket socket, boolean client) {
        socket.setUseClientMode(client);
        socket.setEnabledProtocols(new String[]{"TLSv1.3"});
        if (!client) socket.setNeedClientAuth(true);
        Conscrypt.setUseSessionTickets(socket, false);
        Conscrypt.setApplicationProtocols(socket, new String[]{"mmc-tor/2"});
    }
    public static String authenticate(SSLSocket socket) throws Exception {
        socket.startHandshake();
        if (!"TLSv1.3".equals(socket.getSession().getProtocol()) || !"mmc-tor/2".equals(Conscrypt.getApplicationProtocol(socket)))
            throw new SSLHandshakeException("PROTOCOL_DOWNGRADE");
        return fingerprint((X509Certificate) socket.getSession().getPeerCertificates()[0]);
    }
    /** Explicit UI-confirmed identity rotation. Does not delete history or peer trust. */
    public synchronized void resetAfterConfirmation() throws Exception {
        AtomicFile old = new AtomicFile(new File(context.getNoBackupFilesDir(), name + ".enc")); old.delete();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        store.deleteEntry(alias); store.deleteEntry("MicroMiniChat." + name);
        if (!flags.edit().remove(alias).remove(name).commit()) throw new IOException("IDENTITY_RESET");
        key = null; certificate = null;
    }
}
