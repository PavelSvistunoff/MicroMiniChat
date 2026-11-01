package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.lifecycle.ForegroundPolicy;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ForegroundPolicyTest {
    @Test public void shortAppSwitchPreservesSessionButLongSuspensionExpires(){
        ForegroundPolicy policy=new ForegroundPolicy(120000);policy.background(1000);
        assertFalse(policy.foreground(2000));assertFalse(policy.expired(999999));
        policy.background(3000);policy.background(4000);assertTrue(policy.foreground(123000));
    }
    @Test public void delayedTimersCannotExtendPermissionToStayConnected(){
        ForegroundPolicy policy=new ForegroundPolicy(100);policy.background(0);
        assertFalse(policy.expired(99));assertTrue(policy.expired(100));assertTrue(policy.foreground(10000));
        assertThrows(IllegalArgumentException.class,()->new ForegroundPolicy(0));
    }
}
