package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.tor.OnionAddress;
import org.junit.Test;
import static org.junit.Assert.*;
public class OnionAddressTest {
    @Test public void verifiesVersionChecksumAndCanonicalForm() {
        String valid="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion";
        assertEquals(valid, OnionAddress.require(valid));
        for (String invalid : new String[]{valid.toUpperCase(), valid.replace("m2dqd", "m2dqa"), "localhost", "127.0.0.1", "a".repeat(56)+".onion", valid+"/", "x".repeat(4096)}) {
            try { OnionAddress.require(invalid); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
}
