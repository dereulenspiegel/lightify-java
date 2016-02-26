package de.akuz.lightify;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UpdateGroupInfo extends Packet {

    private int groupId;

    public UpdateGroupInfo(int groupId) {
        super(Packet.COMMAND_GROUP_INFO, (byte) 0x02);
        this.groupId = groupId;
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 8;
    }

    @Override
    protected byte[] getPayload() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) groupId);
        buf.put(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        return buf.array();
    }

}
