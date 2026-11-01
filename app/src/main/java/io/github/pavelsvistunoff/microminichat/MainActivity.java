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
    private TextView pairStep;
    private LinearLayout pairChoices, pairShare, pairImport, pairVerify;
    private Button pairShareButton, pairCopyButton, pairStartButton, pairConnectButton;
    private EditText pairStun, pairTurn, pairUsername, pairPassword, pairPhoneAddress, pairPhonePort;
    private CheckBox pairRelayOnly, pairOnPhone;
    private boolean joining;
    private String displayedSignal="";
    private boolean displayedOffer;
    private EditText imported;
    AlertDialog pairing;

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
    private Button button(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run());
        if (root.getOrientation()==LinearLayout.HORIZONTAL) root.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        else root.addView(button);
        return button;
    }
    private EditText input(LinearLayout root, String hint, int limit) {
        EditText view = new EditText(this); view.setHint(hint); view.setMaxLines(5);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)}); view.setSaveEnabled(false);
        root.addView(view); return view;
    }
    private void render() {
        status.setText(session.status);
        renderPairing();
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
    void pair() {
        if (pairing != null && pairing.isShowing()) return;
        LinearLayout form = column();
        pairStep=text(form, "1. Choose who starts"); pairStep.setTextSize(20);pairStep.setId(R.id.pair_step);
        pairStatus=text(form,session.status);
        text(form,"Keep both apps open. Exchange invitations through a trusted messenger; they contain network addresses.");
        pairChoices=column();form.addView(pairChoices);
        pairStartButton=button(pairChoices,"Start chat",()->submitPairing(true));pairStartButton.setId(R.id.pair_start);
        button(pairChoices,"Join chat",()->{ joining=true;renderPairing(); }).setId(R.id.pair_join);

        pairShare=column();form.addView(pairShare);
        pairShareButton=button(pairShare,"Share invitation",this::sharePairing);
        pairShareButton.setId(R.id.pair_share);
        pairCopyButton=button(pairShare,"Copy invitation",()->copy(outgoingInvitation()));

        pairImport=column();form.addView(pairImport);
        imported=input(pairImport,"Paste invitation or reply here",Signal.MAX_CHARS);imported.setMaxLines(3);imported.setId(R.id.pair_input);
        button(pairImport,"Paste & connect",()->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            ClipData clip=clipboard.getPrimaryClip();
            if (clip==null || clip.getItemCount()==0) { toast("Copy an invitation or reply first");return; }
            CharSequence value=clip.getItemAt(0).getText();
            if (value==null || value.length()>Signal.MAX_CHARS) { toast("Invalid or oversized invitation");return; }
            imported.setText(value);submitPairing(false);
        });
        pairConnectButton=button(pairImport,"Connect with pasted text",()->submitPairing(false));
        pairConnectButton.setId(R.id.pair_connect);

        pairVerify=column();form.addView(pairVerify);
        identity=text(pairVerify,"");
        button(pairVerify,"Compare fingerprints",this::confirmVerify).setId(R.id.pair_verify);

        text(form,"Phone hosting needs a publicly reachable IP and TCP port. Check Connection settings before starting. No public signaling server is used.");
        LinearLayout network=column();network.setVisibility(android.view.View.GONE);
        Button settings=button(form,"Connection settings",()->network.setVisibility(
                network.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));
        settings.setId(R.id.pair_settings);form.addView(network);network.setId(R.id.pair_network);
        pairOnPhone=new CheckBox(this);pairOnPhone.setText("Host pairing on this phone (reachable public IP required)");pairOnPhone.setChecked(true);
        pairOnPhone.setId(R.id.pair_on_phone);network.addView(pairOnPhone);
        pairPhoneAddress=input(network,"Creator’s reachable IP (public IP if forwarded)",45);
        pairPhoneAddress.setText(io.github.pavelsvistunoff.microminichat.pairing.PhoneAddress.detected(this));
        pairPhonePort=input(network,"Pairing TCP port",5);pairPhonePort.setInputType(InputType.TYPE_CLASS_NUMBER);pairPhonePort.setText("44333");
        text(network,"Forward the same TCP port to this phone if behind a router. Mobile CGNAT usually prevents hosting. Uncheck phone hosting for manual invitation/reply exchange.");
        text(network,"STUN discovers routes. TURN relays encrypted traffic when direct routes fail; its operator sees traffic metadata. Credentials are not saved.");
        pairStun=input(network,"STUN URL (optional)",300);
        pairStun.setText(getPreferences(0).getString("stun",""));
        pairTurn=input(network,"TURN URL (optional)",320);
        pairUsername=input(network,"TURN username",512);
        pairPassword=input(network,"TURN password",512);
        pairUsername.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pairPassword.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pairUsername.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        pairPassword.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        pairRelayOnly=new CheckBox(this);pairRelayOnly.setText("Relay only (requires working TURN)");network.addView(pairRelayOnly);
        button(form,"Start again",()->{ joining=false;imported.setText("");session.disconnect(); });
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        pairing=new AlertDialog.Builder(this).setTitle("Connect to your friend").setView(scroll).setPositiveButton("Back to chat",null).create();
        pairing.setOnDismissListener(d->{
            pairPassword.setText("");pairUsername.setText("");
            imported=null;identity=null;pairStatus=null;pairStep=null;pairing=null;displayedSignal="";joining=false;
            pairStun=null;pairTurn=null;pairUsername=null;pairPassword=null;pairRelayOnly=null;
            pairPhoneAddress=null;pairPhonePort=null;pairOnPhone=null;
            pairChoices=null;pairShare=null;pairImport=null;pairVerify=null;
        });
        pairing.show();renderPairing();
        pairing.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void renderPairing() {
        if (pairing==null || pairStep==null) return;
        pairStatus.setText(session.status);
        boolean hasSignal=!session.localSignal.isEmpty();
        if (hasSignal && !session.localSignal.equals(displayedSignal)) {
            displayedSignal=session.localSignal;
            try { displayedOffer=Signal.decode(displayedSignal).offer; }
            catch (IllegalArgumentException e) { displayedSignal="";hasSignal=false; }
        }
        boolean busy=session.status.equals("Gathering connection details") || session.status.equals("Connecting");
        pairChoices.setVisibility(!session.open && !hasSignal && !joining?android.view.View.VISIBLE:android.view.View.GONE);
        pairStartButton.setEnabled(!busy);
        boolean phone=session.phonePairingActive;
        pairShare.setVisibility(!outgoingInvitation().isEmpty() && !session.open?android.view.View.VISIBLE:android.view.View.GONE);
        pairShareButton.setText(displayedOffer?"Share invitation":"Share reply");
        pairCopyButton.setText(displayedOffer?"Copy invitation":"Copy reply");
        pairImport.setVisibility(!session.open && !phone && (hasSignal?displayedOffer:joining)?android.view.View.VISIBLE:android.view.View.GONE);
        pairConnectButton.setEnabled(!busy);
        imported.setHint(hasSignal && displayedOffer?"Paste your friend’s reply":"Paste your friend’s invitation");
        pairVerify.setVisibility(session.open?android.view.View.VISIBLE:android.view.View.GONE);
        identity.setText("Your fingerprint:\n"+session.localFingerprint+"\nFriend’s fingerprint:\n"+session.remoteFingerprint);
        pairStep.setText(session.open?(session.ready?"Connected — return to chat":session.verified?"Waiting for your friend to verify":"3. Compare fingerprints before chatting")
                :phone?(!session.phoneInvitation.isEmpty()?"2. Share invitation — your friend joins without sending a reply":"Connecting phones automatically — please wait")
                :busy?"Preparing connection — please wait"
                :hasSignal?(displayedOffer?"2. Send invitation, then paste your friend’s reply":"2. Send this reply back to your friend")
                :joining?"1. Paste your friend’s invitation":"1. Start a chat or join your friend");
    }

    private void submitPairing(boolean start) {
        try {
            // Validate shared input before changing any connection or network configuration.
            String incoming=start?"":imported.getText().toString().trim();
            io.github.pavelsvistunoff.microminichat.pairing.PhoneInvite phone=null;
            if(!start) {
                if(incoming.startsWith("microminichat:"))phone=io.github.pavelsvistunoff.microminichat.pairing.PhoneInvite.decode(incoming);
                else Signal.decode(incoming);
            }
            io.github.pavelsvistunoff.microminichat.net.IceConfig ice=new io.github.pavelsvistunoff.microminichat.net.IceConfig(
                    pairStun.getText().toString().trim(),pairTurn.getText().toString().trim(),
                    pairUsername.getText().toString(),pairPassword.getText().toString(),pairRelayOnly.isChecked());
            getPreferences(0).edit().putString("stun",ice.stun).apply();
            if(start) {
                if(pairOnPhone.isChecked()) {
                    int port=Integer.parseInt(pairPhonePort.getText().toString());
                    String address=pairPhoneAddress.getText().toString().trim();
                    if(!io.github.pavelsvistunoff.microminichat.pairing.PhoneInvite.validIp(address) || port<1 || port>65535)
                        throw new IllegalArgumentException();
                    pairStartButton.setEnabled(false);session.offerOnPhone(ice,address,port);
                } else { pairStartButton.setEnabled(false);session.offer(ice); }
            } else {
                pairConnectButton.setEnabled(false);
                if(phone!=null)session.joinPhone(phone,ice);else session.apply(incoming,ice);
            }
        } catch (IllegalArgumentException e) { toast("Check the invitation and connection settings; TURN needs URL, username and password"); }
    }

    private String outgoingInvitation() { return session.phonePairingActive?session.phoneInvitation:session.localSignal; }
    private void sharePairing() {
        if(outgoingInvitation().isEmpty())return;
        Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,outgoingInvitation());
        try { startActivity(Intent.createChooser(send,displayedOffer?"Send chat invitation":"Send chat reply")); }
        catch (ActivityNotFoundException e) { toast("No sharing application available"); }
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
        boolean view=Intent.ACTION_VIEW.equals(intent.getAction());
        if(!view && !Intent.ACTION_SEND.equals(intent.getAction()))return;
        try {
            String value=view?intent.getDataString():intent.getStringExtra(Intent.EXTRA_TEXT);
            if (value != null && value.length() <= Signal.MAX_CHARS) {
                String incoming=value.trim();
                if(incoming.startsWith("microminichat:"))io.github.pavelsvistunoff.microminichat.pairing.PhoneInvite.decode(incoming);
                else Signal.decode(incoming);
                pair();joining=true;imported.setText(incoming);renderPairing();
            }
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
