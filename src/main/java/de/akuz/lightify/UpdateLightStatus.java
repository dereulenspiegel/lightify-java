package de.akuz.lightify;

import de.akuz.lightify.Light.Address;

public class UpdateLightStatus extends Packet {

    private Address lightAddress;

    public UpdateLightStatus(Address address) {
        super(Packet.COMMAND_LIGHT_STATUS, (byte) 0x00);
        this.lightAddress = address;
    }

    @Override
    protected short getLength() {
        return (short) (DEFAULT_HEADER_LENGTH + lightAddress.getBytes().length);
    }

    @Override
    protected byte[] getPayload() {
        return lightAddress.getBytes();
    }
}
