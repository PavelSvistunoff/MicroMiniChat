package io.github.pavelsvistunoff.microminichat;

import androidx.test.platform.app.InstrumentationRegistry;
import io.github.pavelsvistunoff.microminichat.pairing.*;
import io.github.pavelsvistunoff.microminichat.security.PairingTls;
import io.github.pavelsvistunoff.microminichat.signal.Signal;
import io.github.pavelsvistunoff.microminichat.net.IceConfig;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** TLS and native-session integration on loopback only; not internet/NAT evidence. */
public final class PhonePairingTest {
    private final android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static String payload(boolean offer,UUID session) {
        return new Signal(offer,session,"v=0\r\na=fingerprint:sha-256 "+"AB:".repeat(31)+"AB\r\n"
                +"m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n").encode();
    }
    private static class Events implements PhonePairing.Listener {
        final CountDownLatch offered=new CountDownLatch(1),answered=new CountDownLatch(1),failed=new CountDownLatch(1);
        final AtomicReference<String> offer=new AtomicReference<>(),answer=new AtomicReference<>();
        @Override public void onOffer(String value) { offer.set(value);offered.countDown(); }
        @Override public void onAnswer(String value) { answer.set(value);answered.countDown(); }
        @Override public void onFailure() { failed.countDown(); }
    }
    @Test public void certificateLifetimeMatchesRequestedHour() throws Exception {
        io.github.pavelsvistunoff.microminichat.net.WebRtcTransport.initialize(context);
        org.webrtc.RtcCertificatePem pem=org.webrtc.RtcCertificatePem.generateCertificate(org.webrtc.PeerConnection.KeyType.ECDSA,PairingTls.CERTIFICATE_LIFETIME_MS);
        long remaining=io.github.pavelsvistunoff.microminichat.security.IdentityStore.certificate(pem).getNotAfter().getTime()-System.currentTimeMillis();
        assertTrue("Remaining certificate lifetime in milliseconds: "+remaining,remaining>TimeUnit.MINUTES.toMillis(50) && remaining<=TimeUnit.MINUTES.toMillis(61));
    }
    @Test public void pinnedTlsExchangesOfferAndAnswerThenClosesListener() throws Exception {
        UUID id=UUID.randomUUID();Events hostEvents=new Events(),joinEvents=new Events();
        PhonePairing host=PhonePairing.host(PairingTls.create(context),"127.0.0.1",0,payload(true,id),hostEvents);
        PhonePairing join=null;
        try {
            join=PhonePairing.join(host.invitation,joinEvents);
            assertTrue(joinEvents.offered.await(10,TimeUnit.SECONDS));assertEquals(payload(true,id),joinEvents.offer.get());
            join.sendAnswer(payload(false,id));assertTrue(hostEvents.answered.await(10,TimeUnit.SECONDS));
            assertEquals(payload(false,id),hostEvents.answer.get());
            assertEquals(1,hostEvents.failed.getCount());
        } finally { host.close();if(join!=null)join.close(); }
        try(java.net.Socket socket=new java.net.Socket()) {
            assertThrows(java.io.IOException.class,()->socket.connect(new java.net.InetSocketAddress("127.0.0.1",host.invitation.port),1000));
        }
    }
    @Test public void wrongCertificatePinCannotReadOfferAndDoesNotConsumeInvitation() throws Exception { rejected(false); }
    @Test public void wrongTokenCannotReadOfferAndDoesNotConsumeInvitation() throws Exception { rejected(true); }
    private void rejected(boolean wrongToken) throws Exception {
        UUID id=UUID.randomUUID();Events hostEvents=new Events(),badEvents=new Events(),goodEvents=new Events();
        PhonePairing host=PhonePairing.host(PairingTls.create(context),"127.0.0.1",0,payload(true,id),hostEvents);
        byte[] token=host.invitation.token();if(wrongToken)token[0]^=1;
        String pin=wrongToken?host.invitation.fingerprint:(host.invitation.fingerprint.startsWith("00")?"ff":"00")+host.invitation.fingerprint.substring(2);
        PhonePairing bad=PhonePairing.join(new PhoneInvite("127.0.0.1",host.invitation.port,pin,token),badEvents),good=null;
        try {
            assertTrue(badEvents.failed.await(10,TimeUnit.SECONDS));assertEquals(1,badEvents.offered.getCount());
            good=PhonePairing.join(host.invitation,goodEvents);boolean offered=goodEvents.offered.await(10,TimeUnit.SECONDS);
            assertTrue("Host failed="+(hostEvents.failed.getCount()==0)+"; join failed="+(goodEvents.failed.getCount()==0)+"; code="+good.failureCode,offered);
            good.sendAnswer(payload(false,id));assertTrue(hostEvents.answered.await(10,TimeUnit.SECONDS));
        } finally { bad.close();if(good!=null)good.close();host.close(); }
    }
    @Test public void wrongSessionAnswerIsNeverApplied() throws Exception {
        UUID id=UUID.randomUUID();Events hostEvents=new Events(),joinEvents=new Events();
        PhonePairing host=PhonePairing.host(PairingTls.create(context),"127.0.0.1",0,payload(true,id),hostEvents);
        PhonePairing join=PhonePairing.join(host.invitation,joinEvents);
        try {
            assertTrue(joinEvents.offered.await(10,TimeUnit.SECONDS));join.sendAnswer(payload(false,UUID.randomUUID()));
            assertTrue(joinEvents.failed.await(10,TimeUnit.SECONDS));assertTrue(hostEvents.failed.await(10,TimeUnit.SECONDS));
            assertEquals(1,hostEvents.answered.getCount());
        } finally { host.close();join.close(); }
    }
    @Test public void incompleteAuthenticationTimesOutWithoutConsumingInvitation() throws Exception {
        UUID id=UUID.randomUUID();Events hostEvents=new Events(),goodEvents=new Events();
        PhonePairing host=PhonePairing.host(PairingTls.create(context),"127.0.0.1",0,payload(true,id),hostEvents);
        PhonePairing good=null;
        try(javax.net.ssl.SSLSocket socket=(javax.net.ssl.SSLSocket)PairingTls.pinned(host.invitation.fingerprint).getSocketFactory().createSocket("127.0.0.1",host.invitation.port)) {
            socket.setSoTimeout(10000);socket.startHandshake();
            java.io.DataOutputStream out=new java.io.DataOutputStream(socket.getOutputStream());
            out.writeInt(0x4d504831);out.writeByte(1);out.flush();
            // The server must close the incomplete authentication after the absolute five-second deadline.
            try { assertEquals(-1,socket.getInputStream().read()); }
            catch(java.net.SocketTimeoutException e) { fail("Incomplete authentication exceeded its deadline"); }
            catch(java.io.IOException expected) { }
            good=PhonePairing.join(host.invitation,goodEvents);boolean offered=goodEvents.offered.await(10,TimeUnit.SECONDS);
            assertTrue("Host failed="+(hostEvents.failed.getCount()==0)+"; join failed="+(goodEvents.failed.getCount()==0)+"; code="+good.failureCode,offered);
            good.sendAnswer(payload(false,id));assertTrue(hostEvents.answered.await(10,TimeUnit.SECONDS));
        } finally { if(good!=null)good.close();host.close(); }
    }
    @Test public void oversizedFrameIsRejectedBeforeAllocatingItsClaimedSize() throws Exception {
        Events hostEvents=new Events();
        PhonePairing host=PhonePairing.host(PairingTls.create(context),"127.0.0.1",0,payload(true,UUID.randomUUID()),hostEvents);
        try(javax.net.ssl.SSLSocket socket=(javax.net.ssl.SSLSocket)PairingTls.pinned(host.invitation.fingerprint).getSocketFactory().createSocket("127.0.0.1",host.invitation.port)) {
            socket.setSoTimeout(10000);socket.startHandshake();
            java.io.DataOutputStream out=new java.io.DataOutputStream(socket.getOutputStream());
            java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());
            out.writeInt(0x4d504831);out.write(host.invitation.token());out.flush();
            int length=in.readInt();assertTrue(length>0 && length<=Signal.MAX_CHARS);byte[] offer=new byte[length];in.readFully(offer);
            out.writeInt(Integer.MAX_VALUE);out.flush();
            assertTrue(hostEvents.failed.await(10,TimeUnit.SECONDS));assertEquals(1,hostEvents.answered.getCount());
        } finally { host.close(); }
    }
    @Test public void completeSessionsPairWithoutReplySharingAndAcknowledgeMessages() throws Exception {
        String suffix=Long.toHexString(System.nanoTime());
        ChatSession left=new ChatSession(context,"phoneleft"+suffix),right=new ChatSession(context,"phoneright"+suffix);
        int port;try(java.net.ServerSocket socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        try {
            left.offerOnPhone(IceConfig.direct(""),"127.0.0.1",port);
            SessionTest.waitFor(()->!left.phoneInvitation.isEmpty());
            right.joinPhone(PhoneInvite.decode(left.phoneInvitation),IceConfig.direct(""));
            SessionTest.waitFor(()->left.open && right.open);
            assertFalse(left.ready);assertFalse(right.ready);
            left.verifyPeer();right.verifyPeer();SessionTest.waitFor(()->left.ready && right.ready);
            assertTrue(left.phoneInvitation.isEmpty());left.sendText("phone-hosted pairing");
            SessionTest.waitFor(()->left.history.toString().contains("[Delivered]: phone-hosted pairing")
                    && right.history.toString().contains("Peer: phone-hosted pairing"));
            left.clearEverything();right.clearEverything();
        } finally { left.shutdown();right.shutdown(); }
    }
    @Test public void networkChangeStopsWaitingPhoneServer() throws Exception {
        ChatSession session=new ChatSession(context,"phonecancel"+Long.toHexString(System.nanoTime()));
        int port;try(java.net.ServerSocket socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        try {
            session.offerOnPhone(IceConfig.direct(""),"127.0.0.1",port);
            SessionTest.waitFor(()->!session.phoneInvitation.isEmpty());session.networkChanged();
            SessionTest.waitFor(()->!session.phonePairingActive && session.phoneInvitation.isEmpty());
            assertFalse(session.open);
        } finally { session.shutdown(); }
    }
}
