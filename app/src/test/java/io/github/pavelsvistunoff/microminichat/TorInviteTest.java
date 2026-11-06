package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.pairing.TorInvite;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorInviteTest {
    private static final String ONION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion";
    private static final String PIN="AB:".repeat(31)+"AB";
    @Test public void roundtripAndCapabilityAreImmutable() {
        TorInvite invite=TorInvite.create(ONION,PIN,1000);
        assertEquals(invite.encode(),TorInvite.decode(invite.encode(),1000).encode());
        byte[] capability=invite.capability(); capability[0]^=1;
        assertFalse(java.util.Arrays.equals(capability,invite.capability()));
    }
    @Test public void rejectsExpiryVersionAndMalformedInput() {
        TorInvite invite=TorInvite.create(ONION,PIN,1000);
        for(String invalid:new String[]{"", invite.encode()+"=", invite.encode().replace("/v2#","/v1#"), "x".repeat(513),invite.encode()+"A"}) {
            try { TorInvite.decode(invalid,1000);fail(); } catch(IllegalArgumentException expected) { }
        }
        try { TorInvite.decode(invite.encode(),601000);fail(); } catch(IllegalArgumentException expected) { }
    }
}
