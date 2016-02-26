package de.akuz.lightify;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SetLuminance extends Packet {

    private byte lum;
    private short time;
    private Luminary luminary;

    public SetLuminance(Luminary luminary, byte lum, short time) {
        super(Packet.COMMAND_LUMINANCE, (byte) (luminary.isGroup() ? 0x02 : 0x00));
        this.luminary = luminary;
        this.lum = lum;
        this.time = time;
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 11;
    }

    @Override
    protected byte[] getPayload() {
        byte[] addr;
        if (luminary.isGroup()) {
            addr = new byte[] { (byte) ((Group) luminary).getId(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        } else {
            addr = ((Light) luminary).getAddress().getBytes();
        }
        ByteBuffer buf = ByteBuffer.allocate(11);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(addr);
        buf.put(lum);
        buf.putShort(time);
        return buf.array();
    }

}
