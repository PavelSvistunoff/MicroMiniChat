package io.github.pavelsvistunoff.microminichat.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;

/** Single-threaded coordinator; the repository must commit receipt before returning. */
public final class ReliableProtocol {
    public static final String PENDING="Pending", SENDING="Sending", SENT="Sent", DELIVERED="Delivered", FAILED="Failed";
    public static final class Message {
        public final UUID id; public final long sequence; public final String text, state;
        public Message(UUID id,long sequence,String text,String state) { this.id=id; this.sequence=sequence; this.text=text; this.state=state; }
    }
    public interface Store {
        Message enqueue(String text) throws IOException;
        Message firstUnconfirmed() throws IOException;
        void state(UUID id,String state) throws IOException;
        void receive(UUID id,long sequence,String text) throws IOException;
        void retry() throws IOException;
    }
    public interface Link { void send(byte[] frame,Consumer<Boolean> accepted); }
    private final UUID session;
    private final Store store;
    private final Link link;
    private final Runnable changed;
    private final Consumer<Frame> files;
    private Message inFlight;
    private boolean peerHello, responded;
    private long sentAt, lastHello;
    private final long acknowledgementTimeout;
    public boolean needsTick() { return !peerHello || inFlight!=null; }
    public boolean ready() { return peerHello; }
    public ReliableProtocol(UUID session,Store store,Link link,Runnable changed,Consumer<Frame> files) {
        this(session, store, link, changed, files, 15000);
    }
    public ReliableProtocol(UUID session,Store store,Link link,Runnable changed,Consumer<Frame> files,long acknowledgementTimeout) {
        if (acknowledgementTimeout < 15000 || acknowledgementTimeout > 120000) throw new IllegalArgumentException();
        this.acknowledgementTimeout = acknowledgementTimeout;
        this.session=session; this.store=store; this.link=link; this.changed=changed; this.files=files;
    }
    public void start(long now) throws IOException { store.retry(); hello(now); }
    private void hello(long now) {
        lastHello=now;
        link.send(new Frame(Frame.HELLO,session,new UUID(0,0),0,ByteBuffer.allocate(4).putInt(1<<1).array()).encode(),accepted -> {});
    }
    public void receive(byte[] bytes,long now) throws IOException {
        Frame frame=Frame.decode(bytes);
        if (!session.equals(frame.session)) throw new IOException("STALE_SESSION");
        if (frame.type==Frame.HELLO) {
            if (frame.payload.length!=4 || (ByteBuffer.wrap(frame.payload).getInt() & (1<<1))==0 || frame.sequence!=0)
                throw new IOException("VERSION_NEGOTIATION");
            peerHello=true;
            if (!responded) { responded=true; hello(now); }
            changed.run(); pump(now); return;
        }
        if (!peerHello) throw new IOException("HELLO_REQUIRED");
        if (frame.type==Frame.TEXT) {
            if (frame.sequence<1) throw new IOException("SEQUENCE");
            String text=Frame.text(frame.payload);
            store.receive(frame.id,frame.sequence,text); // Durable transaction or exception; never ACK a failed commit.
            link.send(new Frame(Frame.ACK,session,frame.id,frame.sequence,new byte[0]).encode(),accepted -> {});
            changed.run();
        } else if (frame.type==Frame.ACK) {
            if (frame.payload.length!=0) throw new IOException("ACK_BODY");
            if (inFlight!=null && inFlight.id.equals(frame.id) && inFlight.sequence==frame.sequence) {
                store.state(inFlight.id,DELIVERED); inFlight=null; changed.run(); pump(now);
            }
        } else files.accept(frame);
    }
    public void send(String text,long now) throws IOException {
        if (!peerHello) throw new IOException("NOT_READY");
        Frame.textBytes(text); store.enqueue(text); changed.run(); pump(now);
    }
    private void pump(long now) throws IOException {
        if (!peerHello || inFlight!=null) return;
        Message message=store.firstUnconfirmed();
        if (message==null || FAILED.equals(message.state)) return;
        inFlight=message; sentAt=now; store.state(message.id,SENDING); changed.run();
        link.send(new Frame(Frame.TEXT,session,message.id,message.sequence,Frame.textBytes(message.text)).encode(),accepted -> {
            if (inFlight!=null && inFlight.id.equals(message.id)) {
                try { store.state(message.id,accepted ? SENT : FAILED); if (!accepted) inFlight=null; changed.run(); }
                catch (IOException e) { throw new IllegalStateException("STATE_PERSISTENCE_FAILED"); }
            }
        });
    }
    public void tick(long now) throws IOException {
        if (!peerHello && now-lastHello>=1000) hello(now);
        if (inFlight!=null && now-sentAt>=acknowledgementTimeout) { store.state(inFlight.id,FAILED); inFlight=null; changed.run(); }
        pump(now);
    }
    public void retry(long now) throws IOException { if(inFlight==null) { store.retry(); pump(now); } }
}
