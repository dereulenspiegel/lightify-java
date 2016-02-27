package de.akuz.lightify;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SetTemperature extends Packet {

    private Luminary lum;
    private short temperature;
    private short time;

    public SetTemperature(Luminary lum, short temp, short time) {
        super(Packet.COMMAND_TEMP, (byte) (lum.isGroup() ? 0x02 : 0x00));
        this.lum = lum;
        this.temperature = temp;
        this.time = time;
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 8 + 4;
    }

    @Override
    protected byte[] getPayload() {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(lum.getAddressBytes());
        buf.putShort(temperature);
        buf.putShort(time);
        return buf.array();
    }

}
