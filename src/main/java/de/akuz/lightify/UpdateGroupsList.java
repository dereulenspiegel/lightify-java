package de.akuz.lightify;

public class UpdateGroupsList extends Packet {

    public UpdateGroupsList() {
        super(Packet.COMMAND_GROUP_LIST, (byte) 0x02);

    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH;
    }

    @Override
    protected byte[] getPayload() {

        return new byte[] {};
    }

}
