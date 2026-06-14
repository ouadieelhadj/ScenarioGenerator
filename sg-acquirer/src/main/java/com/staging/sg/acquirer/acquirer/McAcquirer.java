package com.staging.sg.acquirer.acquirer;

import com.staging.sg.common.hsm.ThalesHsmService;
import com.staging.sg.common.iso.NetworkUtil;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Mastercard Acquirer — sends 0100 Authorization Request.
 *
 * Flow :
 *   1. Apply defaults from config
 *   2. Generate PAN (if not provided)
 *   3. Auto-generate DE007/011/012/013/037
 *   4. Encrypt PIN under ZPK (HSM) → DE052
 *   5. Build ISO message
 *   6. Calculate MAC under ZAK (HSM) → DE064
 *   7. Send to Issuer
 *   8. Receive 0110 response
 */
@Service
public class McAcquirer {

    private static final Logger log = LoggerFactory.getLogger(McAcquirer.class);

    private final NetworkUtil      net;
    private final ThalesHsmService hsm;

    @Value("${mc.acquirer.mas.host:127.0.0.1}")     private String  masHost;
    @Value("${mc.acquirer.mas.port:8200}")           private int     masPort;
    @Value("${mc.acquirer.mas.timeout-seconds:30}")  private int     timeoutSeconds;
    @Value("${mc.acquirer.test-bin:555555}")         private String  testBin;
    @Value("${mc.acquirer.pan-length:16}")           private int     panLength;

    // Defaults from config
    @Value("${mc.acquirer.defaults.DE003_PROCESSING_CODE:000000}")    private String defProcessingCode;
    @Value("${mc.acquirer.defaults.DE018_MCC:5999}")                  private String defMcc;
    @Value("${mc.acquirer.defaults.DE022_POS_ENTRY_MODE:051}")        private String defPosEntryMode;
    @Value("${mc.acquirer.defaults.DE025_POS_CONDITION_CODE:00}")     private String defPosConditionCode;
    @Value("${mc.acquirer.defaults.DE032_ACQUIRING_BIN:411111}")      private String defAcquiringBin;
    @Value("${mc.acquirer.defaults.DE033_FORWARDING_BIN:555555}")     private String defForwardingBin;
    @Value("${mc.acquirer.defaults.DE041_TERMINAL_ID:MCTERM01}")      private String defTerminalId;
    @Value("${mc.acquirer.defaults.DE042_MERCHANT_ID:MCMERCHANT0001}") private String defMerchantId;
    @Value("${mc.acquirer.defaults.DE043_MERCHANT_NAME:DEFAULT MERCHANT PARIS FR}") private String defMerchantName;
    @Value("${mc.acquirer.defaults.DE049_CURRENCY_CODE:978}")         private String defCurrencyCode;
    @Value("${mc.acquirer.defaults.DE052_PIN:1234}")                  private String defPin;

    // Security config
    @Value("${mc.security.mac-enabled:true}")   private boolean macEnabled;
    @Value("${mc.security.mac-fields:2,3,4,7,11,12,13,18,22,37,41,42,49,64}") private String macFields;
    @Value("${mc.security.mac-field:64}")       private int macField;
    @Value("${mc.security.pin-enabled:true}")   private boolean pinEnabled;

    public McAcquirer(NetworkUtil net, ThalesHsmService hsm) {
        this.net = net;
        this.hsm = hsm;
    }

    public McAuthResult authorize(McAuthRequest request) throws Exception {
        long seed = request.getSeed() != null
                ? request.getSeed() : System.currentTimeMillis();

        // Step 1 : Apply defaults
        applyDefaults(request);

        // Step 2 : PAN
        String pan = request.getDE002_PAN() != null
                ? request.getDE002_PAN()
                : generatePan(seed);
        log.info("[ACQUIRING] 0100 — PAN={} amount={}", mask(pan), request.getDE004_AMOUNT());

        // Step 3 : Auto-generated fields
        String stan  = generateStan();
        String rrn   = generateRrn();
        String lTime = new SimpleDateFormat("HHmmss").format(new Date());
        String lDate = new SimpleDateFormat("MMdd").format(new Date());
        String txDt  = new SimpleDateFormat("MMddHHmmss").format(new Date());

        // Step 4 : PIN Block (encrypt under ZPK)
        byte[] pinBlock = null;
        if (pinEnabled && request.getDE052_PIN() != null) {
            pinBlock = encryptPin(request.getDE052_PIN(), pan);
            log.debug("[ACQUIRING] PIN Block calculated under ZPK");
        }

        // Step 5 : Build ISOMsg
        ISOMsg isoRequest = new ISOMsg();
        isoRequest.setPackager(net.getPackager());
        isoRequest.setMTI("0100");
        isoRequest.set(2,  pan);
        isoRequest.set(3,  request.getDE003_PROCESSING_CODE());
        isoRequest.set(4,  String.format("%012d", request.getDE004_AMOUNT()));
        isoRequest.set(7,  txDt);
        isoRequest.set(11, stan);
        isoRequest.set(12, lTime);
        isoRequest.set(13, lDate);
        isoRequest.set(18, request.getDE018_MCC());
        isoRequest.set(22, request.getDE022_POS_ENTRY_MODE());
        isoRequest.set(25, request.getDE025_POS_CONDITION_CODE());
        isoRequest.set(32, request.getDE032_ACQUIRING_BIN());
        isoRequest.set(33, request.getDE033_FORWARDING_BIN());
        isoRequest.set(37, rrn);
        isoRequest.set(41, String.format("%-8s",  request.getDE041_TERMINAL_ID()).substring(0, 8));
        isoRequest.set(42, String.format("%-15s", request.getDE042_MERCHANT_ID()).substring(0, 15));
        isoRequest.set(43, String.format("%-40s", request.getDE043_MERCHANT_NAME()).substring(0, 40));
        isoRequest.set(49, request.getDE049_CURRENCY_CODE());
        if (pinBlock != null) isoRequest.set(52, pinBlock);

        // Step 6 : MAC (calculate under ZAK)
        String macHex = null;
        if (macEnabled && hsm.getSessionZak() != null) {
            byte[] msgBytes = isoRequest.pack();
            byte[] mac = hsm.calculateMac(msgBytes, macField, macFields);
            isoRequest.set(macField, mac);
            macHex = hsm.bytesToHex(mac);
            log.debug("[ACQUIRING] MAC calculated under ZAK — {}", macHex);
        }

        // Step 7 : Log and Send
        logIsoMsg("SENT", "0100 Authorization", isoRequest);
        ISOMsg isoResponse = net.sendAndReceive(isoRequest, masHost, masPort, timeoutSeconds);
        logIsoMsg("RECEIVED", "0110 Authorization Response", isoResponse);

        // Step 8 : Result
        String rc       = net.safeGet(isoResponse, 39);
        String authCode = net.safeGet(isoResponse, 38);
        boolean approved = "00".equals(rc);

        log.info("[ACQUIRING] 0110 — DE039={} DE038={} approved={}", rc, authCode, approved);

        return McAuthResult.builder()
                .mode("ACQUIRING").seed(seed).host(masHost).port(masPort)
                .approved(approved)
                .responseLabel(label(rc))
                .DE002_PAN(mask(pan))
                .DE003_PROCESSING_CODE(request.getDE003_PROCESSING_CODE())
                .DE004_AMOUNT(String.format("%012d", request.getDE004_AMOUNT()))
                .DE007_TRANSMISSION_DATE_TIME(txDt)
                .DE011_STAN(stan)
                .DE012_LOCAL_TIME(lTime)
                .DE013_LOCAL_DATE(lDate)
                .DE018_MCC(request.getDE018_MCC())
                .DE022_POS_ENTRY_MODE(request.getDE022_POS_ENTRY_MODE())
                .DE025_POS_CONDITION_CODE(request.getDE025_POS_CONDITION_CODE())
                .DE032_ACQUIRING_BIN(request.getDE032_ACQUIRING_BIN())
                .DE033_FORWARDING_BIN(request.getDE033_FORWARDING_BIN())
                .DE037_RRN(rrn)
                .DE041_TERMINAL_ID(request.getDE041_TERMINAL_ID())
                .DE042_MERCHANT_ID(request.getDE042_MERCHANT_ID())
                .DE043_MERCHANT_NAME(request.getDE043_MERCHANT_NAME())
                .DE049_CURRENCY_CODE(request.getDE049_CURRENCY_CODE())
                .DE052_PIN_BLOCK(pinBlock != null ? hsm.bytesToHex(pinBlock) : null)
                .DE064_MAC(macHex)
                .DE038_AUTH_CODE(authCode)
                .DE039_RESPONSE_CODE(rc)
                .requestHex(ISOUtil.hexString(isoRequest.pack()))
                .responseHex(ISOUtil.hexString(isoResponse.pack()))
                .requestFields(buildFieldMap(isoRequest, true))
                .responseFields(buildFieldMap(isoResponse, false))
                .build();
    }

    // ── Apply defaults from config ────────────────────────────

    private void applyDefaults(McAuthRequest req) {
        if (isEmpty(req.getDE003_PROCESSING_CODE()))    req.setDE003_PROCESSING_CODE(defProcessingCode);
        if (isEmpty(req.getDE018_MCC()))                req.setDE018_MCC(defMcc);
        if (isEmpty(req.getDE022_POS_ENTRY_MODE()))     req.setDE022_POS_ENTRY_MODE(defPosEntryMode);
        if (isEmpty(req.getDE025_POS_CONDITION_CODE())) req.setDE025_POS_CONDITION_CODE(defPosConditionCode);
        if (isEmpty(req.getDE032_ACQUIRING_BIN()))      req.setDE032_ACQUIRING_BIN(defAcquiringBin);
        if (isEmpty(req.getDE033_FORWARDING_BIN()))     req.setDE033_FORWARDING_BIN(defForwardingBin);
        if (isEmpty(req.getDE041_TERMINAL_ID()))        req.setDE041_TERMINAL_ID(defTerminalId);
        if (isEmpty(req.getDE042_MERCHANT_ID()))        req.setDE042_MERCHANT_ID(defMerchantId);
        if (isEmpty(req.getDE043_MERCHANT_NAME()))      req.setDE043_MERCHANT_NAME(defMerchantName);
        if (isEmpty(req.getDE049_CURRENCY_CODE()))      req.setDE049_CURRENCY_CODE(defCurrencyCode);
        if (isEmpty(req.getDE052_PIN()))                req.setDE052_PIN(defPin);
    }

    // ── PIN encryption ────────────────────────────────────────

    private byte[] encryptPin(String pin, String pan) {
        try {
            // ISO Format 0 PIN Block
            // PIN Block = PIN XOR PAN Block
            String pinBlock = "0" + pin.length() + pin + "F".repeat(14 - pin.length());
            String panBlock = "0000" + pan.substring(3, 15);
            byte[] pb = hexToBytes(pinBlock);
            byte[] panb = hexToBytes(panBlock);
            byte[] xored = new byte[8];
            for (int i = 0; i < 8; i++) xored[i] = (byte)(pb[i] ^ panb[i]);
            // Encrypt under ZPK
            if (hsm.getSessionZpk() != null) {
                return hsm.encryptPinBlock(xored, hsm.getSessionZpk());
            }
            return xored;
        } catch (Exception e) {
            log.error("[ACQUIRING] PIN encryption failed : {}", e.getMessage());
            return new byte[8];
        }
    }

    // ── PAN generation ────────────────────────────────────────

    private String generatePan(long seed) {
        Random rng = new Random(seed);
        StringBuilder pan = new StringBuilder(testBin);
        while (pan.length() < panLength - 1)
            pan.append(Math.abs(rng.nextInt()) % 10);
        pan.append(luhnCheckDigit(pan.toString()));
        return pan.toString();
    }

    private int luhnCheckDigit(String partial) {
        String padded = partial + "0";
        int sum = 0; boolean alt = false;
        for (int i = padded.length() - 1; i >= 0; i--) {
            int n = padded.charAt(i) - '0';
            if (alt) { n *= 2; if (n > 9) n -= 9; }
            sum += n; alt = !alt;
        }
        return (10 - (sum % 10)) % 10;
    }

    // ── STAN / RRN generation ─────────────────────────────────

    private String generateStan() {
        return String.format("%06d", Math.abs(new Random().nextInt()) % 1_000_000);
    }

    private String generateRrn() {
        Random rng = new Random();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder rrn = new StringBuilder();
        for (int i = 0; i < 12; i++)
            rrn.append(chars.charAt(Math.abs(rng.nextInt()) % chars.length()));
        return rrn.toString();
    }

    // ── Log ──────────────────────────────────────────────────

    private void logIsoMsg(String direction, String type, ISOMsg msg) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─────────────────────────────────────────────────\n");
            sb.append(String.format("│ [ACQUIRING] %s — %s\n", direction, type));
            sb.append("├─────────────────────────────────────────────────\n");
            try { sb.append(String.format("│ MTI                          : %s\n", msg.getMTI())); } catch (Exception ignored) {}
            if (msg.hasField(2))  sb.append(String.format("│ DE002_PAN                    : %s\n", mask(msg.getString(2))));
            if (msg.hasField(3))  sb.append(String.format("│ DE003_PROCESSING_CODE        : %s\n", msg.getString(3)));
            if (msg.hasField(4))  sb.append(String.format("│ DE004_AMOUNT                 : %s\n", msg.getString(4)));
            if (msg.hasField(7))  sb.append(String.format("│ DE007_TRANSMISSION_DATE_TIME : %s [AUTO]\n", msg.getString(7)));
            if (msg.hasField(11)) sb.append(String.format("│ DE011_STAN                   : %s [AUTO]\n", msg.getString(11)));
            if (msg.hasField(12)) sb.append(String.format("│ DE012_LOCAL_TIME             : %s [AUTO]\n", msg.getString(12)));
            if (msg.hasField(13)) sb.append(String.format("│ DE013_LOCAL_DATE             : %s [AUTO]\n", msg.getString(13)));
            if (msg.hasField(18)) sb.append(String.format("│ DE018_MCC                    : %s\n", msg.getString(18)));
            if (msg.hasField(22)) sb.append(String.format("│ DE022_POS_ENTRY_MODE         : %s\n", msg.getString(22)));
            if (msg.hasField(25)) sb.append(String.format("│ DE025_POS_CONDITION_CODE     : %s\n", msg.getString(25)));
            if (msg.hasField(32)) sb.append(String.format("│ DE032_ACQUIRING_BIN          : %s\n", msg.getString(32)));
            if (msg.hasField(33)) sb.append(String.format("│ DE033_FORWARDING_BIN         : %s\n", msg.getString(33)));
            if (msg.hasField(37)) sb.append(String.format("│ DE037_RRN                    : %s [AUTO]\n", msg.getString(37)));
            if (msg.hasField(38)) sb.append(String.format("│ DE038_AUTH_CODE              : %s\n", msg.getString(38)));
            if (msg.hasField(39)) sb.append(String.format("│ DE039_RESPONSE_CODE          : %s\n", msg.getString(39)));
            if (msg.hasField(41)) sb.append(String.format("│ DE041_TERMINAL_ID            : %s\n", msg.getString(41)));
            if (msg.hasField(42)) sb.append(String.format("│ DE042_MERCHANT_ID            : %s\n", msg.getString(42)));
            if (msg.hasField(43)) sb.append(String.format("│ DE043_MERCHANT_NAME          : %s\n", msg.getString(43)));
            if (msg.hasField(49)) sb.append(String.format("│ DE049_CURRENCY_CODE          : %s\n", msg.getString(49)));
            if (msg.hasField(52)) sb.append("│ DE052_PIN_BLOCK              : ********\n");
            if (msg.hasField(64)) sb.append(String.format("│ DE064_MAC                    : %s\n", ISOUtil.hexString(msg.getBytes(64))));
            sb.append("├─────────────────────────────────────────────────\n");
            sb.append(String.format("│ HEX : %s\n", ISOUtil.hexString(msg.pack())));
            sb.append("└─────────────────────────────────────────────────");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ACQUIRING] Error logging : {}", e.getMessage());
        }
    }

    private Map<String, String> buildFieldMap(ISOMsg msg, boolean isRequest) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            map.put("MTI", msg.getMTI());
            int[] fields = {2,3,4,7,11,12,13,18,22,25,32,33,37,38,39,41,42,43,49,52,64};
            String[] names = {
                "DE002_PAN","DE003_PROCESSING_CODE","DE004_AMOUNT",
                "DE007_TRANSMISSION_DATE_TIME","DE011_STAN",
                "DE012_LOCAL_TIME","DE013_LOCAL_DATE","DE018_MCC",
                "DE022_POS_ENTRY_MODE","DE025_POS_CONDITION_CODE",
                "DE032_ACQUIRING_BIN","DE033_FORWARDING_BIN","DE037_RRN",
                "DE038_AUTH_CODE","DE039_RESPONSE_CODE","DE041_TERMINAL_ID",
                "DE042_MERCHANT_ID","DE043_MERCHANT_NAME","DE049_CURRENCY_CODE",
                "DE052_PIN_BLOCK","DE064_MAC"
            };
            for (int i = 0; i < fields.length; i++) {
                int f = fields[i];
                if (!msg.hasField(f)) continue;
                String val;
                if (f == 2)       val = mask(msg.getString(f));
                else if (f == 52) val = "********";
                else if (f == 64) val = ISOUtil.hexString(msg.getBytes(f));
                else              val = msg.getString(f);
                if (isRequest && (f==7||f==11||f==12||f==13||f==37))
                    val += " [AUTO]";
                map.put(names[i], val);
            }
        } catch (Exception ignored) {}
        return map;
    }

    // ── Helpers ──────────────────────────────────────────────

    private boolean isEmpty(String s) { return s == null || s.isBlank(); }

    private String mask(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i/2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        return data;
    }

    private String label(String rc) {
        return switch (rc != null ? rc : "") {
            case "00" -> "Approved";
            case "05" -> "Do not honor";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
            case "91" -> "Issuer unavailable";
            default   -> "Code : " + rc;
        };
    }
}
