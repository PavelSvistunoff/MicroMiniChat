package io.github.pavelsvistunoff.microminichat.net;
import io.github.pavelsvistunoff.microminichat.tor.OnionAddress;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
/** Strict SOCKS5 domain request to loopback only; no DNS, system proxy or direct fallback. */
public final class OnionSocket {
    private OnionSocket() { }
    public static void connect(Socket socket,int port,String onion) throws IOException {
        OnionAddress.require(onion);
        if (port < 1 || port > 65535) throw new IOException("SOCKS_PORT");
        socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(120000);
        DataOutputStream out=new DataOutputStream(socket.getOutputStream());
        DataInputStream in=new DataInputStream(socket.getInputStream());
        out.write(new byte[]{5,1,0});out.flush();
        if(in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0)throw new IOException("SOCKS_AUTH");
        byte[] name=onion.getBytes(StandardCharsets.US_ASCII);
        out.write(new byte[]{5,1,0,3});out.writeByte(name.length);out.write(name);out.writeShort(443);out.flush();
        if(in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0 || in.readUnsignedByte()!=0)throw new IOException("SOCKS_CONNECT");
        int type=in.readUnsignedByte(),length;
        if(type==1)length=4;else if(type==4)length=16;else if(type==3){length=in.readUnsignedByte();if(length==0)throw new IOException("SOCKS_REPLY");}
        else throw new IOException("SOCKS_REPLY");
        byte[] bound=new byte[length+2];in.readFully(bound);
    }
}
