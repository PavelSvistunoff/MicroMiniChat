package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.net.ReconnectBackoff;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReconnectPolicyTest {
    @Test public void exponentialJitterCapsAndResets() {
        ReconnectBackoff delays=new ReconnectBackoff(()->0.5);
        assertEquals(1000,delays.nextDelay());assertEquals(2000,delays.nextDelay());assertEquals(4000,delays.nextDelay());
        for(int i=0;i<1000;i++){long delay=delays.nextDelay();assertTrue(delay>=1000 && delay<=300000);}
        assertEquals(300000,delays.nextDelay());delays.reset();assertEquals(1000,delays.nextDelay());
        assertNotEquals(new ReconnectBackoff(()->0).nextDelay(),new ReconnectBackoff(()->1).nextDelay());
    }
    @Test public void simultaneousAttemptsChooseTheSamePhysicalStream() {
        assertTrue(ReconnectBackoff.preferred("a","b",true));assertTrue(ReconnectBackoff.preferred("b","a",false));
        assertFalse(ReconnectBackoff.preferred("a","b",false));assertFalse(ReconnectBackoff.preferred("b","a",true));
    }
}
