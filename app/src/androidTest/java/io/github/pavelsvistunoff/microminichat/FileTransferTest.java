package io.github.pavelsvistunoff.microminichat;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FileTransferTest {
    private static Uri document(String name){return DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY,name);}
    private static void create(Context context,Uri uri,int size)throws Exception{
        try(OutputStream out=context.getContentResolver().openOutputStream(uri,"w")){
            byte[] chunk=new byte[8192];new java.util.Random(42).nextBytes(chunk);
            for(int written=0;written<size;){int count=Math.min(chunk.length,size-written);out.write(chunk,0,count);written+=count;}
        }
    }
    private static byte[] hash(Context context,Uri uri)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=context.getContentResolver().openInputStream(uri)){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1)digest.update(chunk,0,n);}
        return digest.digest();
    }
    @Test public void safStreamingIntegrityAndConcurrentText()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        Uri source=document("source-"+tag),destination=document("destination-"+tag);create(context,source,2*1024*1024);create(context,destination,0);
        ChatSession left=new ChatSession(context,"file-left"+tag),right=new ChatSession(context,"file-right"+tag);
        try{
            SessionTest.connect(left,right,true);left.offerFile(source);SessionTest.waitFor(()->right.incomingOffer!=null);
            right.acceptFile(right.incomingOffer.id,destination);left.sendText("text during file transfer");
            SessionTest.waitFor(()->left.history.toString().contains("[Delivered]: text during file transfer"));
            SessionTest.waitFor(()->left.fileStatus.contains("Complete") && right.fileStatus.contains("Complete"));
            assertArrayEquals(hash(context,source),hash(context,destination));
            left.clearEverything();right.clearEverything();
            SessionTest.waitFor(()->left.history.isEmpty() && right.history.isEmpty());
        }finally{left.shutdown();right.shutdown();try{DocumentsContract.deleteDocument(context.getContentResolver(),source);}catch(Exception ignored){}try{DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}}
    }
    @Test public void hashMismatchNeverCompletesAndDeletesDestination()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        Uri source=document("hash-source-"+tag),destination=document("hash-dest-"+tag);create(context,source,0);create(context,destination,0);
        io.github.pavelsvistunoff.microminichat.files.FileTransfers[] engines=new io.github.pavelsvistunoff.microminichat.files.FileTransfers[2];
        java.util.UUID session=java.util.UUID.randomUUID();
        java.util.concurrent.CountDownLatch rejected=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean completed=new java.util.concurrent.atomic.AtomicBoolean();
        for(int i=0;i<2;i++){
            final int side=i;
            engines[i]=new io.github.pavelsvistunoff.microminichat.files.FileTransfers(context,session,(file,frame,result)->{
                if(side==0 && frame.type==io.github.pavelsvistunoff.microminichat.protocol.Frame.FILE_END){
                    byte[] altered=frame.payload.clone();altered[0]^=1;
                    frame=new io.github.pavelsvistunoff.microminichat.protocol.Frame(frame.type,frame.session,frame.id,frame.sequence,altered);
                }
                engines[1-side].receive(file,frame);result.accept(true);
            },new io.github.pavelsvistunoff.microminichat.files.FileTransfers.Listener(){
                @Override public void onOffer(io.github.pavelsvistunoff.microminichat.files.FileOffer offer){if(side==1 && offer!=null)engines[1].accept(offer.id,destination);}
                @Override public void onUpdate(io.github.pavelsvistunoff.microminichat.files.FileOffer offer,boolean outgoing,long progress,String state,String uri,String hash){
                    if(state.startsWith("Complete"))completed.set(true);
                    if(side==1 && state.startsWith("Failed"))rejected.countDown();
                }
                @Override public void onError(String state){}
            });
        }
        try{
            engines[0].offer(source);assertTrue(rejected.await(15,java.util.concurrent.TimeUnit.SECONDS));assertFalse(completed.get());
            assertThrows(FileNotFoundException.class,()->context.getContentResolver().openInputStream(destination));
        }finally{for(io.github.pavelsvistunoff.microminichat.files.FileTransfers engine:engines)if(engine!=null)engine.close();try{DocumentsContract.deleteDocument(context.getContentResolver(),source);}catch(Exception ignored){}}
    }

    @Test public void cancellationDeletesPartialDestination()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        Uri source=document("cancel-source-"+tag),destination=document("cancel-dest-"+tag);create(context,source,8*1024*1024);create(context,destination,0);
        ChatSession left=new ChatSession(context,"cancel-left"+tag),right=new ChatSession(context,"cancel-right"+tag);
        try{
            SessionTest.connect(left,right,true);left.offerFile(source);SessionTest.waitFor(()->right.incomingOffer!=null);
            right.acceptFile(right.incomingOffer.id,destination);left.cancelTransfers();
            SessionTest.waitFor(()->right.fileStatus.contains("Cancelled"));
            assertThrows(FileNotFoundException.class,()->context.getContentResolver().openInputStream(destination));
            left.clearEverything();right.clearEverything();
        }finally{left.shutdown();right.shutdown();try{DocumentsContract.deleteDocument(context.getContentResolver(),source);}catch(Exception ignored){}try{DocumentsContract.deleteDocument(context.getContentResolver(),destination);}catch(Exception ignored){}}
    }
}
