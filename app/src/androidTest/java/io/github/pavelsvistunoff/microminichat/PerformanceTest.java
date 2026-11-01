package io.github.pavelsvistunoff.microminichat;

import androidx.test.platform.app.InstrumentationRegistry;
import android.os.*;
import android.net.TrafficStats;
import java.io.*;
import org.junit.Test;

/** Measurements, not performance-budget assertions. Two peers share one emulator process. */
public final class PerformanceTest {
    @Test public void recordConnectedIdleResources() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"perfleft"+suffix),right=new ChatSession(context,"perfright"+suffix);
        try {
            long start=SystemClock.elapsedRealtime();SessionTest.connect(left,right,true);
            long pairing=SystemClock.elapsedRealtime()-start;
            long cpu=android.os.Process.getElapsedCpuTime(),wall=SystemClock.elapsedRealtime();
            long rx=TrafficStats.getUidRxBytes(android.os.Process.myUid()),tx=TrafficStats.getUidTxBytes(android.os.Process.myUid());
            Thread.sleep(10000);
            long interval=SystemClock.elapsedRealtime()-wall,cpuMillis=android.os.Process.getElapsedCpuTime()-cpu;
            String json="{\"environment\":\"API 35 arm64 emulator; debug instrumentation; two peers in one process\","
                    +"\"physical_hardware\":false,\"in_process_pairing_ms\":"+pairing+",\"idle_sample_ms\":"+interval
                    +",\"process_cpu_ms\":"+cpuMillis+",\"process_pss_kib\":"+Debug.getPss()
                    +",\"uid_rx_bytes_delta\":"+(TrafficStats.getUidRxBytes(android.os.Process.myUid())-rx)
                    +",\"uid_tx_bytes_delta\":"+(TrafficStats.getUidTxBytes(android.os.Process.myUid())-tx)+"}";
            try(FileOutputStream out=context.openFileOutput("qa-metrics.json",0)){out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            left.clearEverything();right.clearEverything();
        } finally { left.shutdown();right.shutdown(); }
    }
}
