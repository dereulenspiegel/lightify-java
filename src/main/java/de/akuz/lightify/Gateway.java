package de.akuz.lightify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.akuz.lightify.Light.Address;

public class Gateway implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(Gateway.class);

    private static final long INITIAL_RECONNECT_INTERVAL = 500; // 500 ms.
    private static final long MAXIMUM_RECONNECT_INTERVAL = 30000; // 30 sec.
    private static final int READ_BUFFER_SIZE = 2048;
    private static final int WRITE_BUFFER_SIZE = 2048;

    private long reconnectInterval = INITIAL_RECONNECT_INTERVAL;

    private ByteBuffer readBuf = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
    private ByteBuffer writeBuf = ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE);

    private final Thread thread = new Thread(this);
    private SocketAddress address;

    private Selector selector;
    private SocketChannel channel;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private AtomicInteger sequence = new AtomicInteger(0);

    private List<Group> groups = new ArrayList<Group>(50);
    private List<Light> lights = new ArrayList<Light>(128);

    private int expectedLength = -1;

    public Gateway(String host) {
        this(host, 4000);
    }

    public Gateway(String host, int port) {
        this.address = new InetSocketAddress(host, port);
        readBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    public void connect() throws IOException {
        thread.start();
        // Only return when we are connected and really ready
        while (!connected.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    public void disconnect() {
        thread.interrupt();
    }

    private void send(ByteBuffer buffer) throws InterruptedException, IOException {
        if (!connected.get()) {
            throw new IOException("not connected");
        }
        synchronized (writeBuf) {
            // try direct write of what's in the buffer to free up space
            if (writeBuf.remaining() < buffer.remaining()) {
                writeBuf.flip();
                int bytesOp = 0, bytesTotal = 0;
                while (writeBuf.hasRemaining() && (bytesOp = channel.write(writeBuf)) > 0) {
                    bytesTotal += bytesOp;
                }
                writeBuf.compact();
                logger.debug("Written {} bytes to the network", bytesTotal);
            }

            // if didn't help, wait till some space appears
            if (Thread.currentThread().getId() != thread.getId()) {
                while (writeBuf.remaining() < buffer.remaining()) {
                    writeBuf.wait();
                }
            } else {
                if (writeBuf.remaining() < buffer.remaining()) {
                    throw new IOException("send buffer full"); // TODO: add reallocation or buffers chain
                }
            }
            writeBuf.put(buffer);

            // try direct write to decrease the latency
            writeBuf.flip();
            int bytesOp = 0, bytesTotal = 0;
            while (writeBuf.hasRemaining() && (bytesOp = channel.write(writeBuf)) > 0) {
                bytesTotal += bytesOp;
            }
            writeBuf.compact();
            logger.debug("Written {} bytes to the network", bytesTotal);

            if (writeBuf.hasRemaining()) {
                SelectionKey key = channel.keyFor(selector);
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }

    private void configureChannel(SocketChannel channel) throws IOException {
        channel.configureBlocking(false);
        channel.socket().setSendBufferSize(0x100000); // 1Mb
        channel.socket().setReceiveBufferSize(0x100000); // 1Mb
        channel.socket().setKeepAlive(true);
        channel.socket().setReuseAddress(true);
        channel.socket().setSoLinger(false, 0);
        channel.socket().setSoTimeout(0);
        channel.socket().setTcpNoDelay(true);
    }

    @Override
    public void run() {
        logger.info("event loop running");
        try {
            while (!Thread.interrupted()) { // reconnection loop
                try {
                    selector = Selector.open();
                    channel = SocketChannel.open();
                    configureChannel(channel);

                    channel.connect(address);
                    channel.register(selector, SelectionKey.OP_CONNECT);

                    while (!thread.isInterrupted() && channel.isOpen()) { // events multiplexing loop
                        if (selector.select() > 0) {
                            processSelectedKeys(selector.selectedKeys());
                        }
                    }
                } catch (Exception e) {
                    logger.error("exception", e);
                } finally {
                    connected.set(false);
                    onDisconnected();
                    writeBuf.clear();
                    readBuf.clear();
                    if (channel != null) {
                        channel.close();
                    }
                    if (selector != null) {
                        selector.close();
                    }
                    logger.info("connection closed");
                }

                try {
                    Thread.sleep(reconnectInterval);
                    if (reconnectInterval < MAXIMUM_RECONNECT_INTERVAL) {
                        reconnectInterval *= 2;
                    }
                    logger.info("reconnecting to {}", address);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("unrecoverable error", e);
        }

        logger.info("event loop terminated");
    }

    private void onDisconnected() {
        logger.info("Disconnected from {}", address);

    }

    private void processSelectedKeys(Set<SelectionKey> keys) throws Exception {
        Iterator<SelectionKey> itr = keys.iterator();
        while (itr.hasNext()) {
            SelectionKey key = itr.next();
            if (key.isReadable()) {
                processRead(key);
            }
            if (key.isWritable()) {
                processWrite(key);
            }
            if (key.isConnectable()) {
                processConnect(key);
            }
            if (key.isAcceptable()) {
                ;
            }
            itr.remove();
        }
    }

    private void processConnect(SelectionKey key) throws Exception {
        SocketChannel ch = (SocketChannel) key.channel();
        if (ch.finishConnect()) {
            key.interestOps(key.interestOps() ^ SelectionKey.OP_CONNECT);
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            reconnectInterval = INITIAL_RECONNECT_INTERVAL;
            connected.set(true);
            onConnected();
        }
    }

    private void onConnected() {
        logger.info("Connected to Lightify Gateway on address ({})", address);

    }

    private void processRead(SelectionKey key) throws Exception {
        ReadableByteChannel ch = (ReadableByteChannel) key.channel();

        int bytesOp = 0, bytesTotal = 0;
        while (readBuf.hasRemaining() && (bytesOp = ch.read(readBuf)) > 0) {
            bytesTotal += bytesOp;
        }
        logger.debug("Read {} bytes from network", bytesTotal);

        if (bytesTotal > 0) {
            readBuf.flip();
            onRead(readBuf);
            readBuf.compact();
        } else if (bytesOp == -1) {
            logger.info("peer closed read channel");
            ch.close();
        }
    }

    private void onRead(ByteBuffer readBuf) {
        readBuf.order(ByteOrder.LITTLE_ENDIAN);
        if (readBuf.remaining() > 1 && expectedLength == -1) {
            expectedLength = readBuf.getChar();
        }
        if (expectedLength != -1 && readBuf.limit() == expectedLength + 2) {
            byte[] data = new byte[expectedLength + 2];
            readBuf.position(0);
            readBuf.get(data, 0, data.length);
            parseData(byteBufferWrap(data, 0, expectedLength + 2));
            expectedLength = -1;
        }
    }

    private void parseData(ByteBuffer buf) {
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

    private void processWrite(SelectionKey key) throws IOException {
        WritableByteChannel ch = (WritableByteChannel) key.channel();
        synchronized (writeBuf) {
            writeBuf.flip();

            int bytesOp = 0, bytesTotal = 0;
            while (writeBuf.hasRemaining() && (bytesOp = ch.write(writeBuf)) > 0) {
                bytesTotal += bytesOp;
            }
            logger.debug("Written {} bytes to the network", bytesTotal);

            if (writeBuf.remaining() == 0) {
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }

            if (bytesTotal > 0) {
                writeBuf.notify();
            } else if (bytesOp == -1) {
                logger.info("peer closed write channel");
                ch.close();
            }

            writeBuf.compact();
        }
    }

    byte getSequence() {
        return (byte) sequence.incrementAndGet();
    }

    void send(byte[] data) throws IOException, InterruptedException {
        logger.debug("Sending: {}", data);
        send(ByteBuffer.wrap(data));
    }

    public void send(Packet packet) throws IOException, InterruptedException {
        send(packet.serialize(getSequence()));
    }

    ByteBuffer byteBufferWrap(byte[] data, int pos, int len) {
        ByteBuffer buf = ByteBuffer.allocate(len);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(data, pos, len);
        buf.position(0);
        return buf;
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

    private boolean isGroupId(byte[] addr) {
        for (int i = 1; i < addr.length; i++) {
            if (addr[i] != 0x00) {
                return false;
            }
        }
        return true;
    }
}
