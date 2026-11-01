package io.github.pavelsvistunoff.microminichat;

import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.security.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IdentityTest {
    @Test public void identityPersistsEncryptedAndTamperingFailsClosed() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        IdentityStore store = new IdentityStore(context,"test-identity"); store.reset();
        try {
            String first = IdentityStore.fingerprint(store.load());
            assertEquals(first,IdentityStore.fingerprint(new IdentityStore(context,"test-identity").load()));
            byte[] ciphertext = Files.readAllBytes(store.encryptedFile().toPath());
            assertFalse(new String(ciphertext,StandardCharsets.ISO_8859_1).contains("PRIVATE KEY"));
            ciphertext[ciphertext.length-1] ^= 1; Files.write(store.encryptedFile().toPath(),ciphertext);
            assertThrows(Exception.class, store::load);
            Files.delete(store.encryptedFile().toPath());
            assertThrows(Exception.class, store::load);
        } finally { store.reset(); }
    }
    @Test public void trustRequiresExplicitVerificationAndNeverReplacesChangedKey() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TrustStore trust = new TrustStore(context,"test-trust"); trust.clear();
        String first = String.join("",java.util.Collections.nCopies(31,"AB:"))+"AB", changed = String.join("",java.util.Collections.nCopies(31,"CD:"))+"CD";
        assertEquals(TrustStore.Match.UNVERIFIED,trust.match(first));
        trust.verify(first);
        assertEquals(TrustStore.Match.VERIFIED,new TrustStore(context,"test-trust").match(first));
        assertEquals(TrustStore.Match.CHANGED,trust.match(changed));
        assertThrows(java.io.IOException.class,()->trust.verify(changed));
        trust.clear();
    }
}
