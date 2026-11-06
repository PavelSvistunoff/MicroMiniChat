package io.github.pavelsvistunoff.microminichat;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TerminalLayoutTest {
    private static String shell(Instrumentation instrumentation,String command)throws Exception{
        try(ParcelFileDescriptor descriptor=instrumentation.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] block=new byte[1024];int count;while((count=in.read(block))!=-1)out.write(block,0,count);return out.toString("UTF-8").trim();
        }
    }
    @Test public void terminalLayoutSupportsLargeFontAndExportsSyntheticPreview()throws Exception{
        org.junit.Assume.assumeTrue(Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk")||Build.FINGERPRINT.contains("generic"));
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        String previous=shell(instrumentation,"settings get system font_scale");
        if(!previous.matches("null|[0-9.]+"))throw new IOException("Invalid test font setting");
        Activity activity=null;
        try{
            shell(instrumentation,"settings put system font_scale 1.5");Thread.sleep(500);instrumentation.waitForIdleSync();
            activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();final Activity current=activity;
            instrumentation.runOnMainSync(()->{
                assertTrue(current.getResources().getConfiguration().fontScale>=1.4f);
                assertTrue(current.findViewById(R.id.messages).getHeight()>0);
                Button send=current.findViewById(R.id.send);assertTrue(send.isShown());assertTrue(send.getHeight()>=48*current.getResources().getDisplayMetrics().density);
                assertTrue(send.getTop()>=0);assertTrue(send.getBottom()<=((android.view.View)send.getParent()).getHeight());
                android.view.View root=current.findViewById(android.R.id.content);Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
                try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"terminal-preview.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
                catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
            });
        }finally{
            Activity remaining=activity;if(remaining!=null)instrumentation.runOnMainSync(remaining::finish);
            shell(instrumentation,previous.equals("null")?"settings delete system font_scale":"settings put system font_scale "+previous);
            ((ChatApp)context.getApplicationContext()).torSession().disconnect();
        }
    }
}
