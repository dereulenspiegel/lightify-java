package de.akuz.lightify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

public abstract class Luminary {

    public interface ChangeListener {

        public void luminarySwitchedOnUpdated(Luminary lum, boolean switchedOn);

        public void luminaryTemperatureUpdated(Luminary lum, short temp);

        public void luminaryLuminanceUpdated(Luminary lum, short luminance);

        public void luminaryColorUpdated(Luminary lum, byte red, byte green, byte blue);

    }

    protected Gateway conn;

    protected Charset nameCharset;

    protected boolean switchedOn;

    protected byte lum;
    protected short temp;

    protected byte red;
    protected byte green;
    protected byte blue;

    protected String name;

    private Set<ChangeListener> listeners = new HashSet<Luminary.ChangeListener>();

    Luminary(Gateway conn) {
        this.conn = conn;
        nameCharset = Charset.forName("ASCII");
    }

    protected ByteBuffer receive() throws IOException {
        return conn.receiveData();
    }

    public void registerListener(ChangeListener l) {
        this.listeners.add(l);
    }

    public void unregisterListener(ChangeListener l) {
        this.listeners.remove(l);
    }

    public boolean isOn() {
        return switchedOn;
    }

    public int getLuminance() {
        return lum;
    }

    public int getTemperature() {
        return temp;
    }

    public byte[] getRGB() {
        return new byte[] { red, green, blue };
    }

    public String getName() {
        return name;
    }

    public boolean isGroup() {
        return Group.class.isAssignableFrom(this.getClass());
    }

    public void setOn(boolean state) throws IOException {
        Packet command = new OnOffCommand(this, state);
        conn.send(command);
    }

    public void setLuminance(byte value, short time) throws IOException {
        Packet command = new SetLuminance(this, value, time);
        conn.send(command);
    }

    public void setColor(byte red, byte green, byte blue, short time) throws IOException {
        Packet command = new SetColor(this, red, green, blue, time);
        conn.send(command);
    }

    public void setTemperature(short temp, short time) throws IOException {
        Packet command = new SetTemperature(this, temp, time);
        conn.send(command);
    }

    public abstract byte[] getAddressBytes();

    protected void notifySwitchedOnChanged(boolean state) {
        for (ChangeListener l : listeners) {
            l.luminarySwitchedOnUpdated(this, state);
        }
    }

    protected void notifyLuminanceChanged(short luminance) {
        for (ChangeListener l : listeners) {
            l.luminaryLuminanceUpdated(this, luminance);
        }
    }

    protected void notifyTemperatureChanged(short temperature) {
        for (ChangeListener l : listeners) {
            l.luminaryTemperatureUpdated(this, temperature);
        }
    }

    protected void notifyColorChanged(byte red, byte green, byte blue) {
        for (ChangeListener l : listeners) {
            l.luminaryColorUpdated(this, red, green, blue);
        }
    }

    protected ByteBuffer byteBufferWrap(byte[] data, int pos, int len) {
        return conn.byteBufferWrap(data, pos, len);
    }

    void updateOn(boolean state) {
        this.switchedOn = state;
        notifySwitchedOnChanged(state);
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

        notifyColorChanged(red, green, blue);
        notifyLuminanceChanged(lum);
        notifyTemperatureChanged(temp);
        notifySwitchedOnChanged(switchedOn);
    }

}
