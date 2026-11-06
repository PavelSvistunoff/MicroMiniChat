package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.net.FramedLink;
import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamLifecycleTest {
    @Test public void productDataBeforeBilateralVerificationClosesStream() throws Exception {
        CountDownLatch closed=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger(),frames=new AtomicInteger();
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket remote=new Socket("127.0.0.1",listener.getLocalPort());Socket local=listener.accept()) {
            FramedLink link=new FramedLink(local,new FramedLink.Listener(){public void onReady(){fail();}public void onFrame(boolean file,byte[] bytes){frames.incrementAndGet();}public void onClosed(){callbacks.incrementAndGet();closed.countDown();}});
            try{
                link.start();DataInputStream in=new DataInputStream(remote.getInputStream());assertEquals(0x4d544302,in.readInt());
                DataOutputStream out=new DataOutputStream(remote.getOutputStream());byte[] frame=new Frame(Frame.TEXT,UUID.randomUUID(),UUID.randomUUID(),1,Frame.textBytes("unauthorized synthetic frame")).encode();
                out.writeInt(0x4d544302);out.writeInt(frame.length+1);out.writeByte(0);out.write(frame);out.flush();
                assertTrue(closed.await(5,TimeUnit.SECONDS));assertEquals(0,frames.get());link.close();assertEquals(1,callbacks.get());
            }finally{link.close();}
        }
    }
    @Test public void slowReceiverAppliesBackpressureAndDisconnectUnblocksWriter() throws Exception {
        CountDownLatch ready=new CountDownLatch(1),closed=new CountDownLatch(1);AtomicInteger rejected=new AtomicInteger();
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket remote=new Socket("127.0.0.1",listener.getLocalPort());Socket local=listener.accept()) {
            local.setSendBufferSize(1024);remote.setReceiveBufferSize(1024);
            FramedLink link=new FramedLink(local,new FramedLink.Listener(){public void onReady(){ready.countDown();}public void onFrame(boolean file,byte[] bytes){}public void onClosed(){closed.countDown();}});
            try{
                link.start();DataInputStream in=new DataInputStream(remote.getInputStream());assertEquals(0x4d544302,in.readInt());
                DataOutputStream out=new DataOutputStream(remote.getOutputStream());out.writeInt(0x4d544302);out.writeInt(1);out.writeByte(2);out.flush();link.verify();assertTrue(ready.await(5,TimeUnit.SECONDS));
                byte[] frame=new Frame(Frame.TEXT,UUID.randomUUID(),UUID.randomUUID(),1,new byte[16384]).encode();
                for(int i=0;i<1000;i++)link.send(false,frame,written->{if(!written)rejected.incrementAndGet();});
                assertTrue("Output queue must reject excess data",rejected.get()>0);
                remote.close();assertTrue(closed.await(5,TimeUnit.SECONDS));
            }finally{link.close();}
        }
    }
}
