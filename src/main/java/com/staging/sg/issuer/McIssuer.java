package com.staging.sg.issuer;

import com.staging.sg.hsm.ThalesHsmService;
import com.staging.sg.iso.NetworkUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class McIssuer {

    private static final Logger log = LoggerFactory.getLogger(McIssuer.class);

    private final NetworkUtil      net;
    private final ThalesHsmService hsm;

    @Value("${mc.issuer.port:8200}")
    private int issuerPort;

    private Thread       serverThread;
    private ServerSocket serverSocket;
    private final AtomicLong msgCount = new AtomicLong(0);

    public McIssuer(NetworkUtil net, ThalesHsmService hsm) {
        this.net = net;
        this.hsm = hsm;
    }

    @PostConstruct
    public void start() {
        serverThread = new Thread(this::runServer, "mc-issuer-server");
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("[ISSUING] ISOServer started — port {}", issuerPort);
    }

    @PreDestroy
    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (serverThread != null) serverThread.interrupt();
        log.info("[ISSUING] ISOServer stopped");
    }

    public long getMessageCount() { return msgCount.get(); }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(issuerPort);
            log.info("[ISSUING] Listening on :{}", issuerPort);
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                long id = msgCount.incrementAndGet();
                Thread t = new Thread(() -> handleConnection(client), "mc-issuer-client-" + id);
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted())
                log.error("[ISSUING] Server error : {}", e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            ISOMsg request = net.receive(in);
            String mti = request.getMTI();

            switch (mti) {
                case "0820" -> handleKeyExchange(request, out);
                case "0800" -> handleNetworkMessage(request, out);
                default     -> log.warn("[ISSUING] Unknown MTI : {}", mti);
            }

        } catch (Exception e) {
            log.error("[ISSUING] Connection error : {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // ── Key Exchange 0820 ────────────────────────────────────

    private void handleKeyExchange(ISOMsg request, DataOutputStream out) throws Exception {
        String fc   = net.safeGet(request, 70);
        String stan = net.safeGet(request, 11);
        String keyName = switch (fc != null ? fc : "") {
            case "101" -> "ZMK{KEK}";
            case "102" -> "ZPK{ZMK}";
            case "103" -> "ZAK{ZMK}";
            default    -> "KEY-" + fc;
        };

        logIsoMsg("RECEIVED", "0820 Key-Exchange " + keyName, request);

        // Decrypt and store key from DE053
        if (request.hasField(53)) {
            try {
                String de53        = request.getString(53);
                String kcv         = de53.substring(0, 6);
                byte[] encryptedKey = hsm.hexToBytes(de53.substring(6));
                switch (fc != null ? fc : "") {
                    case "101" -> {
                        byte[] zmk = hsm.decryptUnderKek(encryptedKey);
                        hsm.setSessionKeys(zmk, hsm.getSessionZpk(), hsm.getSessionZak());
                        log.info("[ISSUING] ZMK loaded — KCV={}", kcv);
                    }
                    case "102" -> {
                        byte[] zpk = hsm.decryptUnderZmk(encryptedKey);
                        hsm.setSessionKeys(hsm.getSessionZmk(), zpk, hsm.getSessionZak());
                        log.info("[ISSUING] ZPK loaded — KCV={}", kcv);
                    }
                    case "103" -> {
                        byte[] zak = hsm.decryptUnderZmk(encryptedKey);
                        hsm.setSessionKeys(hsm.getSessionZmk(), hsm.getSessionZpk(), zak);
                        log.info("[ISSUING] ZAK loaded — KCV={}", kcv);
                    }
                }
            } catch (Exception e) {
                log.error("[ISSUING] Key decryption failed : {}", e.getMessage());
            }
        }

        // Build 0830
        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0830");
        response.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        response.set(11, stan);
        response.set(39, "00");
        response.set(70, fc);

        logIsoMsg("SENT", "0830 Key-Exchange Response " + keyName, response);
        net.send(out, response);
    }

    // ── Network Message 0800 ─────────────────────────────────

    private void handleNetworkMessage(ISOMsg request, DataOutputStream out) throws Exception {
        String fc   = net.safeGet(request, 70);
        String stan = net.safeGet(request, 11);
        String type = switch (fc != null ? fc : "") {
            case "301" -> "Sign-on";
            case "302" -> "Echo Test";
            default    -> "Network-" + fc;
        };

        logIsoMsg("RECEIVED", "0800 " + type, request);

        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        response.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        response.set(11, stan);
        response.set(39, "00");
        response.set(70, fc);

        logIsoMsg("SENT", "0810 " + type + " Response", response);
        net.send(out, response);
    }

    // ── Log ISO message ──────────────────────────────────────

    private void logIsoMsg(String direction, String type, ISOMsg msg) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─────────────────────────────────────────────────\n");
            sb.append(String.format("│ [ISSUING] %s — %s\n", direction, type));
            sb.append("├─────────────────────────────────────────────────\n");
            try { sb.append(String.format("│ MTI                   : %s\n", msg.getMTI())); } catch (Exception ignored) {}
            if (msg.hasField(7))  sb.append(String.format("│ DE007 Date/Time       : %s\n", msg.getString(7)));
            if (msg.hasField(11)) sb.append(String.format("│ DE011 STAN            : %s\n", msg.getString(11)));
            if (msg.hasField(39)) sb.append(String.format("│ DE039 Response Code   : %s\n", msg.getString(39)));
            if (msg.hasField(53)) sb.append(String.format("│ DE053 Security Info   : %s\n", msg.getString(53).substring(0, Math.min(12, msg.getString(53).length())) + "..."));
            if (msg.hasField(70)) sb.append(String.format("│ DE070 Network Code    : %s\n", msg.getString(70)));
            sb.append("├─────────────────────────────────────────────────\n");
            sb.append(String.format("│ HEX : %s\n", ISOUtil.hexString(msg.pack())));
            sb.append("└─────────────────────────────────────────────────");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ISSUING] Error logging ISO message : {}", e.getMessage());
        }
    }
}
