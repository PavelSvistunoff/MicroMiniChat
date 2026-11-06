package io.github.pavelsvistunoff.microminichat;

import android.app.Application;

public final class ChatApp extends Application {
    private TorChatSession tor;
    private ChatSession legacyTestSession;
    @Override public void onCreate() { super.onCreate(); tor = new TorChatSession(this); }
    public TorChatSession torSession() { return tor; }
    /** Transitional test-only accessor, removed with the obsolete transport in M17. */
    public ChatSession session() { if(legacyTestSession==null)legacyTestSession=new ChatSession(this);return legacyTestSession; }
}
