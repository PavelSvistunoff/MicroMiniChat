package io.github.pavelsvistunoff.microminichat;

import io.github.pavelsvistunoff.microminichat.pairing.PhoneInvite;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneInviteTest {
    @Test public void roundtripIpv4Ipv6AndCapabilityWithoutDns() {
        for(String ip:new String[]{"203.0.113.1","2001:db8::1","127.0.0.1"}) {
            PhoneInvite invite=new PhoneInvite(ip,44333,"ab".repeat(32),new byte[32]);
            PhoneInvite decoded=PhoneInvite.decode(invite.encode());
            assertEquals(ip,decoded.host);assertEquals(44333,decoded.port);assertArrayEquals(invite.token(),decoded.token());
            assertTrue(invite.encode().length()<PhoneInvite.MAX_CHARS);
        }
    }
    @Test public void rejectsAmbiguousAddressesAndUnboundedInput() {
        for(String host:new String[]{"example.org","localhost","01.2.3.4","256.2.3.4","1.2.3", "fe80::1%wlan0","[::1]","1.2.3.4/"})
            assertFalse(host,PhoneInvite.validIp(host));
        String valid=new PhoneInvite("203.0.113.1",44333,"ab".repeat(32),new byte[32]).encode();
        for(String bad:new String[]{valid.replace("/v1","/v2"),valid.replace(":44333",":0"),valid.replace(":44333",":65536"),
                valid.replace("microminichat:","http:"),valid.replace("/pair/v1#","/pair/v1?x=1#"),valid+"x","x".repeat(513)})
            assertThrows(IllegalArgumentException.class,()->PhoneInvite.decode(bad));
    }
    @Test public void capabilityIsCopiedAndCannotBeChangedByCaller() {
        byte[] token=new byte[32];PhoneInvite invite=new PhoneInvite("127.0.0.1",44333,"ab".repeat(32),token);
        token[0]=1;assertEquals(0,invite.token()[0]);byte[] copy=invite.token();copy[0]=2;assertEquals(0,invite.token()[0]);
    }
}
