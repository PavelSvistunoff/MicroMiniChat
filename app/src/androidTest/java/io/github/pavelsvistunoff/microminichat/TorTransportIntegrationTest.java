package io.github.pavelsvistunoff.microminichat;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.net.*;
import io.github.pavelsvistunoff.microminichat.tor.*;
import io.github.pavelsvistunoff.microminichat.pairing.*;
import io.github.pavelsvistunoff.microminichat.security.*;
import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorTransportIntegrationTest {
    @Test public void mutualTlsAndTrustGateAcrossRealOnionStreams() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        TorRuntime[] tor=new TorRuntime[2];TorConnector[] connector=new TorConnector[2];FramedLink[] links=new FramedLink[2];
        String[] addresses=new String[2];TlsIdentity[] identity=new TlsIdentity[2];UUID[] sessions=new UUID[2];
        CountDownLatch published=new CountDownLatch(2),connected=new CountDownLatch(2),ready=new CountDownLatch(2),delivered=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failed=new java.util.concurrent.atomic.AtomicBoolean();
        String tag=Long.toHexString(System.nanoTime());
        PairingCapabilities[] caps=new PairingCapabilities[2];
        try {
            for(int side=0;side<2;side++) {
                final int index=side;String storage="transport-"+side+tag;
                identity[side]=new TlsIdentity(context,storage);identity[side].load();
                caps[side]=new PairingCapabilities(context,storage);
                connector[side]=new TorConnector(identity[side],new TrustStore(context,storage),caps[side],new TorConnector.Listener(){
                    @Override public void failed(){failed.set(true);connected.countDown();connected.countDown();}
                    @Override public void connected(javax.net.ssl.SSLSocket socket,UUID session,String pin,String onion,boolean outgoing,long epoch){
                        sessions[index]=session;
                        links[index]=new FramedLink(socket,new FramedLink.Listener(){
                            @Override public void onReady(){ready.countDown();}
                            @Override public void onClosed(){}
                            @Override public void onFrame(boolean file,byte[] bytes){
                                Frame frame=Frame.decode(bytes);
                                if(index==0 && frame.type==Frame.TEXT && Frame.text(frame.payload).equals("onion TLS test"))delivered.countDown();
                            }
                        });connected.countDown();
                    }
                });
                tor[side]=new TorRuntime(context,new TorRuntime.Listener(){
                    @Override public void state(int progress,boolean complete,boolean error){if(error){failed.set(true);published.countDown();}}
                    @Override public void onionReady(String address){addresses[index]=address;connector[index].localOnion=address;published.countDown();}
                },connector[side].port(),storage);tor[side].start();
            }
            assertTrue("Actual onion publication",published.await(310,TimeUnit.SECONDS));assertFalse(failed.get());
            TorInvite invite=TorInvite.create(addresses[0],identity[0].fingerprint(),System.currentTimeMillis());caps[0].activate(invite);
            connector[1].dial(tor[1].socksPort(),invite.onion,invite.fingerprint,invite);
            assertTrue("Actual Tor/TLS connection",connected.await(180,TimeUnit.SECONDS));assertFalse(failed.get());
            assertEquals(sessions[0],sessions[1]);links[0].start();links[1].start();
            java.util.concurrent.atomic.AtomicBoolean premature=new java.util.concurrent.atomic.AtomicBoolean(true);
            Frame text=new Frame(Frame.TEXT,sessions[1],UUID.randomUUID(),1,Frame.textBytes("onion TLS test"));
            links[1].send(false,text.encode(),premature::set);assertFalse(premature.get());
            links[0].verify();assertFalse(ready.await(100,TimeUnit.MILLISECONDS));links[1].verify();
            assertTrue(ready.await(20,TimeUnit.SECONDS));links[1].send(false,text.encode(),accepted->assertTrue(accepted));
            assertTrue(delivered.await(30,TimeUnit.SECONDS));
        } finally {
            for(FramedLink link:links)if(link!=null)link.close();
            for(TorConnector value:connector)if(value!=null)value.close();
            for(TorRuntime value:tor)if(value!=null)value.close();
            for(TlsIdentity value:identity)if(value!=null)value.resetAfterConfirmation();
        }
    }
}
