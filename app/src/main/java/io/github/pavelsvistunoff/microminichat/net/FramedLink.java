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
    public volatile String failureCode = "";
    private final Socket socket;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean(), announced = new AtomicBoolean();
    private static final class Record {
        final byte[] bytes;
        final Consumer<Boolean> result;
        final AtomicBoolean completed = new AtomicBoolean();
        Record(byte[] bytes, Consumer<Boolean> result) { this.bytes = bytes; this.result = result; }
        void finish(boolean written) {
            if (result != null && completed.compareAndSet(false, true)) {
                try { result.accept(written); } catch (RuntimeException ignored) { }
            }
        }
    }
    private final ArrayDeque<Record> controls = new ArrayDeque<>(), files = new ArrayDeque<>();
    private volatile Record current;
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
        if (closed.get() || localVerified) return;
        localVerified = true;
        if (!enqueue(2, new byte[0])) { close(); return; }
        ready();
    }
    private void ready() { if (localVerified && remoteVerified && announced.compareAndSet(false, true)) listener.onReady(); }
    public void send(boolean file, byte[] bytes, Consumer<Boolean> accepted) {
        if (!announced.get() || bytes == null || bytes.length > Frame.MAX_FRAME || bytes.length < Frame.HEADER) {
            accepted.accept(false); return;
        }
        if (!enqueue(file ? 1 : 0, bytes, accepted)) accepted.accept(false);
    }
    private boolean enqueue(int channel, byte[] payload) { return enqueue(channel, payload, null); }
    private synchronized boolean enqueue(int channel, byte[] payload, Consumer<Boolean> result) {
        int size = payload.length + 1;
        if (closed.get() || size > Frame.MAX_FRAME + 1) return false;
        if (channel == 1 ? fileBytes + size > 128 * 1024 : controlBytes + size > 64 * 1024) return false;
        byte[] record = new byte[size]; record[0] = (byte) channel;
        System.arraycopy(payload, 0, record, 1, payload.length);
        if (channel == 1) { files.add(new Record(record, result)); fileBytes += size; }
        else { controls.add(new Record(record, result)); controlBytes += size; }
        notifyAll(); return true;
    }
    private void writeLoop() {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream()); out.writeInt(0x4d544302); out.flush();
            while (!closed.get()) {
                Record record;
                synchronized (this) {
                    while (!closed.get() && controls.isEmpty() && files.isEmpty()) wait();
                    if (closed.get()) break;
                    if (!controls.isEmpty()) { record = controls.remove(); controlBytes -= record.bytes.length; }
                    else { record = files.remove(); fileBytes -= record.bytes.length; }
                    current = record;
                }
                writing = System.nanoTime();
                out.writeInt(record.bytes.length); out.write(record.bytes); out.flush();
                record.finish(true); current = null;
                lastSent = System.nanoTime(); writing = 0;
            }
        } catch (Exception e) { failureCode = e.getMessage() != null && e.getMessage().matches("[A-Z_]+") ? e.getMessage() : e.getClass().getSimpleName(); close(); }
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
            long window = System.nanoTime(); int records = 0;
            while (!closed.get()) {
                long now = System.nanoTime();
                if (now - window >= TimeUnit.SECONDS.toNanos(1)) { window = now; records = 0; }
                if (++records > 512) throw new IOException("STREAM_RATE");
                byte[] record = readRecord(in);
                if (record[0] == 2) { remoteVerified = true; ready(); }
                else if (record[0] != 3) {
                    if (!announced.get()) throw new IOException("VERIFICATION_REQUIRED");
                    listener.onFrame(record[0] == 1, Arrays.copyOfRange(record, 1, record.length));
                }
            }
        } catch (Exception e) { failureCode = e.getMessage() != null && e.getMessage().matches("[A-Z_]+") ? e.getMessage() : e.getClass().getSimpleName(); close(); }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (deadline != null) deadline.cancel(false);
        try { socket.close(); } catch (IOException ignored) { }
        synchronized (this) {
            if (current != null) current.finish(false);
            for (Record record : controls) record.finish(false);
            for (Record record : files) record.finish(false);
            controls.clear(); files.clear(); controlBytes = fileBytes = 0; notifyAll();
        }
        listener.onClosed();
    }
}
