package com.staging.sg.acquirer;

import com.staging.sg.hsm.HsmKeyResult;
import com.staging.sg.hsm.ThalesHsmService;
import com.staging.sg.iso.NetworkUtil;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class McKeyExchangeManager {

    private static final Logger log = LoggerFactory.getLogger(McKeyExchangeManager.class);

    private final ThalesHsmService hsm;
    private final NetworkUtil      net;

    @Value("${mc.acquirer.mode:loopback}")          private String mode;
    @Value("${mc.acquirer.mas.host:127.0.0.1}")     private String masHost;
    @Value("${mc.acquirer.mas.port:8200}")           private int    masPort;
    @Value("${mc.acquirer.mas.timeout-seconds:30}")  private int    timeoutSeconds;

    private static final String FC_ZMK = "101";
    private static final String FC_ZPK = "102";
    private static final String FC_ZAK = "103";

    public McKeyExchangeManager(ThalesHsmService hsm, NetworkUtil net) {
        this.hsm = hsm;
        this.net = net;
    }

    public McKeyExchangeResult exchangeAllKeys() throws Exception {
        log.info("[ACQUIRING] Starting Key Exchange — ZMK{{KEK}} + ZPK{{ZMK}} + ZAK{{ZMK}}");

        String host = "mas".equalsIgnoreCase(mode) ? masHost : "127.0.0.1";
        int port    = "mas".equalsIgnoreCase(mode) ? masPort  : 8200;

        // Step 1 — ZMK under KEK
        log.info("[ACQUIRING] Step 1/3 — ZMK{{KEK}}...");
        HsmKeyResult zmk = hsm.generateZmk();
        if (!zmk.isSuccess())
            throw new Exception("ZMK generation failed : " + zmk.getErrorMessage());
        McKeyExchangeResult zmkEx = sendKeyExchange(
                zmk.getKeyEncryptedUnderKek(), zmk.getKeyCheckValue(), FC_ZMK, host, port);
        if (!zmkEx.isSuccess())
            throw new Exception("ZMK exchange failed : " + zmkEx.getMessage());
        log.info("[ACQUIRING] ZMK accepted — KCV={}", zmk.getKeyCheckValue());

        // Step 2 — ZPK under ZMK
        log.info("[ACQUIRING] Step 2/3 — ZPK{{ZMK}}...");
        HsmKeyResult zpk = hsm.generateZpk(zmk.getKeyValue());
        if (!zpk.isSuccess())
            throw new Exception("ZPK generation failed : " + zpk.getErrorMessage());
        McKeyExchangeResult zpkEx = sendKeyExchange(
                zpk.getKeyEncryptedUnderZmk(), zpk.getKeyCheckValue(), FC_ZPK, host, port);
        if (!zpkEx.isSuccess())
            throw new Exception("ZPK exchange failed : " + zpkEx.getMessage());
        log.info("[ACQUIRING] ZPK accepted — KCV={}", zpk.getKeyCheckValue());

        // Step 3 — ZAK under ZMK
        log.info("[ACQUIRING] Step 3/3 — ZAK{{ZMK}}...");
        HsmKeyResult zak = hsm.generateZak(zmk.getKeyValue());
        if (!zak.isSuccess())
            throw new Exception("ZAK generation failed : " + zak.getErrorMessage());
        McKeyExchangeResult zakEx = sendKeyExchange(
                zak.getKeyEncryptedUnderZmk(), zak.getKeyCheckValue(), FC_ZAK, host, port);
        if (!zakEx.isSuccess())
            throw new Exception("ZAK exchange failed : " + zakEx.getMessage());
        log.info("[ACQUIRING] ZAK accepted — KCV={}", zak.getKeyCheckValue());

        // Step 4 — Load session keys
        hsm.setSessionKeys(zmk.getKeyValue(), zpk.getKeyValue(), zak.getKeyValue());

        log.info("[ACQUIRING] Key Exchange completed — ZMK={} ZPK={} ZAK={}",
                zmk.getKeyCheckValue(), zpk.getKeyCheckValue(), zak.getKeyCheckValue());

        return McKeyExchangeResult.builder()
                .success(true)
                .message("Key exchange completed — ZMK{KEK} + ZPK{ZMK} + ZAK{ZMK}")
                .zmkKcv(zmk.getKeyCheckValue())
                .zpkKcv(zpk.getKeyCheckValue())
                .zakKcv(zak.getKeyCheckValue())
                .build();
    }

    private McKeyExchangeResult sendKeyExchange(byte[] encryptedKey, String kcv,
                                                  String fc, String host, int port) throws Exception {
        String stan = net.generateStan();
        String keyName = switch (fc) {
            case "101" -> "ZMK{KEK}";
            case "102" -> "ZPK{ZMK}";
            case "103" -> "ZAK{ZMK}";
            default    -> "KEY-" + fc;
        };

        ISOMsg request = new ISOMsg();
        request.setPackager(net.getPackager());
        request.setMTI("0820");
        request.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        request.set(11, stan);
        request.set(53, buildDe53(kcv, encryptedKey));
        request.set(70, fc);

        logIsoMsg("SENT", "0820 Key-Exchange " + keyName, request, kcv);

        ISOMsg response = net.sendAndReceive(request, host, port, timeoutSeconds);

        logIsoMsg("RECEIVED", "0830 Key-Exchange Response " + keyName, response, null);

        String rc = net.safeGet(response, 39);
        boolean success = "00".equals(rc);

        log.info("[ACQUIRING] Key-Exchange {} — DE39={} success={}", keyName, rc, success);

        return McKeyExchangeResult.builder()
                .success(success)
                .message(success ? "Accepted" : "Rejected DE39=" + rc)
                .requestHex(ISOUtil.hexString(request.pack()))
                .responseHex(ISOUtil.hexString(response.pack()))
                .build();
    }

    private String buildDe53(String kcv, byte[] encryptedKey) {
        String kcvSafe = kcv != null ? kcv : "000000";
        String keyHex  = encryptedKey != null ? hsm.bytesToHex(encryptedKey) : "00000000000000000000000000000000";
        return kcvSafe + keyHex;
    }

    private void logIsoMsg(String direction, String type, ISOMsg msg, String kcv) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─────────────────────────────────────────────────\n");
            sb.append(String.format("│ [ACQUIRING] %s — %s\n", direction, type));
            sb.append("├─────────────────────────────────────────────────\n");
            try { sb.append(String.format("│ MTI                   : %s\n", msg.getMTI())); } catch (Exception ignored) {}
            if (msg.hasField(7))  sb.append(String.format("│ DE007 Date/Time       : %s\n", msg.getString(7)));
            if (msg.hasField(11)) sb.append(String.format("│ DE011 STAN            : %s\n", msg.getString(11)));
            if (msg.hasField(39)) sb.append(String.format("│ DE039 Response Code   : %s\n", msg.getString(39)));
            if (msg.hasField(53)) sb.append(String.format("│ DE053 Security Info   : %s...\n",
                    msg.getString(53).substring(0, Math.min(12, msg.getString(53).length()))));
            if (msg.hasField(70)) sb.append(String.format("│ DE070 Network Code    : %s\n", msg.getString(70)));
            if (kcv != null)      sb.append(String.format("│ KCV                   : %s\n", kcv));
            sb.append("├─────────────────────────────────────────────────\n");
            sb.append(String.format("│ HEX : %s\n", ISOUtil.hexString(msg.pack())));
            sb.append("└─────────────────────────────────────────────────");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ACQUIRING] Error logging ISO message : {}", e.getMessage());
        }
    }
}
