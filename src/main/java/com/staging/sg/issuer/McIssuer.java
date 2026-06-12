package com.staging.sg.issuer;

import com.staging.sg.hsm.ThalesHsmService;
import com.staging.sg.iso.McPackager;
import com.staging.sg.iso.NetworkUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
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

/**
 * Issuing Simulator — Mastercard.
 * Listens on port 8200.
 * Handles :
 *   0820 — Key Exchange (FC=101 ZMK, FC=102 ZPK, FC=103 ZAK)
 *   0800 — Network Management (FC=301 Sign-on, FC=302 Echo)
 */
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
        log.info("[ISSUING] ISOServer started — listening on port {}", issuerPort);
    }

    @PreDestroy
    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (serverThread != null) serverThread.interrupt();
        log.info("[ISSUING] ISOServer stopped");
    }

    public long getMessageCount() { return msgCount.get(); }

    // ── Server loop ──────────────────────────────────────────

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

    // ── Connection handler ───────────────────────────────────

    private void handleConnection(Socket socket) {
        try {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            ISOMsg request = net.receive(in);
            String mti = request.getMTI();
            log.debug("[ISSUING] Received MTI={}", mti);

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

    private void handleKeyExchange(ISOMsg request,
                                    DataOutputStream out) throws Exception {
        String fc   = net.safeGet(request, 70);
        String stan = net.safeGet(request, 11);

        String keyName = switch (fc != null ? fc : "") {
            case "101" -> "ZMK{KEK}";
            case "102" -> "ZPK{ZMK}";
            case "103" -> "ZAK{ZMK}";
            default    -> "KEY-" + fc;
        };

        log.info("[ISSUING] 0820 Key-Exchange {} received — STAN={}", keyName, stan);

        // Decrypt and store key from DE053
        if (request.hasField(53)) {
            try {
                String de53 = request.getString(53);
                // DE053 format : KCV(6) + encrypted key hex
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

        // Send 0830 response
        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0830");
        response.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        response.set(11, stan);
        response.set(39, "00");
        response.set(70, fc);

        net.send(out, response);
        log.info("[ISSUING] 0830 Key-Exchange Response sent — {} DE39=00", keyName);
    }

    // ── Network Message 0800 ─────────────────────────────────

    private void handleNetworkMessage(ISOMsg request,
                                       DataOutputStream out) throws Exception {
        String fc   = net.safeGet(request, 70);
        String stan = net.safeGet(request, 11);

        String type = switch (fc != null ? fc : "") {
            case "301" -> "Sign-on";
            case "302" -> "Echo Test";
            default    -> "Network-" + fc;
        };

        log.info("[ISSUING] 0800 {} received — STAN={}", type, stan);

        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        response.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        response.set(11, stan);
        response.set(39, "00");
        response.set(70, fc);

        net.send(out, response);
        log.info("[ISSUING] 0810 {} Response sent — DE39=00", type);
    }
}
