package io.github.pavelsvistunoff.microminichat;

import io.github.pavelsvistunoff.microminichat.net.IceConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IceConfigTest {
    @Test public void directModeHasNoImplicitServer() {
        IceConfig config=IceConfig.direct("");
        assertFalse(config.allowsRelay());
        assertFalse(config.relayOnly);
        assertEquals("",config.stun);
    }
    @Test public void supportsUdpTcpTlsAndIpv6WithExplicitCredentials() {
        for (String url : new String[]{"turn:relay.example:3478?transport=udp", "turn:relay.example:3478?transport=tcp",
                "turns:relay.example:5349?transport=tcp", "turn:[2001:db8::1]:3478", "turns:relay.example"}) {
            IceConfig config=new IceConfig("",url,"user","secret",true);
            assertTrue(url,config.allowsRelay());
            assertTrue(config.relayOnly);
            assertEquals("secret",config.password);
        }
    }
    @Test public void rejectsAmbiguousMalformedAndUnsupportedEndpoints() {
        for (String url : new String[]{"stun:relay.example", "https://relay.example", "turn:user:pass@relay.example",
                "turn:relay.example:0", "turn:relay.example:65536", "turn:relay.example\n",
                "turns:relay.example?transport=udp", "turn:relay.example?transport=quic", "turn:relay.example/path",
                "turn:relay.example?transport=tcp&x=y", "turn:", "turn:one turn:two"}) {
            assertFalse(url,IceConfig.validTurn(url));
        }
    }
    @Test public void credentialsAndRelayOnlyCannotBeSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","","","",true));
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","","user","secret",false));
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","turn:relay.example","","secret",false));
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","turn:relay.example","user","",false));
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","turn:relay.example","user","bad\nsecret",false));
        assertThrows(IllegalArgumentException.class,()->new IceConfig("","turn:relay.example","user","x".repeat(513),false));
    }
}
