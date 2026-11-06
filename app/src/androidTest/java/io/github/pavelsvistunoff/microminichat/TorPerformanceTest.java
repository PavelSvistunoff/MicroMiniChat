package io.github.pavelsvistunoff.microminichat;

import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.regex.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic emulator measurements, not thresholds or physical battery claims. */
public final class TorPerformanceTest {
    private static String shell(Instrumentation instrumentation,String command)throws Exception {
        try(ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand(command);
                InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[4096];int count;while((count=in.read(block))!=-1)out.write(block,0,count);
            return out.toString("UTF-8");
        }
    }
    private static JSONObject memory(Instrumentation instrumentation)throws Exception {
        JSONObject result=new JSONObject();Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);
        result.put("app_pss_kib",info.getTotalPss());
        long total=0;int children=0;
        for(String pid:shell(instrumentation,"pidof libtor.so").trim().split("\\s+")) {
            if(!pid.matches("[0-9]+"))continue;
            String dump=shell(instrumentation,"dumpsys meminfo "+pid);
            Matcher matcher=Pattern.compile("TOTAL PSS:\\s*([0-9]+)").matcher(dump);
            if(!matcher.find())throw new IOException("Native PSS unavailable");
            total+=Long.parseLong(matcher.group(1));children++;
        }
        assertEquals("Two independent measured Tor children",2,children);
        result.put("tor_children_pss_kib",total);return result;
    }
    @Test public void measureRealTorPairingMemoryAndStreaming()throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        String tag=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"perf-a-"+tag),right=new ChatSession(context,"perf-b-"+tag);
        Uri source=DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY,"perf-source-"+tag);
        Uri dest=DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY,"perf-dest-"+tag);
        final int size=2*1024*1024;JSONObject metrics=new JSONObject();
        try {
            try(OutputStream out=context.getContentResolver().openOutputStream(source,"w")) {
                byte[] block=new byte[8192];new java.util.Random(45).nextBytes(block);
                for(int count=0;count<size;count+=block.length)out.write(block);
            }
            try(OutputStream out=context.getContentResolver().openOutputStream(dest,"w")) { }
            long start=SystemClock.elapsedRealtime();left.createChat();right.start();
            TorSessionTest.waitFor(()->left.bootstrap==100&&right.bootstrap==100,310);
            metrics.put("both_bootstrap_ms",SystemClock.elapsedRealtime()-start);
            TorSessionTest.waitFor(()->!left.invitation.isEmpty()&&!right.localOnion.isEmpty(),310);
            metrics.put("both_published_ms",SystemClock.elapsedRealtime()-start);
            long joining=SystemClock.elapsedRealtime();right.joinChat(left.invitation);
            TorSessionTest.waitFor(()->!left.remoteFingerprint.isEmpty()&&!right.remoteFingerprint.isEmpty(),360);
            left.verifyPeer(left.remoteFingerprint);right.verifyPeer(right.remoteFingerprint);
            TorSessionTest.waitFor(()->left.ready&&right.ready,60);
            metrics.put("join_to_verified_ms",SystemClock.elapsedRealtime()-joining);
            metrics.put("start_to_authenticated_ms",SystemClock.elapsedRealtime()-start);
            Thread.sleep(5000);metrics.put("connected_idle",memory(instrumentation));
            left.offerFile(source);TorSessionTest.waitFor(()->right.incomingOffer!=null,60);
            long transfer=SystemClock.elapsedRealtime();right.acceptFile(right.incomingOffer.id,dest);
            left.sendText("synthetic performance text");
            TorSessionTest.waitFor(()->left.fileProgress>0,60);metrics.put("active_transfer",memory(instrumentation));
            TorSessionTest.waitFor(()->left.fileState.startsWith("Complete")&&right.fileState.startsWith("Complete"),300);
            long duration=SystemClock.elapsedRealtime()-transfer;
            metrics.put("file_bytes",size);metrics.put("file_ms",duration);metrics.put("file_bytes_per_second",size*1000L/duration);
            TorSessionTest.waitFor(()->left.history.stream().anyMatch(row->row.state.equals("Delivered")),60);
            metrics.put("concurrent_text_delivered",true);
            metrics.put("environment","API35 arm64 emulator, two clients in one app UID, real Tor");
            android.util.Log.i("MMC_MEASUREMENT",metrics.toString());
        } finally {
            left.shutdown();right.shutdown();
            for(Uri uri:new Uri[]{source,dest})try { DocumentsContract.deleteDocument(context.getContentResolver(),uri); }catch(Exception ignored) { }
        }
    }
}
