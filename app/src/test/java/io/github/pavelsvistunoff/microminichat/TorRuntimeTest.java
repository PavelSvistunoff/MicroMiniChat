package io.github.pavelsvistunoff.microminichat;

import io.github.pavelsvistunoff.microminichat.tor.TorRuntime;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class TorRuntimeTest {
    @Test public void parsesOnlyBoundedRealBootstrapProgress() throws Exception {
        assertEquals(45, TorRuntime.bootstrapProgress("NOTICE BOOTSTRAP PROGRESS=45 TAG=loading_descriptors SUMMARY=\"Loading relay descriptors\""));
        assertEquals(100, TorRuntime.bootstrapProgress("NOTICE BOOTSTRAP PROGRESS=100 TAG=done"));
        for (String invalid : new String[]{"PROGRESS=101", "PROGRESS=-1", "xPROGRESS=5", "PROGRESS=10x", "", "x".repeat(4097)}) {
            try { TorRuntime.bootstrapProgress(invalid); fail(); } catch (IOException expected) { }
        }
    }
}
