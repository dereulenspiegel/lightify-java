package de.akuz.lightify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Group extends Luminary {

    private final static Logger logger = LoggerFactory.getLogger(Group.class);

    private int id;

    private List<Light> lights = new ArrayList<Light>(128);

    Group(Gateway conn, ByteBuffer payload) {
        super(conn);
        update(payload);
    }

    void update(ByteBuffer payload) {
        payload.position(0);
        id = payload.getShort();
        CharBuffer nameBuf = nameCharset.decode(payload);
        name = nameBuf.toString().trim();
    }

    void updateInfo(ByteBuffer payload) {
        CharBuffer nameBuf = nameCharset.decode(byteBufferWrap(payload.array(), 0, 16));
        this.name = nameBuf.toString();
        logger.debug("Updating group name to {}", name);
        int num = payload.get(16);
        logger.debug("Group info contains {} lights", num);
        for (int i = 0; i < num; i++) {
            int pos = 17 + i * 8;
            ByteBuffer lightPayload = byteBufferWrap(payload.array(), pos, 8);

            byte[] addr = lightPayload.array();
            logger.debug("Light address {}", addr);
            Light l = conn.getLightByAddress(addr);
            if (l != null) {
                if (!lights.contains(l)) {
                    lights.add(l);
                }
            } else {
                l = new Light(conn, addr);
                lights.add(l);
                conn.addLight(l);
            }
        }
    }

    Group(Gateway conn, String name, int id) {
        super(conn);
        this.name = name;
        this.id = id;
    }

    public List<Light> getLights() {
        return lights;
    }

    public void updateGroupInfo() throws IOException, InterruptedException {
        Packet command = new UpdateGroupInfo(id);
        conn.send(command);
        synchronized (this) {
            this.wait();
        }
    }

    public int getId() {
        return id;
    }

    @Override
    protected byte[] getAddressBytes() {
        return new byte[] { (byte) id, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    }

}
