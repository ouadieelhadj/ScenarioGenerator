package com.staging.sg.acquirer.acquirer;

import com.staging.sg.common.hsm.ThalesHsmService;
import com.staging.sg.common.iso.McPackager;
import com.staging.sg.common.iso.NetworkUtil;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
public class McAdviceManager {

    private static final Logger log = LoggerFactory.getLogger(McAdviceManager.class);

    private final McPackager       packager;
    private final ThalesHsmService hsm;
    private final NetworkUtil      networkUtil;

    @Value("${mc.acquirer.mas.host:127.0.0.1}")
    private String masHost;

    @Value("${mc.acquirer.mas.port:8200}")
    private int masPort;

    @Value("${mc.acquirer.test-bin:555555}")
    private String testBin;

    @Value("${mc.acquirer.defaults.DE032_ACQUIRING_BIN:411111}")
    private String acquiringBin;

    @Value("${mc.acquirer.defaults.DE041_TERMINAL_ID:MCTERM01}")
    private String terminalId;

    @Value("${mc.acquirer.defaults.DE042_MERCHANT_ID:MCMERCHANT0001 }")
    private String merchantId;

    @Value("${mc.acquirer.defaults.DE049_CURRENCY_CODE:978}")
    private String defaultCurrency;

    @Value("${mc.security.mac-fields:2,3,4,7,11,12,13,18,22,37,41,42,49,64}")
    private String macFields;

    private final Random random = new Random();

    public McAdviceManager(McPackager packager,
                           ThalesHsmService hsm,
                           NetworkUtil networkUtil) {
        this.packager    = packager;
        this.hsm         = hsm;
        this.networkUtil = networkUtil;
    }

    public McAdviceResult sendAdvice(McAdviceRequest request) {
        McAdviceResult result = new McAdviceResult();
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0120");

            String pan = request.getDE002_PAN() != null
                    ? request.getDE002_PAN() : generatePan();
            msg.set(2, pan);
            result.setDE002_PAN(maskPan(pan));

            msg.set(3, request.getDE003_PROCESSING_CODE() != null
                    ? request.getDE003_PROCESSING_CODE() : "000000");

            long amount = request.getDE004_AMOUNT() != null
                    ? request.getDE004_AMOUNT() : 5000L;
            msg.set(4, String.format("%012d", amount));

            msg.set(7, LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMddHHmmss")));

            String stan = String.format("%06d", random.nextInt(999999));
            msg.set(11, stan);

            msg.set(12, LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("HHmmss")));

            msg.set(13, LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMdd")));

            msg.set(18, request.getDE018_MCC() != null
                    ? request.getDE018_MCC() : "5411");

            msg.set(22, request.getDE022_POS_ENTRY_MODE() != null
                    ? request.getDE022_POS_ENTRY_MODE() : "051");

            msg.set(25, "00");
            msg.set(32, acquiringBin);

            String rrn = request.getDE037_RETRIEVAL_REF() != null
                    ? request.getDE037_RETRIEVAL_REF()
                    : String.format("%012d", random.nextInt(999999999));
            msg.set(37, rrn);
            result.setDE037_RETRIEVAL_REF(rrn);

            if (request.getDE038_AUTH_CODE() != null)
                msg.set(38, request.getDE038_AUTH_CODE());

            msg.set(39, request.getDE039_RESPONSE_CODE() != null
                    ? request.getDE039_RESPONSE_CODE() : "00");

            msg.set(41, terminalId);
            msg.set(42, merchantId);

            msg.set(49, request.getDE049_CURRENCY_CODE() != null
                    ? request.getDE049_CURRENCY_CODE() : defaultCurrency);

            msg.set(60, request.getDE060_ADVICE_REASON() != null
                    ? request.getDE060_ADVICE_REASON() : "191");

            // MAC
            byte[] packed0 = msg.pack();
            byte[] mac = hsm.calculateMac(packed0, 64, macFields);
            msg.set(64, mac);

            byte[] packed = msg.pack();
            result.setRequestHex(bytesToHex(packed));

            log.info("\n┌─────────────────────────────────────────────────");
            log.info("│ [ACQUIRING] SENT — 0120 Authorization Advice");
            log.info("├─────────────────────────────────────────────────");
            log.info("│ DE002 PAN   : {}", maskPan(pan));
            log.info("│ DE004 Amount: {}", amount);
            log.info("│ DE011 STAN  : {}", stan);
            log.info("│ DE060 Reason: {}", request.getDE060_ADVICE_REASON());
            log.info("└─────────────────────────────────────────────────");

            ISOMsg response = networkUtil.sendAndReceive(msg, masHost, masPort, 30);
            if (response == null) {
                result.setError("No response received");
                result.setAccepted(false);
                return result;
            }

            byte[] respPacked = response.pack();
            result.setResponseHex(bytesToHex(respPacked));

            String de39 = response.getString(39);
            result.setDE039_RESPONSE_CODE(de39);
            result.setAccepted("00".equals(de39));

            log.info("\n┌─────────────────────────────────────────────────");
            log.info("│ [ACQUIRING] RECEIVED — 0130 Advice Response");
            log.info("├─────────────────────────────────────────────────");
            log.info("│ DE039 Response Code : {}", de39);
            log.info("│ Accepted            : {}", result.isAccepted());
            log.info("└─────────────────────────────────────────────────");

        } catch (Exception e) {
            log.error("[ADVICE] Error : {}", e.getMessage(), e);
            result.setError(e.getMessage());
            result.setAccepted(false);
        }
        return result;
    }

    private String generatePan() {
        return testBin + String.format("%010d", random.nextInt(999999999));
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
