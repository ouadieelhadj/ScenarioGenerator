package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOPackager;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/** Two-byte unsigned big-endian length followed by the packed ISO message. */
public final class WayPosLengthChannel extends BaseChannel {

    public WayPosLengthChannel() {
    }

    public WayPosLengthChannel(ISOPackager packager) {
        setPackager(packager);
    }

    public WayPosLengthChannel(String host, int port, ISOPackager packager) {
        super(host, port, packager);
    }

    @Override
    protected void sendMessageLength(int length) throws IOException {
        if (length < 0 || length > 0xFFFF) {
            throw new IOException("Way POS frame length out of range: " + length);
        }
        serverOut.write((length >>> 8) & 0xFF);
        serverOut.write(length & 0xFF);
    }

    @Override
    protected int getMessageLength() throws IOException, EOFException {
        byte[] header = new byte[2];
        ((DataInputStream) serverIn).readFully(header);
        return ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
    }
}
