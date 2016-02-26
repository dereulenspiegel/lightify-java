package de.akuz.lightify;

public class UpdateAllLightStatus extends Packet {

    public UpdateAllLightStatus() {
        super(Packet.COMMAND_ALL_LIGHT_STATUS, (byte) 0x02);
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 1;
    }

    @Override
    protected byte[] getPayload() {
        return new byte[] { 0x01 };
    }

}
