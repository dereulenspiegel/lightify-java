package de.akuz.lightify;

import org.junit.Assert;
import org.junit.Test;

public class PacketTest {

    @Test
    public void testOnOffCommand() throws Exception {
        Light testLight = new Light(null, new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 });
        Packet onOff = new OnOffCommand(testLight, true);
        byte[] expectedBytes = new byte[] { 15, 0, 0, 50, 0, 0, 7, 1, 1, 2, 3, 4, 5, 6, 7, 8, 1 };
        byte[] serializedCommand = onOff.serialize((byte) 0x01);
        Assert.assertArrayEquals(expectedBytes, serializedCommand);
    }

    @Test
    public void testLuminanceCommand() throws Exception {
        Light testLight = new Light(null, new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 });
        Packet luminanceCommand = new SetLuminance(testLight, (byte) 95, (short) 42);
        byte[] expectedBytes = new byte[] { 17, 0, 0, 49, 0, 0, 7, 1, 1, 2, 3, 4, 5, 6, 7, 8, 95, 42, 0 };
        byte[] serializedCommand = luminanceCommand.serialize((byte) 0x01);
        Assert.assertArrayEquals(expectedBytes, serializedCommand);
    }

}
