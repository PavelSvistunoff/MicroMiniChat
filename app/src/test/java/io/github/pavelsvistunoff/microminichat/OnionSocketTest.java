package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.net.OnionSocket;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class OnionSocketTest {
    @Test public void sendsCanonicalDomainOnlyAndRejectsProxyFailure() throws Exception {
        String onion="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaam2dqd.onion";
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            Future<String> request=worker.submit(()->{
                try(Socket accepted=server.accept()) {
                    DataInputStream in=new DataInputStream(accepted.getInputStream());DataOutputStream out=new DataOutputStream(accepted.getOutputStream());
                    assertEquals(5,in.readUnsignedByte());assertEquals(1,in.readUnsignedByte());assertEquals(0,in.readUnsignedByte());out.write(new byte[]{5,0});out.flush();
                    assertEquals(5,in.readUnsignedByte());assertEquals(1,in.readUnsignedByte());assertEquals(0,in.readUnsignedByte());assertEquals(3,in.readUnsignedByte());
                    int length=in.readUnsignedByte();assertEquals(62,length);byte[] name=new byte[length];in.readFully(name);assertEquals(443,in.readUnsignedShort());
                    out.write(new byte[]{5,4,0,1,0,0,0,0,0,0});out.flush();return new String(name,java.nio.charset.StandardCharsets.US_ASCII);
                }
            });
            try(Socket socket=new Socket()){assertThrows(IOException.class,()->OnionSocket.connect(socket,server.getLocalPort(),onion));}
            assertEquals(onion,request.get(5,TimeUnit.SECONDS));
        }finally{worker.shutdownNow();}
        try(Socket socket=new Socket()){assertThrows(IllegalArgumentException.class,()->OnionSocket.connect(socket,9050,"example.com"));assertFalse(socket.isConnected());}
    }
}
