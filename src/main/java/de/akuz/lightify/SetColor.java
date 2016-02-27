package de.akuz.lightify;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SetColor extends Packet {

    private Luminary lum;

    private byte red;
    private byte green;
    private byte blue;

    private short time;

    public SetColor(Luminary lum, byte red, byte green, byte blue, short time) {
        super(Packet.COMMAND_COLOUR, (byte) (lum.isGroup() ? 0x02 : 0x00));
        this.lum = lum;

        this.red = red;
        this.green = green;
        this.blue = blue;
        this.time = time;
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 8 + 6;
    }

    @Override
    protected byte[] getPayload() {
        ByteBuffer buf = ByteBuffer.allocate(14);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(lum.getAddressBytes());
        buf.put(red);
        buf.put(green);
        buf.put(blue);
        buf.put((byte) 0xFF);
        buf.putShort(time);
        return buf.array();
    }

}
