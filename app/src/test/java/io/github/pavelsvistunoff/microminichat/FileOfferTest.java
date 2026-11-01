package io.github.pavelsvistunoff.microminichat;
import io.github.pavelsvistunoff.microminichat.files.FileOffer;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
public final class FileOfferTest {
    @Test public void metadataRoundtripAndSafeNames(){
        FileOffer offer=new FileOffer(UUID.randomUUID(),"../../folder\\file.bin","application/octet-stream",Long.valueOf(123));
        FileOffer decoded=FileOffer.decode(offer.id,offer.encode());assertEquals("file.bin",decoded.name);assertEquals(123,decoded.size);
        assertEquals("file.bin",FileOffer.safeName(".."));assertFalse(FileOffer.safeName("a\u0000b").contains("\u0000"));
    }
    @Test public void boundedMetadataAndOffsetsRejectOverflow(){
        assertThrows(IllegalArgumentException.class,()->new FileOffer(UUID.randomUUID(),"x","x/y",-1));
        assertThrows(IllegalArgumentException.class,()->FileOffer.decode(UUID.randomUUID(),new byte[2000]));
        assertTrue(FileOffer.validChunk(0,16384,0,16384));
        assertFalse(FileOffer.validChunk(1,1,0,10));assertFalse(FileOffer.validChunk(0,16385,0,20000));
        assertFalse(FileOffer.validChunk(Long.MAX_VALUE,1,Long.MAX_VALUE,Long.MAX_VALUE));
        assertFalse(FileOffer.validChunk(0,1,0,0));
    }
}
