package io.github.pavelsvistunoff.microminichat.security;

import android.content.Context;
import io.github.pavelsvistunoff.microminichat.net.WebRtcTransport;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.net.ssl.*;
import org.webrtc.PeerConnection;
import org.webrtc.RtcCertificatePem;

/** Standard TLS with an ephemeral certificate, pinned by the shared invitation. */
public final class PairingTls {
    public static final long CERTIFICATE_LIFETIME_MS=java.util.concurrent.TimeUnit.HOURS.toMillis(1);
    public final SSLContext context;
    public final String fingerprint;
    private PairingTls(SSLContext context,String fingerprint) { this.context=context;this.fingerprint=fingerprint; }
    public static PairingTls create(Context context) throws Exception {
        WebRtcTransport.initialize(context);
        RtcCertificatePem pem=RtcCertificatePem.generateCertificate(PeerConnection.KeyType.ECDSA,CERTIFICATE_LIFETIME_MS);
        if (pem==null) throw new GeneralSecurityException("PAIRING_CERTIFICATE_FAILED");
        X509Certificate cert=IdentityStore.certificate(pem);
        byte[] der=Base64.getMimeDecoder().decode(pem.privateKey.replace("-----BEGIN PRIVATE KEY-----","")
                .replace("-----END PRIVATE KEY-----",""));
        try {
            PrivateKey key=KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            KeyStore store=KeyStore.getInstance("PKCS12");store.load(null);
            char[] password=new char[0];
            store.setKeyEntry("pairing",key,password,new java.security.cert.Certificate[]{cert});
            KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(store,password);
            SSLContext tls=SSLContext.getInstance("TLS");tls.init(managers.getKeyManagers(),null,new SecureRandom());
            return new PairingTls(tls,hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));
        } finally { Arrays.fill(der,(byte)0); }
    }
    public static SSLContext pinned(String fingerprint) throws Exception {
        if (fingerprint==null || !fingerprint.matches("[0-9a-f]{64}")) throw new GeneralSecurityException("INVALID_PIN");
        byte[] pin=new byte[32];for(int i=0;i<32;i++)pin[i]=(byte)Integer.parseInt(fingerprint.substring(i*2,i*2+2),16);
        X509TrustManager trust=new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain,String authType) throws CertificateException { throw new CertificateException("CLIENT_AUTH_UNSUPPORTED"); }
            @Override public void checkServerTrusted(X509Certificate[] chain,String authType) throws CertificateException {
                try {
                    if (chain==null || chain.length!=1) throw new CertificateException("INVALID_PAIRING_CHAIN");
                    chain[0].checkValidity();
                    if (!MessageDigest.isEqual(pin,MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())))
                        throw new CertificateException("PAIRING_PIN_MISMATCH");
                } catch (GeneralSecurityException e) { throw new CertificateException("PAIRING_CERTIFICATE_REJECTED",e); }
            }
        };
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{trust},new SecureRandom());return tls;
    }
    public static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
}
