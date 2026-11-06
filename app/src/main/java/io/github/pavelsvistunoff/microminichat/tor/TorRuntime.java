package io.github.pavelsvistunoff.microminichat.tor;

import android.content.*;
import android.os.IBinder;
import org.torproject.jni.TorService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-private embedded Tor. All control operations run off the Android main thread. */
public final class TorRuntime implements AutoCloseable {
    public interface Listener { void state(int progress, boolean ready, boolean failed);
        default void onionReady(String address) { } }
    private final Context context;
    private final Listener listener;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile TorService service;
    private boolean bound;
    private long started;
    private final OnionIdentity onion;
    private final int listenerPort;
    private boolean eventsConfigured;
    public volatile String failureCode = "";
    private String stage = "START";
    private volatile String onionAddress = "";
    private volatile boolean published;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((TorService.LocalBinder) binder).getService();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            if (!closed.get()) listener.state(0, false, true);
        }
    };
    public TorRuntime(Context context, Listener listener) {
        this(context, listener, 0);
    }
    public TorRuntime(Context context, Listener listener, int listenerPort) {
        this.context = context.getApplicationContext();
        if (listenerPort < 0 || listenerPort > 65535) throw new IllegalArgumentException();
        this.listenerPort = listenerPort;
        onion = new OnionIdentity(this.context);
        this.listener = listener;
    }
    public static boolean isNativeRunning() { return TorService.isNativeRunning(); }
    public void start() {
        worker.execute(() -> {
            if (closed.get() || bound) return;
            try {
                long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
                while (TorService.isNativeRunning()) {
                    if (closed.get()) return;
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) throw new IOException("TOR_STOPPING");
                    Thread.sleep(100);
                }
                File config = TorService.getTorrc(context);
                try (FileOutputStream out = new FileOutputStream(config)) {
                    String text = "SocksPort 127.0.0.1:auto\nHTTPTunnelPort 0\nSafeLogging 1\nLog err file /dev/null\n";
                    text += "DisableNetwork 1\n";
                    if (listenerPort > 0) {
                        text += "HiddenServiceDir " + onion.prepare().getAbsolutePath() + "\n"
                                + "HiddenServiceVersion 3\nHiddenServicePort 443 127.0.0.1:" + listenerPort + "\n";
                    }
                    out.write(text.getBytes(StandardCharsets.US_ASCII));
                    out.getFD().sync();
                }
                started = android.os.SystemClock.elapsedRealtime();
                synchronized (this) {
                    if (closed.get()) return;
                    bound = context.bindService(new Intent(context, TorService.class), connection, Context.BIND_AUTO_CREATE);
                }
                if (!bound) throw new IOException("TOR_BIND_FAILED");
                listener.state(0, false, false);
                worker.scheduleWithFixedDelay(this::poll, 1, 1, TimeUnit.SECONDS);
            } catch (Exception e) { listener.state(0, false, true); }
        });
    }
    private void poll() {
        if (closed.get()) return;
        TorService current = service;
        try {
            if (current != null && current.getTorControlConnection() != null) {
                stage = "CONTROL";
                if (!eventsConfigured) {
                    current.getTorControlConnection().addRawEventListener((keyword, data) -> {
                        if (closed.get() || !"HS_DESC".equals(keyword) || onionAddress.isEmpty()) return;
                        String[] tokens = data.split(" ");
                        if (tokens.length > 1 && tokens[0].equals("UPLOADED")
                                && (tokens[1] + ".onion").equals(onionAddress)) {
                            published = true;
                            listener.onionReady(onionAddress);
                        }
                    });
                    current.getTorControlConnection().setEvents(java.util.Arrays.asList("STATUS_CLIENT", "HS_DESC"));
                    current.getTorControlConnection().setConf("DisableNetwork", "0");
                    eventsConfigured = true;
                }
                stage = "ONION_IDENTITY";
                if (listenerPort > 0 && onionAddress.isEmpty()) onionAddress = onion.address();
                stage = "BOOTSTRAP";
                String phase = current.getTorControlConnection().getInfo("status/bootstrap-phase");
                int progress = bootstrapProgress(phase);
                listener.state(progress, progress == 100, false);
                if (progress == 100 && (listenerPort == 0 || published)) return;
            }
            if (android.os.SystemClock.elapsedRealtime() - started > 180000) {
                listener.state(0, false, true);
                close();
            }
        } catch (Exception e) {
            failureCode = stage + "_" + e.getClass().getSimpleName();
            listener.state(0, false, true); close();
        }
    }
    public static int bootstrapProgress(String phase) throws IOException {
        if (phase == null || phase.length() > 4096) throw new IOException("TOR_STATUS");
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?:^| )PROGRESS=([0-9]{1,3})(?: |$)").matcher(phase);
        if (!match.find()) throw new IOException("TOR_STATUS");
        int value = Integer.parseInt(match.group(1));
        if (value > 100) throw new IOException("TOR_STATUS");
        return value;
    }
    public int socksPort() throws IOException {
        TorService current = service;
        if (closed.get() || current == null || current.getSocksPort() < 1) throw new IOException("TOR_UNAVAILABLE");
        return current.getSocksPort();
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.shutdownNow();
        synchronized (this) {
            if (bound) { context.unbindService(connection); bound = false; }
        }
        service = null;
    }
}
