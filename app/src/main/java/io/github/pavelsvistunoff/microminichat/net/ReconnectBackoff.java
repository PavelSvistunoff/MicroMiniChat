package io.github.pavelsvistunoff.microminichat.net;
import java.security.SecureRandom;
import java.util.function.DoubleSupplier;
/** One serialized retry timer, exponential delays with jitter, bounded at five minutes. */
public final class ReconnectBackoff {
    private final DoubleSupplier random;
    private int attempt;
    public ReconnectBackoff() { this(new SecureRandom()::nextDouble); }
    public ReconnectBackoff(DoubleSupplier random) { this.random = random; }
    public long nextDelay() {
        long base = Math.min(300000L, 1000L << Math.min(attempt++, 9));
        attempt = Math.min(attempt, 10);
        return Math.max(1000L, Math.min(300000L, Math.round(base * (0.75 + 0.5 * random.getAsDouble()))));
    }
    public void reset() { attempt = 0; }
    public static boolean preferred(String local,String remote,boolean outgoing) {
        if (local.equals(remote)) throw new IllegalArgumentException("SELF_CONNECTION");
        return outgoing == (local.compareTo(remote) < 0);
    }
}
