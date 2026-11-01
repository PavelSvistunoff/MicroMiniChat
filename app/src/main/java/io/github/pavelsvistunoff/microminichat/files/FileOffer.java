package io.github.pavelsvistunoff.microminichat.files;

import io.github.pavelsvistunoff.microminichat.protocol.Frame;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class FileOffer {
    public static final long MAX_SIZE=16L*1024*1024*1024;
    public final UUID id; public final String name,mime; public final long size;
    public FileOffer(UUID id,String name,String mime,long size) {
        if(id==null || size<0 || size>MAX_SIZE)throw new IllegalArgumentException("FILE_SIZE");
        this.id=id;this.name=safeName(name);this.size=size;
        this.mime=mime!=null && mime.length()<=128 && mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")?mime:"application/octet-stream";
    }
    public static String safeName(String name) {
        if(name==null)return "file.bin";
        name=name.replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1);
        StringBuilder safe=new StringBuilder();
        for(int i=0,count=0;i<name.length() && count<120;count++) {
            int c=name.codePointAt(i);i+=Character.charCount(c);
            if(Character.isISOControl(c) || Character.getType(c)==Character.FORMAT || (c>=0xD800 && c<=0xDFFF))safe.append('_');
            else safe.appendCodePoint(c);
        }
        String result=safe.toString().trim();
        return result.isEmpty() || result.equals(".") || result.equals("..")?"file.bin":result;
    }
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeLong(size);write(out,name);write(out,mime);return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static void write(DataOutputStream out,String value)throws IOException {
        byte[] bytes=Frame.textBytes(value);if(bytes.length>512)throw new IOException();out.writeShort(bytes.length);out.write(bytes);
    }
    private static String read(DataInputStream in)throws IOException {
        int size=in.readUnsignedShort();if(size<1 || size>512 || size>in.available())throw new IOException();
        byte[] bytes=new byte[size];in.readFully(bytes);return Frame.text(bytes);
    }
    public static FileOffer decode(UUID id,byte[] data) {
        if(data==null || data.length>1100)throw new IllegalArgumentException("FILE_METADATA");
        try {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));long size=in.readLong();
            String name=read(in),mime=read(in);if(in.available()!=0)throw new IOException();return new FileOffer(id,name,mime,size);
        }catch(IOException e){throw new IllegalArgumentException("FILE_METADATA");}
    }
    public static boolean validChunk(long offset,int length,long progress,long size) {
        return offset>=0 && offset==progress && length>0 && length<=Frame.CHUNK && progress<=size && length<=size-progress;
    }
}
