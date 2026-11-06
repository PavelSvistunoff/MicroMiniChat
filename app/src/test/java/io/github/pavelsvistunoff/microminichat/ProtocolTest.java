package io.github.pavelsvistunoff.microminichat;

import io.github.pavelsvistunoff.microminichat.protocol.*;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProtocolTest {
    static final class Store implements ReliableProtocol.Store {
        final List<ReliableProtocol.Message> out=new ArrayList<>(); final Map<UUID,String> received=new HashMap<>(); boolean failCommit;
        @Override public ReliableProtocol.Message enqueue(String text) { ReliableProtocol.Message m=new ReliableProtocol.Message(UUID.randomUUID(),out.size()+1,text,ReliableProtocol.PENDING); out.add(m); return m; }
        @Override public ReliableProtocol.Message firstUnconfirmed() { return out.stream().filter(m -> !m.state.equals(ReliableProtocol.DELIVERED)).findFirst().orElse(null); }
        @Override public void state(UUID id,String state) { for(int i=0;i<out.size();i++) { ReliableProtocol.Message m=out.get(i); if(m.id.equals(id))out.set(i,new ReliableProtocol.Message(id,m.sequence,m.text,state)); } }
        @Override public void receive(UUID id,long seq,String text) throws IOException {
            if(failCommit)throw new IOException();
            if(received.containsKey(id)) { if(!received.get(id).equals(text))throw new IOException(); return; }
            if(seq!=received.size()+1)throw new IOException(); received.put(id,text);
        }
        @Override public void retry() { for(ReliableProtocol.Message m:new ArrayList<>(out))if(!m.state.equals(ReliableProtocol.DELIVERED))state(m.id,ReliableProtocol.PENDING); }
    }
    static final class Pair {
        final UUID session=UUID.randomUUID(); final Store a=new Store(),b=new Store();
        final Deque<byte[]> ab=new ArrayDeque<>(),ba=new ArrayDeque<>();
        final ReliableProtocol left=new ReliableProtocol(session,a,(x,c)->{ab.add(x);c.accept(true);},()->{},f->{});
        final ReliableProtocol right=new ReliableProtocol(session,b,(x,c)->{ba.add(x);c.accept(true);},()->{},f->{});
        Pair() throws Exception { left.start(0);right.start(0);drain(); }
        void drain() throws Exception { int n=0;while(!ab.isEmpty()||!ba.isEmpty()){if(++n>100)throw new AssertionError();if(!ab.isEmpty())right.receive(ab.remove(),1);if(!ba.isEmpty())left.receive(ba.remove(),1);} }
    }
    @Test public void acknowledgementRequiresCommitAndRetryKeepsId() throws Exception {
        Pair p=new Pair();p.left.send("hello",2);byte[] original=p.ab.peek();
        assertEquals(ReliableProtocol.SENT,p.a.out.get(0).state);
        p.right.receive(p.ab.remove(),3);p.ba.clear(); // Lost ACK, committed remotely.
        p.left.tick(16000);assertEquals(ReliableProtocol.FAILED,p.a.out.get(0).state);
        p.left.retry(16001);assertArrayEquals(original,p.ab.peek());p.drain();
        assertEquals(1,p.b.received.size());assertEquals(ReliableProtocol.DELIVERED,p.a.out.get(0).state);
    }
    @Test public void failedCommitProducesNoAcknowledgement() throws Exception {
        Pair p=new Pair();p.b.failCommit=true;p.left.send("hello",2);
        assertThrows(IOException.class,()->p.right.receive(p.ab.remove(),3));assertTrue(p.ba.isEmpty());
        assertEquals(ReliableProtocol.SENT,p.a.out.get(0).state);
    }
    @Test public void malformedFramesAndCrossSessionReplayReject() throws Exception {
        Pair p=new Pair();byte[] other=new Frame(Frame.TEXT,UUID.randomUUID(),UUID.randomUUID(),1,new byte[]{65}).encode();
        assertThrows(IOException.class,()->p.right.receive(other,1));
        byte[] bad=new Frame(Frame.TEXT,p.session,UUID.randomUUID(),1,new byte[]{(byte)0xFF}).encode();
        assertThrows(IllegalArgumentException.class,()->p.right.receive(bad,1));
        byte[] valid=new Frame(Frame.ACK,p.session,UUID.randomUUID(),1,new byte[0]).encode(); valid[4]=2;
        assertThrows(IllegalArgumentException.class,()->Frame.decode(valid));
        assertThrows(IllegalArgumentException.class,()->Frame.textBytes("é".repeat(Frame.MAX_TEXT)));
        Random random=new Random(2);for(int i=0;i<1000;i++){byte[] bytes=new byte[random.nextInt(512)];random.nextBytes(bytes);assertThrows(IllegalArgumentException.class,()->Frame.decode(bytes));}
    }
    @Test public void failedFirstMessageBlocksLaterSequencesUntilRetry() throws Exception {
        Pair p=new Pair();p.left.send("first",0);p.left.tick(16000);p.left.send("second",16001);
        assertEquals(1,p.ab.size()); // only original first frame, never sequence 2 before 1 is acknowledged.
    }
    @Test public void wrongAcknowledgementsNeverMarkDeliveredAndSilentNegotiationExpires() throws Exception {
        Pair p=new Pair();p.left.send("receipt boundary",1);
        ReliableProtocol.Message message=p.a.out.get(0);
        p.left.receive(new Frame(Frame.ACK,p.session,UUID.randomUUID(),message.sequence,new byte[0]).encode(),2);
        p.left.receive(new Frame(Frame.ACK,p.session,message.id,message.sequence+1,new byte[0]).encode(),2);
        assertEquals(ReliableProtocol.SENT,p.a.out.get(0).state);
        assertThrows(IOException.class,()->p.left.receive(new Frame(Frame.ACK,p.session,message.id,message.sequence,new byte[]{1}).encode(),2));
        p.drain();assertEquals(ReliableProtocol.DELIVERED,p.a.out.get(0).state);
        ReliableProtocol silent=new ReliableProtocol(UUID.randomUUID(),new Store(),(bytes,result)->result.accept(true),()->{},frame->{});
        silent.start(0);assertThrows(IOException.class,()->silent.tick(60000));
    }
}
