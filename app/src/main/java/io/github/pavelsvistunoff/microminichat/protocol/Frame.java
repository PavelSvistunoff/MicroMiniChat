package io.github.pavelsvistunoff.microminichat.protocol;

import java.nio.*;
import java.nio.charset.*;
import java.util.UUID;

/** Application records gain integrity/authentication from the verified DTLS channel. */
public final class Frame {
    public static final int HELLO=1, TEXT=2, ACK=3, FILE_OFFER=4, FILE_ACCEPT=5, FILE_CHUNK=6,
            FILE_CANCEL=7, FILE_END=8, FILE_COMPLETE=9, ERROR=10, FILE_CHUNK_ACK=11;
    public static final int HEADER=50, MAX_FRAME=65536, MAX_TEXT=16384, CHUNK=16384;
    public final int type;
    public final UUID session, id;
    public final long sequence;
    public final byte[] payload;
    public Frame(int type, UUID session, UUID id, long sequence, byte[] payload) {
        if (type<1 || type>11 || type==ERROR || session==null || id==null || sequence<0 || payload==null || payload.length>MAX_FRAME-HEADER)
            throw new IllegalArgumentException("FRAME_BOUNDS");
        this.type=type; this.session=session; this.id=id; this.sequence=sequence; this.payload=payload;
    }
    public byte[] encode() {
        ByteBuffer out=ByteBuffer.allocate(HEADER+payload.length);
        out.putInt(0x4d4d4346).put((byte)1).put((byte)type);
        put(out,session); put(out,id); out.putLong(sequence).putInt(payload.length).put(payload); return out.array();
    }
    public static Frame decode(byte[] bytes) {
        if (bytes==null || bytes.length<HEADER || bytes.length>MAX_FRAME) throw new IllegalArgumentException("FRAME_BOUNDS");
        ByteBuffer in=ByteBuffer.wrap(bytes);
        if (in.getInt()!=0x4d4d4346 || in.get()!=1) throw new IllegalArgumentException("FRAME_VERSION");
        int type=in.get()&255; UUID session=get(in), id=get(in); long sequence=in.getLong(); int size=in.getInt();
        if (size<0 || size!=in.remaining()) throw new IllegalArgumentException("FRAME_LENGTH");
        byte[] payload=new byte[size]; in.get(payload); return new Frame(type,session,id,sequence,payload);
    }
    private static void put(ByteBuffer buffer, UUID id) { buffer.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()); }
    private static UUID get(ByteBuffer buffer) { return new UUID(buffer.getLong(),buffer.getLong()); }
    public static byte[] textBytes(String text) {
        if (text==null || text.isEmpty() || text.length()>MAX_TEXT) throw new IllegalArgumentException("TEXT_SIZE");
        try {
            ByteBuffer bytes=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            if (bytes.remaining()>MAX_TEXT) throw new IllegalArgumentException("TEXT_SIZE");
            byte[] body=new byte[bytes.remaining()]; bytes.get(body); return body;
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("TEXT_ENCODING"); }
    }
    public static String text(byte[] bytes) {
        if (bytes.length<1 || bytes.length>MAX_TEXT) throw new IllegalArgumentException("TEXT_SIZE");
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IllegalArgumentException("TEXT_ENCODING"); }
    }
}
