package io.github.pavelsvistunoff.microminichat;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import io.github.pavelsvistunoff.microminichat.files.FileOffer;
import io.github.pavelsvistunoff.microminichat.pairing.TorInvite;
import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import io.github.pavelsvistunoff.microminichat.storage.MessageDatabase;
import java.util.*;

/** Native terminal-style messenger. Message content is always literal selectable text. */
public final class MainActivity extends Activity {
    private ChatSession session;
    private EditText composer;
    private TextView status, secure, notice, fileStatus;
    private Button send, attach, share, verify, accept, cancelFile;
    private LinearLayout pairingActions;
    private LogAdapter adapter;
    private UUID saving;
    AlertDialog pairing;
    private String pendingImport = "";
    @Override protected void attachBaseContext(Context base) {
        String language = base.getSharedPreferences("ui",0).getString("language","");
        if (Build.VERSION.SDK_INT < 33 && !language.isEmpty()) {
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag(language)); base = base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        session = ((ChatApp)getApplication()).session();
        if (saved != null) { String id=saved.getString("saving"); if(id!=null)saving=UUID.fromString(id); pendingImport=saved.getString("import",""); }
        LinearLayout root = column();root.setBackgroundColor(color(R.color.terminal_background));
        int padding=dimension(R.dimen.terminal_padding);root.setPadding(padding,padding,padding,padding);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets value=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());left=value.left;top=value.top;right=value.right;bottom=value.bottom;}
            else {left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}
            view.setPadding(padding+left,padding+top,padding+right,padding+bottom);return insets;
        });
        LinearLayout header = row();root.addView(header);
        TextView title = text(R.string.app_name,R.dimen.terminal_title_size);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button menu = button(R.string.overflow_symbol,()->showMenu(header));menu.setContentDescription(getString(R.string.more_options));header.addView(menu,new LinearLayout.LayoutParams(dimension(R.dimen.terminal_touch),-2));
        status = text(R.string.state_offline,R.dimen.terminal_status_size);status.setId(R.id.status);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        secure = text(R.string.secure_unpaired,R.dimen.terminal_status_size);root.addView(secure);
        pairingActions = row();root.addView(pairingActions);
        Button create=button(R.string.create_chat,session::createChat);create.setId(R.id.pair_start);pairingActions.addView(create,weight());
        Button join=button(R.string.join_chat,()->joinDialog(""));join.setId(R.id.pair_join);pairingActions.addView(join,weight());
        LinearLayout invitationActions=row();root.addView(invitationActions);
        share=button(R.string.share_invitation,this::shareInvitation);share.setId(R.id.pair_share);invitationActions.addView(share,weight());
        verify=button(R.string.verification,this::verifyDialog);verify.setId(R.id.pair_verify);invitationActions.addView(verify,weight());
        ListView history = new ListView(this);history.setId(R.id.messages);history.setDivider(new android.graphics.drawable.ColorDrawable(color(R.color.terminal_surface)));history.setDividerHeight(dimension(R.dimen.terminal_separator));history.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        adapter = new LogAdapter();history.setAdapter(adapter);root.addView(history,new LinearLayout.LayoutParams(-1,0,1));
        fileStatus=text(0,R.dimen.terminal_status_size);root.addView(fileStatus);
        LinearLayout files=row();root.addView(files);
        accept=button(R.string.save_file,this::acceptFile);files.addView(accept,weight());cancelFile=button(R.string.cancel_transfer,session::cancelTransfers);files.addView(cancelFile,weight());
        LinearLayout input=row();input.setBackgroundColor(color(R.color.terminal_surface));input.setGravity(Gravity.CENTER_VERTICAL);root.addView(input);
        TextView prompt=text(R.string.prompt,R.dimen.terminal_message_size);input.addView(prompt);
        composer=new EditText(this);composer.setId(R.id.chat_input);composer.setHint(R.string.message_hint);composer.setTextColor(color(R.color.terminal_primary));composer.setHintTextColor(color(R.color.terminal_secondary));composer.setTypeface(Typeface.MONOSPACE);composer.setTextSize(TypedValue.COMPLEX_UNIT_PX,getResources().getDimension(R.dimen.terminal_message_size));
        composer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);composer.setMaxLines(4);composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Frame.MAX_TEXT)});composer.setMinHeight(dimension(R.dimen.terminal_touch));composer.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        composer.setText(session.draft);input.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        composer.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){session.draft=s.toString();}public void afterTextChanged(Editable value){}});
        LinearLayout actions=row();root.addView(actions);
        attach=button(R.string.attach,this::pickSource);attach.setId(R.id.attach);actions.addView(attach,weight());
        send=button(R.string.send,()->{
            String value=composer.getText().toString();try{Frame.textBytes(value);}catch(IllegalArgumentException e){toast(R.string.invalid_message);return;}
            session.sendText(value);composer.setText("");
        });send.setId(R.id.send);actions.addView(send,weight());
        notice=text(R.string.foreground_notice,R.dimen.terminal_status_size);root.addView(notice);
        setContentView(root);session.watch(this::render);root.requestApplyInsets();
        if(!pendingImport.isEmpty())joinDialog(pendingImport);else inspectIntent(getIntent());
    }
    private int dimension(int id){return getResources().getDimensionPixelSize(id);}
    private int color(int id){return getColor(id);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);return view;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private TextView text(int resource,int size){TextView view=new TextView(this);if(resource!=0)view.setText(resource);view.setTypeface(Typeface.MONOSPACE);view.setTextColor(color(R.color.terminal_primary));view.setTextSize(TypedValue.COMPLEX_UNIT_PX,getResources().getDimension(size));return view;}
    private Button button(int label,Runnable action){Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setMinHeight(dimension(R.dimen.terminal_touch));button.setTextColor(color(R.color.terminal_green));button.setOnClickListener(v->action.run());return button;}
    private void toast(int message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private String connectionStatus(){
        switch(session.state){
            case STARTING_TOR:return getString(R.string.state_starting);
            case CONNECTING_TOR:return getString(R.string.state_bootstrap,session.bootstrap);
            case ONION_READY:return getString(R.string.state_onion_ready);
            case CONNECTING_PEER:return getString(R.string.state_connecting);
            case AUTHENTICATING:return getString(R.string.state_authenticating);
            case CONNECTED:return getString(R.string.state_connected);
            case RECONNECTING:return getString(R.string.state_reconnecting);
            case FAILED:return getString(R.string.state_failed);
            default:return getString(R.string.state_offline);
        }
    }
    private int problem(){switch(session.problem){case IDENTITY:return R.string.problem_identity;case PAIRING:return R.string.problem_pairing;case IDENTITY_CHANGED:return R.string.problem_identity_changed;case STORAGE:return R.string.problem_storage;case CONNECTION:return R.string.problem_connection;case PROTOCOL:return R.string.problem_protocol;default:return 0;}}
    private int messageState(String state){switch(state){case "Pending":return R.string.pending;case "Sending":return R.string.sending;case "Sent":return R.string.sent;case "Delivered":return R.string.delivered;default:return R.string.failed;}}
    private int fileState(String state){if(state.startsWith("Complete"))return R.string.file_complete;if(state.startsWith("Cancelled"))return R.string.file_cancelled;if(state.startsWith("Offered"))return R.string.file_offered;if(state.startsWith("Receiving"))return R.string.file_receiving;if(state.startsWith("Sending"))return R.string.file_sending;if(state.startsWith("Waiting"))return R.string.file_waiting;if(state.startsWith("Interrupted"))return R.string.file_interrupted;if(state.startsWith("Provider"))return R.string.file_busy;return R.string.failed;}
    private String bytes(long value){return android.text.format.Formatter.formatFileSize(this,value);}
    private void render(){
        if(isFinishing()||isDestroyed())return;
        status.setText(connectionStatus());status.setTextColor(color(session.state==ChatSession.State.FAILED?R.color.terminal_error:R.color.terminal_secondary));
        secure.setText(session.ready?R.string.secure_verified:session.verified?R.string.secure_waiting:!session.remoteFingerprint.isEmpty()?R.string.secure_pending:R.string.secure_unpaired);
        secure.setTextColor(color(session.ready?R.color.terminal_green:R.color.terminal_amber));
        pairingActions.setVisibility(session.canQueue()?View.GONE:View.VISIBLE);share.setVisibility(session.invitation.isEmpty()?View.GONE:View.VISIBLE);verify.setVisibility(session.remoteFingerprint.isEmpty()?View.GONE:View.VISIBLE);
        send.setEnabled(session.canQueue());attach.setEnabled(session.ready&&session.filesEnabled);
        accept.setVisibility(session.incomingOffer==null?View.GONE:View.VISIBLE);
        cancelFile.setVisibility(session.fileState.isEmpty()||session.fileState.startsWith("Complete")||session.fileState.startsWith("Cancelled")||session.fileState.startsWith("Failed")?View.GONE:View.VISIBLE);
        fileStatus.setVisibility(session.fileState.isEmpty()?View.GONE:View.VISIBLE);
        if(!session.fileState.isEmpty())fileStatus.setText(getString(R.string.file_line,session.fileName,bytes(session.fileSize),getString(fileState(session.fileState))+" · "+getString(R.string.file_progress,bytes(session.fileProgress),bytes(session.fileSize))));
        int error=problem();notice.setText(session.cleanupRequired?R.string.cleanup_required:error!=0?error:!session.invitation.isEmpty()?R.string.invitation_ready:R.string.foreground_notice);
        notice.setTextColor(color(session.cleanupRequired||error!=0?R.color.terminal_error:R.color.terminal_secondary));
        adapter.refresh();
        findViewById(R.id.messages).setContentDescription(getResources().getQuantityString(R.plurals.message_count,session.history.size(),session.history.size()));
    }
    private void showMenu(View anchor){
        PopupMenu popup=new PopupMenu(this,anchor,Gravity.END);
        if(!session.invitation.isEmpty())add(popup,R.string.copy_invitation,this::copyInvitation);
        if(!session.invitation.isEmpty()||(!session.remoteFingerprint.isEmpty()&&!session.ready))add(popup,R.string.cancel_pairing,session::cancelPairing);
        add(popup,R.string.verification,this::verifyDialog);add(popup,R.string.reconnect,session::start);add(popup,R.string.disconnect,session::disconnect);add(popup,R.string.retry_messages,session::retryMessages);
        add(popup,R.string.older_history,session::loadOlder);add(popup,R.string.latest_history,session::latestHistory);add(popup,R.string.settings,this::settings);popup.show();
    }
    private void add(PopupMenu popup,int label,Runnable action){popup.getMenu().add(label).setOnMenuItemClickListener(item->{action.run();return true;});}
    private void settings(){
        int[] entries={R.string.language,R.string.diagnostics,R.string.clear_history,R.string.reset_trust,R.string.reset_identity,R.string.licenses};String[] labels=new String[entries.length];for(int i=0;i<entries.length;i++)labels[i]=getString(entries[i]);
        new AlertDialog.Builder(this).setTitle(R.string.settings).setItems(labels,(dialog,index)->{
            switch(index){case 0:language();break;case 1:new AlertDialog.Builder(this).setTitle(R.string.diagnostics).setMessage(getString(R.string.diagnostics_text,connectionStatus(),session.bootstrap,getString(session.verified?R.string.secure_verified:R.string.secure_pending),getString(R.string.foreground_notice),session.diagnostic.isEmpty()?getString(R.string.no_diagnostic):session.diagnostic)).setPositiveButton(R.string.close,null).show();break;
            case 2:confirm(R.string.clear_history,R.string.clear_history_question,session::clearHistory);break;case 3:confirm(R.string.reset_trust,R.string.reset_trust_question,session::resetTrust);break;case 4:confirm(R.string.reset_identity,R.string.reset_identity_question,session::resetIdentity);break;case 5:licenses();break;}
        }).show();
    }
    private void confirm(int title,int question,Runnable action){new AlertDialog.Builder(this).setTitle(title).setMessage(question).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,(dialog,which)->action.run()).show();}
    private void language(){String[] codes={"","en","uk"};String[] labels={getString(R.string.system_language),getString(R.string.language_en),getString(R.string.language_uk)};
        new AlertDialog.Builder(this).setTitle(R.string.language).setItems(labels,(dialog,index)->{if(Build.VERSION.SDK_INT>=33){getSharedPreferences("ui",0).edit().remove("language").apply();getSystemService(android.app.LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(codes[index]));}
            else{getSharedPreferences("ui",0).edit().putString("language",codes[index]).apply();recreate();}}).show();}
    private void licenses(){new Thread(()->{
        try{StringBuilder content=new StringBuilder();for(String file:new String[]{"LICENSE","TOR_LICENSE.txt","BOUNCY_CASTLE_LICENSE.html","CONSCRYPT_LICENSE.txt","CONSCRYPT_NOTICE.txt","CONSCRYPT_NETTY_LICENSE.txt","CONSCRYPT_HARMONY_LICENSE.txt","BORINGSSL_LICENSE.txt","JTORCTL_LICENSE.txt"}){
            try(java.io.InputStream in=getAssets().open(file);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] block=new byte[4096];int size;while((size=in.read(block))!=-1){if(out.size()+size>1024*1024)throw new java.io.IOException();out.write(block,0,size);}content.append(out.toString("UTF-8")).append('\n');}}
            runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())new AlertDialog.Builder(this).setTitle(R.string.licenses).setMessage(content.toString()).setPositiveButton(R.string.close,null).show();});
        }catch(Exception e){runOnUiThread(()->toast(R.string.licenses_unavailable));}
    },"licenses").start();}
    private void verifyDialog(){
        String expected=session.remoteFingerprint;
        LinearLayout content=column();int pad=dimension(R.dimen.terminal_padding);content.setPadding(pad,pad,pad,pad);
        TextView help=text(R.string.verification_instructions,R.dimen.terminal_message_size);content.addView(help);
        TextView fingerprints=text(0,R.dimen.terminal_message_size);fingerprints.setText(getString(R.string.fingerprints,session.localFingerprint,expected.isEmpty()?getString(R.string.not_connected):expected));fingerprints.setTextIsSelectable(true);content.addView(fingerprints);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.verification).setView(scroll).setNegativeButton(R.string.close,null).setPositiveButton(R.string.verify_match,(d,w)->session.verifyPeer(expected)).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!expected.isEmpty()&&!session.verified));dialog.show();
    }
    private String invitation(){try{TorInvite.decode(session.invitation,System.currentTimeMillis());return session.invitation;}catch(IllegalArgumentException e){toast(R.string.invalid_invitation);return "";}}
    private void shareInvitation(){String value=invitation();if(value.isEmpty())return;startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value),getString(R.string.share_invitation)));}
    private void copyInvitation(){String value=invitation();if(value.isEmpty())return;ClipData data=ClipData.newPlainText(getString(R.string.clipboard_label),value);PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);data.getDescription().setExtras(extras);((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(data);toast(R.string.copied);}
    private void joinDialog(String initial){
        if(pairing!=null)pairing.dismiss();
        LinearLayout content=column();int pad=dimension(R.dimen.terminal_padding);content.setPadding(pad,pad,pad,pad);
        EditText input=new EditText(this);input.setId(R.id.pair_input);input.setHint(R.string.invitation_hint);input.setTypeface(Typeface.MONOSPACE);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setMinLines(2);input.setMaxLines(4);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);input.setText(initial);content.addView(input);
        Button paste=button(R.string.paste_invitation,()->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData data=clipboard.getPrimaryClip();CharSequence text=data!=null&&data.getItemCount()>0?data.getItemAt(0).getText():null;if(text==null||text.length()>512){toast(R.string.invalid_invitation);return;}input.setText(text);});content.addView(paste);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        pairing=new AlertDialog.Builder(this).setTitle(R.string.join_chat).setView(scroll).setNegativeButton(R.string.cancel,(d,w)->{pendingImport="";session.cancelPairing();}).setPositiveButton(R.string.connect,null).create();
        pairing.setOnCancelListener(d->{pendingImport="";session.cancelPairing();});
        pairing.setOnShowListener(d->{pairing.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.pair_connect);pairing.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=input.getText().toString();try{TorInvite.decode(value,System.currentTimeMillis());}catch(IllegalArgumentException e){toast(R.string.invalid_invitation);return;}pendingImport="";session.joinChat(value);pairing.dismiss();});});pairing.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);pairing.show();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);inspectIntent(intent);}
    private void inspectIntent(Intent intent){
        if(intent==null)return;CharSequence value=null;
        try { if(Intent.ACTION_VIEW.equals(intent.getAction()))value=intent.getDataString();else if(Intent.ACTION_SEND.equals(intent.getAction()))value=intent.getCharSequenceExtra(Intent.EXTRA_TEXT); }
        catch(RuntimeException e){toast(R.string.invalid_invitation);return;}
        if(value==null)return;
        intent.setData(null);intent.removeExtra(Intent.EXTRA_TEXT);intent.setAction(Intent.ACTION_MAIN);
        if(value.length()>512){toast(R.string.invalid_invitation);return;}
        try{TorInvite.decode(value.toString(),System.currentTimeMillis());pendingImport=value.toString();joinDialog(pendingImport);}catch(IllegalArgumentException e){toast(R.string.invalid_invitation);}
    }
    private void pickSource(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),101);}catch(ActivityNotFoundException e){toast(R.string.file_picker_unavailable);}}
    private void acceptFile(){FileOffer offer=session.incomingOffer;if(offer==null)return;saving=offer.id;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(offer.mime).putExtra(Intent.EXTRA_TITLE,offer.name),102);}catch(ActivityNotFoundException e){toast(R.string.file_picker_unavailable);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==101)session.offerFile(uri);else if(request==102&&saving!=null){session.acceptFile(saving,uri);saving=null;}}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("saving",saving==null?null:saving.toString());out.putString("import",pendingImport);session.draft=composer.getText().toString();super.onSaveInstanceState(out);}
    @Override protected void onStart(){super.onStart();session.watch(this::render);session.foreground();}
    @Override protected void onStop(){session.background();super.onStop();}
    @Override protected void onDestroy(){session.watch(null);if(pairing!=null)pairing.dismiss();super.onDestroy();}
    private final class LogAdapter extends BaseAdapter {
        private List<MessageDatabase.Row> messages=Collections.emptyList();private List<MessageDatabase.TransferRow> files=Collections.emptyList();
        private final List<Object> entries=new ArrayList<>();
        private long created(Object entry){return entry instanceof MessageDatabase.Row?((MessageDatabase.Row)entry).created:((MessageDatabase.TransferRow)entry).created;}
        void refresh(){if(messages!=session.history||files!=session.transferHistory){messages=session.history;files=session.transferHistory;entries.clear();entries.addAll(messages);entries.addAll(files);entries.sort(Comparator.comparingLong(this::created));notifyDataSetChanged();}}
        public int getCount(){return entries.size();}public Object getItem(int position){return entries.get(position);}public long getItemId(int position){return position;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){LinearLayout item=column();int gap=dimension(R.dimen.terminal_gap);item.setPadding(0,gap,0,gap);
            TextView body=text(0,R.dimen.terminal_message_size);body.setTextIsSelectable(true);item.addView(body);
            if(getItem(position) instanceof MessageDatabase.Row){MessageDatabase.Row row=(MessageDatabase.Row)getItem(position);String time=android.text.format.DateFormat.getTimeFormat(MainActivity.this).format(new Date(row.created));body.setText(getString(R.string.message_line,time,getString(row.outgoing?R.string.you:R.string.peer),row.body));body.setTextColor(color(row.outgoing?R.color.terminal_green:R.color.terminal_primary));
                if(!row.peer.equals(session.historyPeer)){TextView archive=text(R.string.previous_pairing,R.dimen.terminal_status_size);archive.setTextColor(color(R.color.terminal_secondary));item.addView(archive);}
                if(row.outgoing){TextView delivery=text(0,R.dimen.terminal_status_size);delivery.setText(getString(R.string.status_line,getString(messageState(row.state))));delivery.setTextColor(color(row.state.equals("Failed")?R.color.terminal_error:R.color.terminal_secondary));item.addView(delivery);}
            }else{MessageDatabase.TransferRow file=(MessageDatabase.TransferRow)getItem(position);body.setText(getString(R.string.file_line,file.name,bytes(file.size),getString(fileState(file.state))+" · "+getString(R.string.file_progress,bytes(file.progress),bytes(file.size))));if(file.state.contains("cleanup failed"))item.addView(text(R.string.cleanup_required,R.dimen.terminal_status_size));body.setTextColor(color(R.color.terminal_amber));if(!file.peer.equals(session.historyPeer))item.addView(text(R.string.previous_pairing,R.dimen.terminal_status_size));}
            return item;
        }
    }
}
