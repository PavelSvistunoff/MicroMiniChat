package io.github.pavelsvistunoff.microminichat.tor;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.freehaven.tor.control.TorControlConnection;

/** Runs the bundled Tor executable in a child process, never reinitializing native globals. */
public final class TorRuntime implements AutoCloseable {
    public interface Listener {
        void state(int progress, boolean ready, boolean failed);
        default void onionReady(String address) { }
    }
    private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();
    private final Context context;
    private final Listener listener;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final OnionIdentity onion;
    private final int listenerPort;
    private final String storage;
    private volatile Process process;
    private volatile Socket controlSocket;
    private TorControlConnection control;
    private volatile int socks;
    private long started;
    public volatile String failureCode = "";
    private String onionAddress = "";
    private volatile boolean published;
    public TorRuntime(Context context, Listener listener) { this(context, listener, 0); }
    public TorRuntime(Context context, Listener listener, int listenerPort) { this(context, listener, listenerPort, "main"); }
    public TorRuntime(Context context, Listener listener, int listenerPort, String storage) {
        if (!storage.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException();
        this.storage = storage;
        this.context = context.getApplicationContext();
        if (listenerPort < 0 || listenerPort > 65535) throw new IllegalArgumentException();
        this.listenerPort = listenerPort;
        onion = new OnionIdentity(this.context, storage);
        this.listener = listener;
    }
    public static boolean isNativeRunning() { return PROCESSES.stream().anyMatch(Process::isAlive); }
    public void start() {
        worker.execute(() -> {
            if (closed.get() || process != null) return;
            String stage = "PREPARE";
            try {
                File data = new File(context.getNoBackupFilesDir(), "tor-data" + (storage.equals("main") ? "" : "-" + storage));
                if (!data.isDirectory() && !data.mkdir()) throw new IOException();
                File portFile = new File(data, "control-port");
                if (portFile.exists() && !portFile.delete()) throw new IOException();
                List<String> args = new ArrayList<>(Arrays.asList(
                        new File(context.getApplicationInfo().nativeLibraryDir, "libtor.so").getAbsolutePath(),
                        "--ignore-missing-torrc", "-f", new File(data, "unused-torrc").getAbsolutePath(),
                        "--DataDirectory", data.getAbsolutePath(), "--RunAsDaemon", "0",
                        "--SocksPort", "127.0.0.1:auto", "--HTTPTunnelPort", "0",
                        "--ControlPort", "127.0.0.1:auto", "--ControlPortWriteToFile", portFile.getAbsolutePath(),
                        "--CookieAuthentication", "1", "--SafeLogging", "1", "--Log", "err file /dev/null",
                        "--DisableNetwork", "1", "--__OwningControllerProcess", Integer.toString(android.os.Process.myPid())));
                if (listenerPort > 0) Collections.addAll(args, "--HiddenServiceDir", onion.prepare().getAbsolutePath(),
                        "--HiddenServiceVersion", "3", "--HiddenServicePort", "443 127.0.0.1:" + listenerPort);
                synchronized (this) {
                    if (closed.get()) return;
                    stage = "EXECUTE";
                    process = new ProcessBuilder(args).redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null"))).start();
                    PROCESSES.add(process);
                }
                stage = "CONTROL_WAIT";
                started = SystemClock.elapsedRealtime();
                listener.state(0, false, false);
                long deadline = started + 15000;
                while (!closed.get()) {
                    if (!process.isAlive()) throw new IOException("TOR_EXITED");
                    if (portFile.isFile() && new File(data, "control_auth_cookie").length() == 32) break;
                    if (SystemClock.elapsedRealtime() > deadline) throw new IOException("TOR_CONTROL_TIMEOUT");
                    Thread.sleep(100);
                }
                if (closed.get()) return;
                stage = "CONTROL_ENDPOINT";
                String endpoint = readSmall(portFile, 128).trim();
                if (!endpoint.matches("PORT=127\\.0\\.0\\.1:[0-9]{1,5}")) throw new IOException("TOR_CONTROL_ADDRESS");
                stage = "CONTROL_CONNECT";
                Socket socket = new Socket();
                controlSocket = socket;
                socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(endpoint.substring(endpoint.lastIndexOf(':') + 1))), 5000);
                socket.setSoTimeout(10000);
                control = new TorControlConnection(socket.getInputStream(), socket.getOutputStream());
                control.launchThread(true);
                byte[] cookie = new byte[32];
                try (DataInputStream in = new DataInputStream(new FileInputStream(new File(data, "control_auth_cookie")))) { in.readFully(cookie); }
                try { control.authenticate(cookie); } finally { Arrays.fill(cookie, (byte) 0); }
                control.takeOwnership();
                control.addRawEventListener((keyword, details) -> {
                    if (closed.get() || !"HS_DESC".equals(keyword) || onionAddress.isEmpty()) return;
                    String[] tokens = details.split(" ");
                    if (tokens.length > 1 && tokens[0].equals("UPLOADED")
                            && (tokens[1] + ".onion").equals(onionAddress) && !published) {
                        published = true;
                        listener.onionReady(onionAddress);
                    }
                });
                control.setEvents(Arrays.asList("STATUS_CLIENT", "HS_DESC"));
                if (listenerPort > 0) onionAddress = onion.address();
                control.setConf("DisableNetwork", "0");
                String value = control.getInfo("net/listeners/socks");
                if (!value.matches("\"127\\.0\\.0\\.1:[0-9]{1,5}\"")) throw new IOException("TOR_SOCKS_ADDRESS");
                socks = Integer.parseInt(value.substring(value.lastIndexOf(':') + 1, value.length() - 1));
                worker.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
            } catch (Exception e) {
                String code = e.getMessage();
                fail(code != null && code.matches("TOR_[A-Z_]+") ? code : "TOR_" + stage + "_" + e.getClass().getSimpleName());
            }
        });
    }
    private void poll() {
        if (closed.get()) return;
        try {
            if (!process.isAlive()) throw new IOException("TOR_EXITED");
            if (listenerPort > 0 && onionAddress.isEmpty()) onionAddress = onion.address();
            int progress = bootstrapProgress(control.getInfo("status/bootstrap-phase"));
            listener.state(progress, progress == 100, false);
            if (progress == 100 && (listenerPort == 0 || published)) return;
            if (SystemClock.elapsedRealtime() - started > 300000) fail("TOR_BOOTSTRAP_TIMEOUT");
        } catch (Exception e) { fail("TOR_CONTROL_" + e.getClass().getSimpleName()); }
    }
    private void fail(String code) {
        if (closed.get()) return;
        failureCode = code;
        listener.state(0, false, true);
        close();
    }
    private static String readSmall(File file, int maximum) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int next;
            while ((next = in.read()) != -1) { if (out.size() >= maximum) throw new IOException(); out.write(next); }
            return out.toString("US-ASCII");
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
        if (closed.get() || socks < 1) throw new IOException("TOR_UNAVAILABLE");
        return socks;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.shutdownNow();
        Socket socket = controlSocket;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        synchronized (this) {
            Process child = process;
            if (child != null) {
                child.destroy();
                new Thread(() -> {
                    try { if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(); } }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); child.destroyForcibly(); }
                    finally { PROCESSES.remove(child); }
                }, "tor-cleanup").start();
            }
        }
    }
}
