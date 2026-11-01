package io.github.pavelsvistunoff.microminichat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import io.github.pavelsvistunoff.microminichat.signal.Signal;

public final class MainActivity extends Activity {
    private ChatSession session;
    private EditText messageInput;
    private Button sendButton;
    private Button attachButton;
    private Button saveButton;
    private TextView transferStatus;
    private String savingId;
    private ArrayAdapter<String> rows;
    private java.util.List<String> renderedRows;

    private TextView status;
    private TextView identity;
    private TextView pairStatus;
    private LinearLayout fileControls;
    private TextView exported;
    private EditText imported;
    private AlertDialog pairing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        session = ((ChatApp) getApplication()).session();
        if (state != null) savingId=state.getString("saving_id");
        LinearLayout root = column();
        status = text(root, "Disconnected");
        status.setId(R.id.status); status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);root.addView(toolbar);
        button(toolbar,"Pair / verify",this::pair);
        button(toolbar,"Disconnect",session::disconnect);
        button(toolbar,"Settings",this::settings);
        ListView messages = new ListView(this); messages.setId(R.id.messages);
        rows = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new java.util.ArrayList<>());
        messages.setAdapter(rows); root.addView(messages,new LinearLayout.LayoutParams(-1,0,1));
        messageInput = input(root,"Plain-text message",16384); messageInput.setId(R.id.chat_input);
        messageInput.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        sendButton = new Button(this); sendButton.setId(R.id.send); sendButton.setText("Send");
        sendButton.setOnClickListener(v -> {
            String value = messageInput.getText().toString();
            try { io.github.pavelsvistunoff.microminichat.protocol.Frame.textBytes(value); }
            catch (IllegalArgumentException e) { toast("Message must be valid UTF-8 text, 1–16,384 bytes"); return; }
            session.sendText(value); messageInput.setText("");
        });
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);root.addView(actions);
        actions.addView(sendButton,new LinearLayout.LayoutParams(0,-2,1));
        attachButton = new Button(this); attachButton.setId(R.id.attach); attachButton.setText("Attach file");
        attachButton.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),101)); actions.addView(attachButton,new LinearLayout.LayoutParams(0,-2,1));
        transferStatus=text(root,"");
        fileControls=new LinearLayout(this);fileControls.setOrientation(LinearLayout.HORIZONTAL);root.addView(fileControls);
        saveButton=new Button(this);saveButton.setText("Save incoming file");
        saveButton.setOnClickListener(v -> {
            io.github.pavelsvistunoff.microminichat.files.FileOffer offer=session.incomingOffer;
            if (offer==null) return;
            savingId=offer.id.toString();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(offer.mime).putExtra(Intent.EXTRA_TITLE,offer.name),102);
        });fileControls.addView(saveButton,new LinearLayout.LayoutParams(0,-2,1));
        button(fileControls,"Cancel transfers",session::cancelTransfers);
        final int padding=(int)(16*getResources().getDisplayMetrics().density);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets edges=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());
                view.setPadding(padding+edges.left,padding+edges.top,padding+edges.right,padding+edges.bottom);
            }
            return insets;
        });
        setContentView(root);
        receive(getIntent());
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,pad); return layout;
    }
    private TextView text(LinearLayout root, String value) {
        TextView view = new TextView(this); view.setText(value); view.setTextIsSelectable(true);
        root.addView(view); return view;
    }
    private void button(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run());
        if (root.getOrientation()==LinearLayout.HORIZONTAL) root.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        else root.addView(button);
    }
    private EditText input(LinearLayout root, String hint, int limit) {
        EditText view = new EditText(this); view.setHint(hint); view.setMaxLines(5);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)}); view.setSaveEnabled(false);
        root.addView(view); return view;
    }
    private void render() {
        status.setText(session.status);
        if (identity!=null) identity.setText("My fingerprint:\n"+session.localFingerprint+"\nPeer fingerprint:\n"+session.remoteFingerprint);
        if (pairStatus!=null) pairStatus.setText(session.status);
        if (exported != null) exported.setText(session.localSignal);
        sendButton.setEnabled(session.ready);
        attachButton.setEnabled(session.ready && session.filesEnabled);
        saveButton.setEnabled(session.ready && session.incomingOffer!=null);
        transferStatus.setText(session.fileStatus);
        fileControls.setVisibility(session.fileStatus.isEmpty() && session.incomingOffer==null?android.view.View.GONE:android.view.View.VISIBLE);
        if (renderedRows != session.history) {
            renderedRows = session.history; rows.clear(); rows.addAll(renderedRows);
        }
    }
    private void settings() {
        String[] options = {"Forget trusted peer", "Reset my identity", "Third-party licenses", "Retry failed messages", "Delete conversation history", "Delete all history, metadata and identity", "Older messages", "Latest messages", "Background behavior"};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(options,(d,w) -> {
            if (w == 8) {
                new AlertDialog.Builder(this).setTitle("Interactive sessions only").setMessage("Keep the app open for chat and transfers. Short app switches have a best-effort two-minute grace for sharing or choosing files; Android may stop the process sooner. After expiry or process termination, pair again. There is no background incoming delivery, push service or foreground service.").setPositiveButton("Close",null).show();return;
            }
            if (w == 6) { session.loadOlder();return; }
            if (w == 7) { session.latestHistory();return; }
            if (w == 3) { session.retryMessages(); return; }
            if (w == 4 || w == 5) {
                new AlertDialog.Builder(this).setMessage(w == 4 ? "Disconnect and delete visible messages and transfer metadata? Minimal replay-protection receipts remain." : "Delete all local history, metadata and identity? The peer must verify a new identity; secure flash erasure is not guaranteed.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which) -> { if (w == 4) session.clearHistory(); else session.clearEverything(); }).show(); return;
            }
            if (w == 2) {
                session.loadNotices(value -> {
                    TextView notice = new TextView(this); notice.setText(value); notice.setTextIsSelectable(true);
                    ScrollView scroll = new ScrollView(this); scroll.addView(notice);
                    new AlertDialog.Builder(this).setTitle("Licenses").setView(scroll).setPositiveButton("Close",null).show();
                }); return;
            }
            new AlertDialog.Builder(this).setMessage(w == 0 ? "Disconnect and forget the verified peer? Compare fingerprints again before trusting anyone." : "Delete local history and metadata and create a new identity? The other person must verify your new fingerprint. This cannot be undone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Reset",(dialog,which) -> {
                        if (w == 0) session.resetTrust(); else session.resetIdentity();
                    }).show();
        }).setNegativeButton("Close",null).show();
    }
    private void pair() {
        if (pairing != null && pairing.isShowing()) return;
        LinearLayout form = column();
        text(form, "Signaling reveals network addresses. Exchange it through a trusted channel. No TURN; some networks cannot connect.");
        pairStatus=text(form,session.status);
        identity=text(form,"My fingerprint:\n"+session.localFingerprint+"\nPeer fingerprint:\n"+session.remoteFingerprint);
        button(form,"Verify peer fingerprint",this::confirmVerify);
        EditText stun = input(form, "Optional stun:host:port; blank = host candidates only", 300);
        stun.setText(getPreferences(0).getString("stun", ""));
        imported = input(form, "Paste offer or answer", Signal.MAX_CHARS);
        button(form, "Paste", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence value = clip.getItemAt(0).getText();
                if (value != null && value.length() <= Signal.MAX_CHARS) imported.setText(value);
            }
        });
        button(form, "Create offer", () -> {
            String endpoint = stun.getText().toString().trim(); getPreferences(0).edit().putString("stun", endpoint).apply();
            session.offer(endpoint);
        });
        button(form, "Apply offer / answer", () -> session.apply(imported.getText().toString(), stun.getText().toString().trim()));
        exported = text(form, session.localSignal); exported.setMaxLines(5);
        button(form, "Copy local signaling", () -> copy(session.localSignal));
        button(form, "Share local signaling", () -> {
            if (session.localSignal.isEmpty()) return;
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, session.localSignal);
            try { startActivity(Intent.createChooser(send, "Share sensitive signaling")); }
            catch (ActivityNotFoundException e) { toast("No sharing application available"); }
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        pairing = new AlertDialog.Builder(this).setTitle("Manual pairing").setView(scroll).setPositiveButton("Close", null).create();
        pairing.setOnDismissListener(d -> { exported = null; imported = null; identity=null;pairStatus=null;pairing = null; }); pairing.show();
        pairing.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void confirmVerify() {
        String expected=session.remoteFingerprint;
        new AlertDialog.Builder(this).setTitle("Verify independently")
                .setMessage("Compare this full peer fingerprint with the fingerprint shown on the other person’s device through an independently authenticated channel. Do not trust a fingerprint sent only with signaling.\n\n"+expected)
                .setNegativeButton("Cancel",null).setPositiveButton("I compared; they match",(d,w)->session.verifyPeer(expected)).show();
    }
    private void copy(String value) {
        if (value.isEmpty()) return;
        ClipData clip = ClipData.newPlainText("Sensitive MicroMiniChat signaling", value);
        if (Build.VERSION.SDK_INT >= 33) {
            PersistableBundle extras = new PersistableBundle(); extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true); clip.getDescription().setExtras(extras);
        }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        toast("Copied; clipboard may retain network addresses");
    }
    private void receive(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        try {
            String value = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (value != null && value.length() <= Signal.MAX_CHARS) { pair(); imported.setText(value); }
            else toast("Invalid or oversized shared signaling");
        } catch (RuntimeException e) { toast("Malformed shared signaling"); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result!=RESULT_OK || data==null || data.getData()==null) return;
        if (request==101) session.offerFile(data.getData());
        if (request==102 && savingId!=null) {
            try { session.acceptFile(java.util.UUID.fromString(savingId),data.getData()); }
            catch (IllegalArgumentException e) { toast("File offer no longer available"); }
            savingId=null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state);state.putString("saving_id",savingId); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); receive(intent); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart();session.foreground();session.watch(this::render); }
    @Override protected void onStop() { session.watch(null);session.background();super.onStop(); }
}
