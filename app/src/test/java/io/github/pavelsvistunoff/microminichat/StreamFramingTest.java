package io.github.pavelsvistunoff.microminichat;
import java.io.*;
import io.github.pavelsvistunoff.microminichat.net.FramedLink;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamFramingTest {
    @Test public void rejectsLengthsBeforeAllocatingAndRejectsChannels() throws Exception {
        for(int size:new int[]{-1,0,65538,Integer.MAX_VALUE}) {
            try { FramedLink.readRecord(new DataInputStream(new ByteArrayInputStream(java.nio.ByteBuffer.allocate(4).putInt(size).array())));fail(); }
            catch(IOException expected) { }
        }
        for(byte channel:new byte[]{-1,4,0,1}) {
            try { FramedLink.readRecord(new DataInputStream(new ByteArrayInputStream(new byte[]{0,0,0,1,channel})));fail(); }
            catch(IOException expected) { }
        }
        assertArrayEquals(new byte[]{2},FramedLink.readRecord(new DataInputStream(new ByteArrayInputStream(new byte[]{0,0,0,1,2}))));
    }
}
