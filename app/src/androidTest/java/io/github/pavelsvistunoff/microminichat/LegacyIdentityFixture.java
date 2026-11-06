package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import javax.crypto.*;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
/** Fresh test-only legacy-format material, never a committed or production private key. */
final class LegacyIdentityFixture {
    static X509Certificate create(Context context,String name)throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));KeyPair pair=generator.generateKeyPair();
        X500Principal subject=new X500Principal("CN=Legacy migration test");long now=System.currentTimeMillis();
        X509Certificate certificate=new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(subject,BigInteger.ONE,new Date(now-86400000),new Date(now+86400000),subject,pair.getPublic()).build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
        String key="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
        String cert="-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(certificate.getEncoded())+"\n-----END CERTIFICATE-----\n";
        KeyGenerator wrapping=KeyGenerator.getInstance("AES","AndroidKeyStore");String alias="MicroMiniChat."+name;
        wrapping.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());SecretKey secret=wrapping.generateKey();
        ByteArrayOutputStream plain=new ByteArrayOutputStream();DataOutputStream fields=new DataOutputStream(plain);fields.writeUTF(key);fields.writeUTF(cert);fields.flush();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret);byte[] encoded=plain.toByteArray(),encrypted;
        try{encrypted=cipher.doFinal(encoded);}finally{Arrays.fill(encoded,(byte)0);}
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),name+".enc"));FileOutputStream stream=file.startWrite();
        try{DataOutputStream out=new DataOutputStream(stream);out.writeInt(0x4d494401);out.write(cipher.getIV());out.write(encrypted);out.flush();file.finishWrite(stream);}catch(Exception e){file.failWrite(stream);throw e;}
        if(!context.getSharedPreferences("identity_flags",0).edit().putBoolean(name,true).commit())throw new IOException();return certificate;
    }
}
