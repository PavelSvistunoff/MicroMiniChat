package io.github.pavelsvistunoff.microminichat.probe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Manual experimental SDP exchange. Intentionally separate from the product UI. */
public final class ProbeActivity extends Activity {
    private ProbePeer peer;
    private TextView status;
    private TextView output;
    private EditText input;
    private EditText stun;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        TextView warning = new TextView(this);
        warning.setText(R.string.probe_warning);
        form.addView(warning);
        status = new TextView(this);
        status.setText(R.string.disconnected);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(status);
        stun = new EditText(this);
        stun.setHint("Optional explicit stun:host:port; blank = host candidates only");
        stun.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        stun.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        stun.setSaveEnabled(false);
        form.addView(stun);
        input = new EditText(this);
        input.setHint("Paste remote raw SDP here");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ProbeRules.MAX_SDP_BYTES)});
        input.setSaveEnabled(false);
        form.addView(input);
        addButton(form, "Create offer", () -> newPeer().offer(stun.getText().toString().trim()));
        addButton(form, "Create answer from pasted offer", () ->
                newPeer().answer(stun.getText().toString().trim(), input.getText().toString()));
        addButton(form, "Apply pasted answer", () -> {
            if (peer != null) peer.acceptAnswer(input.getText().toString());
        });
        addButton(form, "Send synthetic roundtrip probe", () -> {
            if (peer != null) peer.sendSynthetic();
        });
        addButton(form, "Disconnect", this::disconnect);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setSaveEnabled(false);
        form.addView(output);
        addButton(form, "Copy local SDP (sensitive)", () -> {
            if (output.length() == 0) return;
            ClipData clip = ClipData.newPlainText("Sensitive P2P SDP", output.getText());
            if (Build.VERSION.SDK_INT >= 33) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                clip.getDescription().setExtras(extras);
            }
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        setContentView(scroll);
    }

    private void addButton(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }

    private ProbePeer newPeer() {
        disconnect();
        ProbePeer.Listener listener = new ProbePeer.Listener() {
            @Override public void onState(String value) { update(() -> status.setText(value)); }
            @Override public void onLocalSdp(boolean offer, String sdp) { update(() -> output.setText(sdp)); }
            @Override public void onRoundTrip() { update(() -> status.setText(R.string.roundtrip_ok)); }
            private void update(Runnable change) {
                // Suppress stale callbacks from previously disconnected peers.
                ProbePeer.Listener current = this;
                runOnUiThread(() -> { if (current == activeListener) change.run(); });
            }
        };
        activeListener = listener;
        peer = new ProbePeer(this, listener);
        return peer;
    }

    private ProbePeer.Listener activeListener;

    private void disconnect() {
        activeListener = null;
        if (peer != null) { peer.close(); peer = null; }
        if (output != null) output.setText("");
        if (status != null) status.setText(R.string.disconnected);
    }

    @Override protected void onStop() {
        disconnect();
        input.setText("");
        super.onStop();
    }
}
