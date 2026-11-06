package io.github.pavelsvistunoff.microminichat;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalizationTest {
    @Test public void ukrainianPluralsFormatsAndNativeControls() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocale(java.util.Locale.forLanguageTag("uk"));config.fontScale=1.5f;
        Context localized=context.createConfigurationContext(config);
        assertEquals("Створити чат",localized.getString(R.string.create_chat));
        assertTrue(localized.getResources().getQuantityString(R.plurals.message_count,5,5).contains("повідомлень"));
        assertTrue(localized.getString(R.string.state_bootstrap,45).contains("45%"));
        if(Build.VERSION.SDK_INT<33)return;
        LocaleManager manager=context.getSystemService(LocaleManager.class);LocaleList previous=manager.getApplicationLocales();
        Activity activity=null;
        try{
            instrumentation.runOnMainSync(()->manager.setApplicationLocales(LocaleList.forLanguageTags("uk")));
            instrumentation.waitForIdleSync();Thread.sleep(300);
            activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity current=activity;
            instrumentation.runOnMainSync(()->{
                assertEquals("Створити чат",((Button)current.findViewById(R.id.pair_start)).getText().toString());
                assertEquals("Надіслати",((Button)current.findViewById(R.id.send)).getText().toString());
                assertFalse(current.findViewById(R.id.send).isEnabled());
            });
        }finally{
            Activity remaining=activity;if(remaining!=null)instrumentation.runOnMainSync(remaining::finish);
            instrumentation.runOnMainSync(()->manager.setApplicationLocales(previous));
            ((ChatApp)context.getApplicationContext()).torSession().disconnect();
        }
    }
}
