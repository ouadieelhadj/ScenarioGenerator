package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.util.Random;

/**
 * Network utility for ISO 8583 messages.
 * Handles STAN/RRN generation and raw socket send/receive.
 */
@Component
public class NetworkUtil {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtil.class);

    private final McPackager packager;

    public NetworkUtil(McPackager packager) {
        this.packager = packager;
    }

    // ── STAN / RRN generation ────────────────────────────────

    public String generateStan() {
        return String.format("%06d",
                Math.abs(new Random().nextInt()) % 1_000_000);
    }

    public String generateRrn() {
        Random rng = new Random();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder rrn = new StringBuilder();
        for (int i = 0; i < 12; i++)
            rrn.append(chars.charAt(Math.abs(rng.nextInt()) % chars.length()));
        return rrn.toString();
    }

    // ── Send / Receive ───────────────────────────────────────

    /**
     * Send ISO message and receive response via raw TCP socket.
     * Frame format : 2 bytes length + message bytes.
     */
    public ISOMsg sendAndReceive(ISOMsg request, String host,
                                  int port, int timeoutSeconds) throws Exception {
        byte[] requestBytes = request.pack();
        log.debug("Sending {} bytes to {}:{}", requestBytes.length, host, port);

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(timeoutSeconds * 1000);

            // Send
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeShort(requestBytes.length);
            out.write(requestBytes);
            out.flush();

            // Receive
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int len = in.readShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            log.debug("Received {} bytes from {}:{}", len, host, port);

            // Unpack
            ISOMsg response = new ISOMsg();
            response.setPackager(packager);
            response.unpack(responseBytes);
            return response;
        }
    }

    /**
     * Receive ISO message from socket (server side).
     */
    public ISOMsg receive(DataInputStream in) throws Exception {
        int len = in.readShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(bytes);
        return msg;
    }

    /**
     * Send ISO message via socket (server side).
     */
    public void send(DataOutputStream out, ISOMsg msg) throws Exception {
        byte[] bytes = msg.pack();
        out.writeShort(bytes.length);
        out.write(bytes);
        out.flush();
    }

    /**
     * Safe field getter — returns null if field not present.
     */
    public String safeGet(ISOMsg msg, int field) {
        try { return msg.hasField(field) ? msg.getString(field) : null; }
        catch (Exception e) { return null; }
    }

    public McPackager getPackager() { return packager; }
}
