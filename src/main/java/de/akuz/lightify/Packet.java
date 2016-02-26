package de.akuz.lightify;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class Packet {

    public static final byte COMMAND_LUMINANCE = 0x31;
    public static final byte COMMAND_ONOFF = 0x32;
    public static final byte COMMAND_TEMP = 0x33;
    public static final byte COMMAND_COLOUR = 0x36;

    public static final byte COMMAND_GROUP_LIST = 0x1e;
    public static final byte COMMAND_GROUP_INFO = 0x26;
    public static final byte COMMAND_LIGHT_STATUS = 0x68;
    public static final byte COMMAND_ALL_LIGHT_STATUS = 0x13;

    protected static final short DEFAULT_HEADER_LENGTH = 6;

    protected byte command;
    protected byte flag;

    private final static byte[] staticParts = new byte[] { 0x00, 0x00, 0x07 };

    protected Packet(byte command, byte flag) {
        this.command = command;
        this.flag = flag;
    }

    public byte[] serialize(byte sequence) {
        ByteBuffer buf = ByteBuffer.allocate(getLength() + 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(buildHeader(sequence));
        buf.put(getPayload());
        return buf.array();
    }

    protected abstract short getLength();

    protected abstract byte[] getPayload();

    protected byte[] buildHeader(byte sequence) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(getLength());
        buf.put(flag);
        buf.put(command);
        buf.put(staticParts);
        buf.put(sequence);

        return buf.array();
    }

}
