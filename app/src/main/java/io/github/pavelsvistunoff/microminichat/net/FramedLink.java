package io.github.pavelsvistunoff.microminichat.net;

import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One TLS stream; bounded control-priority multiplexing, explicit two-sided trust gate. */
public final class FramedLink implements AutoCloseable {
    public interface Listener {
        void onReady();
        void onFrame(boolean file, byte[] bytes);
        void onClosed();
    }
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "stream-deadlines"); thread.setDaemon(true); return thread;
    });
    private final Socket socket;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean(), announced = new AtomicBoolean();
    private final ArrayDeque<byte[]> controls = new ArrayDeque<>(), files = new ArrayDeque<>();
    private int controlBytes, fileBytes;
    private volatile boolean localVerified, remoteVerified;
    private volatile long writing, lastSent = System.nanoTime(), created = System.nanoTime();
    private ScheduledFuture<?> deadline;
    public FramedLink(Socket socket, Listener listener) { this.socket = socket; this.listener = listener; }
    public void start() throws IOException {
        socket.setSoTimeout(90000);
        deadline = TIMER.scheduleWithFixedDelay(() -> {
            long now = System.nanoTime();
            if ((writing != 0 && now - writing > TimeUnit.SECONDS.toNanos(60))
                    || (!announced.get() && now - created > TimeUnit.MINUTES.toNanos(5))) { close(); return; }
            if (now - lastSent > TimeUnit.SECONDS.toNanos(30)) enqueue(3, new byte[0]);
        }, 1, 1, TimeUnit.SECONDS);
        new Thread(this::writeLoop, "tls-writer").start();
        new Thread(this::readLoop, "tls-reader").start();
    }
    public void verify() {
        if (closed.get()) return;
        localVerified = true;
        if (!enqueue(2, new byte[0])) { close(); return; }
        ready();
    }
    private void ready() { if (localVerified && remoteVerified && announced.compareAndSet(false, true)) listener.onReady(); }
    public void send(boolean file, byte[] bytes, Consumer<Boolean> accepted) {
        if (!announced.get() || bytes == null || bytes.length > Frame.MAX_FRAME || bytes.length < Frame.HEADER) {
            accepted.accept(false); return;
        }
        accepted.accept(enqueue(file ? 1 : 0, bytes));
    }
    private synchronized boolean enqueue(int channel, byte[] payload) {
        int size = payload.length + 1;
        if (closed.get() || size > Frame.MAX_FRAME + 1) return false;
        if (channel == 1 ? fileBytes + size > 128 * 1024 : controlBytes + size > 64 * 1024) return false;
        byte[] record = new byte[size]; record[0] = (byte) channel;
        System.arraycopy(payload, 0, record, 1, payload.length);
        if (channel == 1) { files.add(record); fileBytes += size; }
        else { controls.add(record); controlBytes += size; }
        notifyAll(); return true;
    }
    private void writeLoop() {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream()); out.writeInt(0x4d544302); out.flush();
            while (!closed.get()) {
                byte[] record;
                synchronized (this) {
                    while (!closed.get() && controls.isEmpty() && files.isEmpty()) wait();
                    if (closed.get()) break;
                    if (!controls.isEmpty()) { record = controls.remove(); controlBytes -= record.length; }
                    else { record = files.remove(); fileBytes -= record.length; }
                }
                writing = System.nanoTime();
                out.writeInt(record.length); out.write(record); out.flush();
                lastSent = System.nanoTime(); writing = 0;
            }
        } catch (Exception e) { close(); }
    }
    public static byte[] readRecord(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 1 || size > Frame.MAX_FRAME + 1) throw new IOException("STREAM_FRAME_SIZE");
        byte[] record = new byte[size]; in.readFully(record);
        int channel = record[0] & 255;
        if (channel > 3 || (channel >= 2 && size != 1) || (channel < 2 && size < Frame.HEADER + 1))
            throw new IOException("STREAM_CHANNEL");
        return record;
    }
    private void readLoop() {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            if (in.readInt() != 0x4d544302) throw new IOException("STREAM_VERSION");
            while (!closed.get()) {
                byte[] record = readRecord(in);
                if (record[0] == 2) { remoteVerified = true; ready(); }
                else if (record[0] != 3) {
                    if (!announced.get()) throw new IOException("VERIFICATION_REQUIRED");
                    listener.onFrame(record[0] == 1, Arrays.copyOfRange(record, 1, record.length));
                }
            }
        } catch (Exception e) { close(); }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (deadline != null) deadline.cancel(false);
        try { socket.close(); } catch (IOException ignored) { }
        synchronized (this) { controls.clear(); files.clear(); controlBytes = fileBytes = 0; notifyAll(); }
        listener.onClosed();
    }
}
