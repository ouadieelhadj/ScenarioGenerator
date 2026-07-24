package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOPackager;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Channel jPOS au framing identique a McDmasNetworkUtil :
 *   2 octets de longueur (big-endian) + message packe.
 * Utilise des deux cotes (serveur acquereur + client issuer).
 */
public class McDmasLengthChannel extends BaseChannel {

    public McDmasLengthChannel() { super(); }

    public McDmasLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public McDmasLengthChannel(String host, int port, ISOPackager p) {
        super(host, port, p);
    }

    /** Ecrit la longueur sur 2 octets big-endian (comme writeShort). */
    @Override
    protected void sendMessageLength(int len) throws IOException {
        serverOut.write((len >> 8) & 0xFF);
        serverOut.write(len & 0xFF);
    }

    /** Lit la longueur sur 2 octets big-endian (comme readShort). */
    @Override
    protected int getMessageLength() throws IOException, java.io.EOFException {
        byte[] b = new byte[2];
        ((DataInputStream) serverIn).readFully(b);
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }
}
