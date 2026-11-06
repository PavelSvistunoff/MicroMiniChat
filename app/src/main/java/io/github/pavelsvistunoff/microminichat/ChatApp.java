package io.github.pavelsvistunoff.microminichat;

import android.app.Application;

public final class ChatApp extends Application {
    private ChatSession session;
    @Override public void onCreate() {
        super.onCreate();
        session = new ChatSession(this);
    }
    public ChatSession session() { return session; }
}
