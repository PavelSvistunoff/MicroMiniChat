package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorFileSessionTest {
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
    @Test public void realTorStreamingTextAndCancellation()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String tag=Long.toHexString(System.nanoTime());
        Uri source=document("tor-source-"+tag),dest=document("tor-destination-"+tag),cancelDest=document("tor-cancel-"+tag);
        create(context,source,2*1024*1024);create(context,dest,0);create(context,cancelDest,0);
        ChatSession left=new ChatSession(context,"file-a-"+tag),right=new ChatSession(context,"file-b-"+tag);
        try{
            TorSessionTest.pair(left,right);left.offerFile(source);TorSessionTest.waitFor(()->right.incomingOffer!=null,30);
            right.acceptFile(right.incomingOffer.id,dest);left.sendText("text alongside Tor file");
            TorSessionTest.waitFor(()->left.history.stream().anyMatch(row->row.body.equals("text alongside Tor file") && row.state.equals("Delivered")),60);
            TorSessionTest.waitFor(()->left.fileState.startsWith("Complete") && right.fileState.startsWith("Complete"),240);
            assertArrayEquals(hash(context,source),hash(context,dest));
            left.offerFile(source);TorSessionTest.waitFor(()->right.incomingOffer!=null,30);
            right.acceptFile(right.incomingOffer.id,cancelDest);left.cancelTransfers();
            TorSessionTest.waitFor(()->right.fileState.startsWith("Cancelled"),30);
            assertThrows(FileNotFoundException.class,()->context.getContentResolver().openInputStream(cancelDest));
        }finally{
            left.shutdown();right.shutdown();
            for(Uri uri:new Uri[]{source,dest,cancelDest})try{DocumentsContract.deleteDocument(context.getContentResolver(),uri);}catch(Exception ignored){}
        }
    }
}
