package de.akuz.lightify;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import de.akuz.lightify.Light.Address;

public class Gateway {

    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(Gateway.class);

    private String host;
    private int port;

    private Socket socket;
    private AtomicInteger sequence = new AtomicInteger(0);

    private List<Group> groups = new ArrayList<Group>(50);
    private List<Light> lights = new ArrayList<Light>(128);

    private Thread receiveThread;

    public Gateway(String host) {
        this(host, 4000);
    }

    public Gateway(String host, int port) {
        this.host = host;
        this.port = port;
        receiveThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        waitAndReceive();
                    } catch (IOException e) {
                        this.interrupt();
                        try {
                            disconnect();
                        } catch (IOException e1) {
                            logger.error("Error while disconnecting in IOException catch", e1);
                        }
                    }
                }
            }
        };
    }

    public void connect() throws IOException {
        logger.debug("Disconnecting");
        socket = new Socket(host, port);
        receiveThread.start();
    }

    public void disconnect() throws IOException {
        receiveThread.interrupt();
        socket.close();
    }

    byte getSequence() {
        return (byte) sequence.incrementAndGet();
    }

    public List<Group> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public List<Group> refreshGroups() throws IOException, InterruptedException {
        groups.clear();
        Packet command = new UpdateGroupsList();
        send(command);
        synchronized (groups) {
            groups.wait();
        }
        return Collections.unmodifiableList(groups);
    }

    public List<Light> refreshAllLights() throws IOException, InterruptedException {
        Packet command = new UpdateAllLightStatus();
        send(command);
        synchronized (lights) {
            lights.wait();
        }
        return Collections.unmodifiableList(lights);
    }

    ByteBuffer byteBufferWrap(byte[] data, int pos, int len) {
        ByteBuffer buf = ByteBuffer.allocate(len);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(data, pos, len);
        buf.position(0);
        return buf;
    }

    void send(ByteBuffer buf) throws IOException {
        send(buf.array());
    }

    void send(byte[] data) throws IOException {
        logger.debug("Sending: {}", data);
        socket.getOutputStream().write(data);
    }

    public void send(Packet packet) throws IOException {
        send(packet.serialize(getSequence()));
    }

    private void waitAndReceive() throws IOException {
        ByteBuffer buf = receiveData();
        buf.position(0);
        byte command = buf.get(3);
        switch (command) {
            case Packet.COMMAND_GROUP_LIST:
                logger.debug("Received group list");
                refreshGroups(buf);
                break;
            case Packet.COMMAND_GROUP_INFO:
                logger.debug("Received group info");
                updateGroupInfo(buf);
                break;
            case Packet.COMMAND_LIGHT_STATUS:
                logger.debug("Received light status");
                updateLight(buf);
                break;
            case Packet.COMMAND_ONOFF:
                updateOnOff(buf);
                break;
            case Packet.COMMAND_LUMINANCE:
                // TODO we don't seem to get information about new luminance, probably this can be solved
                break;
            case Packet.COMMAND_ALL_LIGHT_STATUS:
                logger.debug("Received status for all lights");
                updateAllLights(buf);
                break;
            default:
                logger.warn("Received unknown packet. Packet command {}", command);
                logger.warn("Data: {}", buf.array());
        }
    }

    private void updateAllLights(ByteBuffer buf) {

        int count = buf.getShort(9);
        logger.debug("Received update for {} lights", count);

        for (int i = 0; i < count; i++) {
            int pos = 11 + i * 50;
            ByteBuffer payload = byteBufferWrap(buf.array(), pos, 50);
            byte[] addr = new byte[8];
            payload.position(2);
            payload.get(addr, 0, 8);

            Light l = getLightByAddress(addr);
            if (l == null) {
                l = new Light(this, addr);
                lights.add(l);
            }
            l.update(payload);
        }

        synchronized (lights) {
            lights.notify();
        }
    }

    private void updateOnOff(ByteBuffer buf) {
        byte[] addr = new byte[8];
        buf.position(11);
        buf.get(addr, 0, 8);
        Luminary lum = getLuminary(addr);
        if (lum == null) {
            logger.error("Received data for unknown light {}", addr);
            return;
        }

        byte onOff = buf.get(19);
        lum.updateOn(onOff == 0x01);

    }

    private void updateLight(ByteBuffer buf) {
        byte[] addr = new byte[8];
        buf.position(11);
        buf.get(addr, 0, 8);
        logger.debug("Updating light {}", addr);
        Light l = getLightByAddress(addr);
        if (l == null) {
            logger.error("Updating unknown light with address {}", addr);
            return;
        }
        l.updateStatusData(buf);
        synchronized (l) {
            l.notify();
        }
    }

    private void updateGroupInfo(ByteBuffer buf) {
        short groupId = buf.getShort(9);
        logger.debug("Updating group {}", groupId);
        Group g = getGroupById(groupId);
        if (g == null) {
            // FIXME determine the name
            g = new Group(this, "", groupId);
        }
        g.updateInfo(byteBufferWrap(buf.array(), 11, buf.array().length - 11));
        synchronized (g) {
            g.notify();
        }
    }

    private void refreshGroups(ByteBuffer buf) {
        int groupCount = buf.getShort(9);
        logger.debug("Group count {}", groupCount);
        for (int i = 0; i < groupCount; i++) {
            int pos = 11 + i * 18;
            ByteBuffer payload = byteBufferWrap(buf.array(), pos, 18);

            int idx = payload.getShort();
            Group g = getGroupById(idx);
            if (g == null) {
                g = new Group(this, payload);
                groups.add(g);
            } else {
                g.update(payload);
            }
        }
        synchronized (groups) {
            groups.notify();
        }
    }

    void addLight(Light l) {
        if (!lights.contains(l)) {
            lights.add(l);
        }
    }

    public List<Light> getLights() {
        return Collections.unmodifiableList(lights);
    }

    public Luminary getLuminaryByName(String name) {
        Group g = getGroupByName(name);
        if (g != null) {
            return g;
        }
        return getLightByName(name);
    }

    public Light getLightByName(String name) {
        for (Light l : lights) {
            if (l.getName().equals(name)) {
                return l;
            }
        }
        return null;
    }

    public Group getGroupByName(String name) {
        for (Group g : groups) {
            if (g.getName().equals(name)) {
                return g;
            }
        }
        return null;
    }

    public Luminary getLuminary(byte[] addr) {
        if (isGroupId(addr)) {
            return getGroupById(addr[0]);
        } else {
            return getLightByAddress(addr);
        }
    }

    public Light getLightByAddress(byte[] addressBytes) {
        Address address = new Address(addressBytes);
        for (Light l : lights) {
            if (l.getAddress().equals(address)) {
                return l;
            }
        }
        return null;
    }

    public Group getGroupById(int id) {
        for (Group g : groups) {
            if (g.getId() == id) {
                return g;
            }
        }
        return null;
    }

    ByteBuffer receiveData() throws IOException {
        byte[] array = new byte[2];
        logger.debug("Reading two bytes from socket to get length of data");
        int count = socket.getInputStream().read(array);
        if (count < 2) {
            throw new IOException("Didn't read enough bytes to determine expected length");
        }
        ByteBuffer arrayBuf = ByteBuffer.wrap(array);
        arrayBuf.order(ByteOrder.LITTLE_ENDIAN);
        int expectedLength = arrayBuf.getChar();
        ByteBuffer finalBuf = ByteBuffer.allocate(expectedLength + 2);
        finalBuf.order(ByteOrder.LITTLE_ENDIAN);
        finalBuf.put(array);
        int readBytes = 0;
        array = new byte[expectedLength];
        logger.debug("Trying to read {} bytes from socket", expectedLength);
        while (readBytes < expectedLength) {
            count = socket.getInputStream().read(array);
            readBytes += count;
            finalBuf.put(array, 0, count);
        }
        logger.debug("Received: {}", finalBuf.array());
        return finalBuf;
    }

    private boolean isGroupId(byte[] addr) {
        for (int i = 1; i < addr.length; i++) {
            if (addr[i] != 0x00) {
                return false;
            }
        }
        return true;
    }

}
