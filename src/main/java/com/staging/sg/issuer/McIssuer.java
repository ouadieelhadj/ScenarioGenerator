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

    private final NetworkUtil       net;
    private final ThalesHsmService  hsm;
    private final McDecisionEngine  decisionEngine;

    @Value("${mc.issuer.port:8200}")
    private int issuerPort;

    @Value("${mc.security.mac-verify:true}")
    private boolean macVerify;

    @Value("${mc.security.mac-fields:2,3,4,7,11,12,13,18,22,37,41,42,49,64}")
    private String macFields;

    @Value("${mc.security.mac-field:64}")
    private int macField;

    @Value("${mc.security.pin-decrypt-log:true}")
    private boolean pinDecryptLog;

    private Thread       serverThread;
    private ServerSocket serverSocket;
    private final AtomicLong msgCount = new AtomicLong(0);

    public McIssuer(NetworkUtil net, ThalesHsmService hsm, McDecisionEngine decisionEngine) {
        this.net            = net;
        this.hsm            = hsm;
        this.decisionEngine = decisionEngine;
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
                case "0100" -> handleAuthorization(request, out);
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

    // ── Authorization 0100 ───────────────────────────────────

    private void handleAuthorization(ISOMsg request, DataOutputStream out) throws Exception {
        logIsoMsg("RECEIVED", "0100 Authorization", request);

        // Step 1 : Verify MAC
        if (macVerify && hsm.getSessionZak() != null) {
            boolean macOk = hsm.verifyMac(request.pack(), macField, macFields);
            if (macOk) {
                log.info("[ISSUING] MAC verification OK");
            } else {
                log.warn("[ISSUING] MAC verification FAILED");
            }
        }

        // Step 2 : Decrypt and log PIN
        if (pinDecryptLog && request.hasField(52) && hsm.getSessionZpk() != null) {
            try {
                byte[] pinBlock    = request.getBytes(52);
                byte[] decrypted   = hsm.decryptPinBlock(pinBlock, hsm.getSessionZpk());
                String pan         = net.safeGet(request, 2);
                String pin         = extractPin(decrypted, pan);
                log.info("[ISSUING] PIN decrypted : {}", pin);
            } catch (Exception e) {
                log.warn("[ISSUING] PIN decryption failed : {}", e.getMessage());
            }
        }

        // Step 3 : Decision
        McDecisionEngine.Decision decision = decisionEngine.decide(request);
        log.info("[ISSUING] Decision — DE039={} reason={}", decision.responseCode(), decision.reason());

        // Step 4 : Build 0110
        String stan     = net.safeGet(request, 11);
        String authCode = decision.isApproved() ? buildAuthCode(stan) : null;

        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0110");
        // Echo fields
        int[] echo = {2,3,4,7,11,12,13,18,22,25,37,41,42,49};
        for (int f : echo)
            if (request.hasField(f)) response.set(f, request.getString(f));
        if (authCode != null) response.set(38, authCode);
        response.set(39, decision.responseCode());

        // Step 5 : Calculate MAC on response
        if (macVerify && hsm.getSessionZak() != null) {
            byte[] mac = hsm.calculateMac(response.pack(), macField, macFields);
            response.set(macField, mac);
            log.debug("[ISSUING] MAC calculated on 0110");
        }

        logIsoMsg("SENT", "0110 Authorization Response", response);
        net.send(out, response);
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

    // ── Helpers ──────────────────────────────────────────────

    private String buildAuthCode(String stan) {
        String code = "MC" + (stan != null ? stan : "000000");
        return code.substring(0, Math.min(6, code.length())).toUpperCase();
    }

    private String extractPin(byte[] decryptedPinBlock, String pan) {
        try {
            String pb  = hsm.bytesToHex(decryptedPinBlock);
            int pinLen = Integer.parseInt(pb.substring(1, 2), 16);
            return pb.substring(2, 2 + pinLen);
        } catch (Exception e) {
            return "????";
        }
    }

    private void logIsoMsg(String direction, String type, ISOMsg msg) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─────────────────────────────────────────────────\n");
            sb.append(String.format("│ [ISSUING] %s — %s\n", direction, type));
            sb.append("├─────────────────────────────────────────────────\n");
            try { sb.append(String.format("│ MTI                          : %s\n", msg.getMTI())); } catch (Exception ignored) {}
            if (msg.hasField(2))  sb.append(String.format("│ DE002_PAN                    : %s\n", mask(msg.getString(2))));
            if (msg.hasField(3))  sb.append(String.format("│ DE003_PROCESSING_CODE        : %s\n", msg.getString(3)));
            if (msg.hasField(4))  sb.append(String.format("│ DE004_AMOUNT                 : %s\n", msg.getString(4)));
            if (msg.hasField(7))  sb.append(String.format("│ DE007_TRANSMISSION_DATE_TIME : %s\n", msg.getString(7)));
            if (msg.hasField(11)) sb.append(String.format("│ DE011_STAN                   : %s\n", msg.getString(11)));
            if (msg.hasField(12)) sb.append(String.format("│ DE012_LOCAL_TIME             : %s\n", msg.getString(12)));
            if (msg.hasField(13)) sb.append(String.format("│ DE013_LOCAL_DATE             : %s\n", msg.getString(13)));
            if (msg.hasField(18)) sb.append(String.format("│ DE018_MCC                    : %s\n", msg.getString(18)));
            if (msg.hasField(22)) sb.append(String.format("│ DE022_POS_ENTRY_MODE         : %s\n", msg.getString(22)));
            if (msg.hasField(25)) sb.append(String.format("│ DE025_POS_CONDITION_CODE     : %s\n", msg.getString(25)));
            if (msg.hasField(37)) sb.append(String.format("│ DE037_RRN                    : %s\n", msg.getString(37)));
            if (msg.hasField(38)) sb.append(String.format("│ DE038_AUTH_CODE              : %s\n", msg.getString(38)));
            if (msg.hasField(39)) sb.append(String.format("│ DE039_RESPONSE_CODE          : %s\n", msg.getString(39)));
            if (msg.hasField(41)) sb.append(String.format("│ DE041_TERMINAL_ID            : %s\n", msg.getString(41)));
            if (msg.hasField(42)) sb.append(String.format("│ DE042_MERCHANT_ID            : %s\n", msg.getString(42)));
            if (msg.hasField(49)) sb.append(String.format("│ DE049_CURRENCY_CODE          : %s\n", msg.getString(49)));
            if (msg.hasField(52)) sb.append("│ DE052_PIN_BLOCK              : ********\n");
            if (msg.hasField(64)) sb.append(String.format("│ DE064_MAC                    : %s\n", ISOUtil.hexString(msg.getBytes(64))));
            if (msg.hasField(70)) sb.append(String.format("│ DE070_NETWORK_CODE           : %s\n", msg.getString(70)));
            sb.append("├─────────────────────────────────────────────────\n");
            sb.append(String.format("│ HEX : %s\n", ISOUtil.hexString(msg.pack())));
            sb.append("└─────────────────────────────────────────────────");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ISSUING] Error logging : {}", e.getMessage());
        }
    }

    private String mask(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }
}
