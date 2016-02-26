package de.akuz.lightify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

public class Light extends Luminary {

    public static class Address {
        private byte[] address;

        public Address(byte[] address) {
            this.address = address;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj.getClass().isAssignableFrom(this.getClass())) {
                Address other = (Address) obj;
                return Arrays.equals(address, other.address);
            }
            return super.equals(obj);
        }

        public byte[] getBytes() {
            return address;
        }
    }

    private Address address;

    public Light(Gateway conn, byte[] address) {
        super(conn);
        this.address = new Address(address);
    }

    void updateStatusData(ByteBuffer buf) {
        if (buf.limit() > 20) {
            byte onByte = buf.get(21);
            switchedOn = onByte == 0x01;
        }
    }

    void update(ByteBuffer payload) {
        CharBuffer nameBuf = nameCharset.decode(byteBufferWrap(payload.array(), 26, 16));
        super.name = nameBuf.toString();
        updateStatus(byteBufferWrap(payload.array(), 10, 16));
    }

    void updateStatus(ByteBuffer payload) {
        payload.position(8);
        byte onByte = payload.get();
        lum = payload.get();
        temp = payload.getShort();
        red = payload.get();
        green = payload.get();
        blue = payload.get();

        switchedOn = onByte == 0x01;
    }

    public void update() throws IOException, InterruptedException {
        Packet updateStatus = new UpdateLightStatus(address);
        conn.send(updateStatus);
        synchronized (this) {
            this.wait();
        }
    }

    public Address getAddress() {
        return address;
    }

    @Override
    protected byte[] getAddressBytes() {
        return address.address;
    }

}
