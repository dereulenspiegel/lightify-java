package de.akuz.lightify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public abstract class Luminary {

    public interface ChangeListener {

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

    Luminary(Gateway conn) {
        this.conn = conn;
        nameCharset = Charset.forName("ASCII");
    }

    protected ByteBuffer receive() throws IOException {
        return conn.receiveData();
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

    protected abstract byte[] getAddressBytes();

    protected ByteBuffer byteBufferWrap(byte[] data, int pos, int len) {
        return conn.byteBufferWrap(data, pos, len);
    }

    void updateOn(boolean state) {
        this.switchedOn = state;
    }

}
