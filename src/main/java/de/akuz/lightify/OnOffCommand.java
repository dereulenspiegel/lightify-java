package de.akuz.lightify;

import java.nio.ByteBuffer;

public class OnOffCommand extends Packet {

    private boolean switchOn;
    private Luminary lum;

    public OnOffCommand(Luminary lum, boolean switchOn) {
        super(Packet.COMMAND_ONOFF, (byte) (lum.isGroup() ? 0x02 : 0x00));
        this.lum = lum;
        this.switchOn = switchOn;
    }

    @Override
    protected short getLength() {
        return DEFAULT_HEADER_LENGTH + 9;
    }

    @Override
    protected byte[] getPayload() {
        ByteBuffer buf = ByteBuffer.allocate(9);
        byte[] addr;
        if (lum instanceof Group) {
            addr = new byte[] { ((Group) lum).getAddressBytes()[0], 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        } else {
            addr = ((Light) lum).getAddress().getBytes();
        }
        buf.put(addr);
        buf.put((byte) (switchOn ? 0x01 : 0x00));

        return buf.array();
    }

}
