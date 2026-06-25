package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * Network utility for DMAS ISO 8583 messages (EBCDIC + bitmap binaire).
 * Jumeau de NetworkUtil mais câblé sur McPackagerEbcdic.
 * Framing identique : 2 octets length (big-endian) + message bytes.
 */
@Component
public class DmasNetworkUtil {
    private final java.util.concurrent.atomic.AtomicInteger stanSeq = new java.util.concurrent.atomic.AtomicInteger(new java.util.Random().nextInt(900000));

    private static final Logger log = LoggerFactory.getLogger(DmasNetworkUtil.class);

    private final McPackagerEbcdic packager;

    public DmasNetworkUtil(McPackagerEbcdic packager) {
        this.packager = packager;
    }

    public String generateStan() {
        // n-6 conforme spec DMAS (DE011 p.297). Compteur atomique PARTAGE (singleton)
        // -> STAN globalement unique entre tous les flux (auth, advice, reversal, loadtest)
        // -> evite les collisions de correlation dans la map pending de DmasJposServer.
        int v = Math.floorMod(stanSeq.getAndIncrement(), 1_000_000);
        return String.format("%06d", v);
    }

    public String generateRrn() {
        Random rng = new Random();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder rrn = new StringBuilder();
        for (int i = 0; i < 12; i++)
            rrn.append(chars.charAt(Math.abs(rng.nextInt()) % chars.length()));
        return rrn.toString();
    }

    public ISOMsg sendAndReceive(ISOMsg request, String host,
                                 int port, int timeoutSeconds) throws Exception {
        byte[] requestBytes = request.pack();
        log.debug("[DMAS-NET] Sending {} bytes to {}:{}", requestBytes.length, host, port);
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(timeoutSeconds * 1000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(requestBytes.length);
            out.write(requestBytes);
            out.flush();

            DataInputStream in = new DataInputStream(socket.getInputStream());
            int len = in.readShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            log.debug("[DMAS-NET] Received {} bytes from {}:{}", len, host, port);

            ISOMsg response = new ISOMsg();
            response.setPackager(packager);
            response.unpack(responseBytes);
            return response;
        }
    }

    public ISOMsg receive(DataInputStream in) throws Exception {
        int len = in.readShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(bytes);
        return msg;
    }

    public void send(DataOutputStream out, ISOMsg msg) throws Exception {
        byte[] bytes = msg.pack();
        out.writeShort(bytes.length);
        out.write(bytes);
        out.flush();
    }

    public String safeGet(ISOMsg msg, int field) {
        try { return msg.hasField(field) ? msg.getString(field) : null; }
        catch (Exception e) { return null; }
    }

    public McPackagerEbcdic getPackager() { return packager; }
}
