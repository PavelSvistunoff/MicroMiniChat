package io.github.pavelsvistunoff.microminichat.lifecycle;

/** A best-effort grace, not a promise that Android will run a background process. */
public final class ForegroundPolicy {
    private final long grace;
    private long since=-1;
    public ForegroundPolicy(long grace){if(grace<1 || grace>120000)throw new IllegalArgumentException();this.grace=grace;}
    public void background(long now){if(now<0)throw new IllegalArgumentException();if(since<0)since=now;}
    public boolean expired(long now){return since>=0 && now>=since && now-since>=grace;}
    public boolean foreground(long now){boolean expired=expired(now);since=-1;return expired;}
    public long grace(){return grace;}
}
