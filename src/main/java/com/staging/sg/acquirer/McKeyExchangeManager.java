package com.staging.sg.acquirer;

import com.staging.sg.hsm.HsmKeyResult;
import com.staging.sg.hsm.ThalesHsmService;
import com.staging.sg.iso.McPackager;
import com.staging.sg.iso.NetworkUtil;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Key Exchange Manager — Mastercard Acquiring.
 *
 * Sequence :
 *   1. Generate ZMK → encrypt under KEK → send 0820 FC=101
 *   2. Generate ZPK → encrypt under ZMK → send 0820 FC=102
 *   3. Generate ZAK → encrypt under ZMK → send 0820 FC=103
 *
 * KEK shared physically between Acquirer and Issuer.
 */
@Service
public class McKeyExchangeManager {

    private static final Logger log = LoggerFactory.getLogger(McKeyExchangeManager.class);

    private final ThalesHsmService hsm;
    private final NetworkUtil      net;

    @Value("${mc.acquirer.mode:loopback}")       private String mode;
    @Value("${mc.acquirer.mas.host:127.0.0.1}")  private String masHost;
    @Value("${mc.acquirer.mas.port:8200}")        private int    masPort;
    @Value("${mc.acquirer.mas.timeout-seconds:30}") private int timeoutSeconds;

    private static final String FC_ZMK = "101";
    private static final String FC_ZPK = "102";
    private static final String FC_ZAK = "103";

    public McKeyExchangeManager(ThalesHsmService hsm, NetworkUtil net) {
        this.hsm = hsm;
        this.net = net;
    }

    public McKeyExchangeResult exchangeAllKeys() throws Exception {
        log.info("[KEY-EXCHANGE] Starting — ZMK{{KEK}} + ZPK{{ZMK}} + ZAK{{ZMK}}");

        String host = "mas".equalsIgnoreCase(mode) ? masHost : "127.0.0.1";
        int port    = "mas".equalsIgnoreCase(mode) ? masPort  : 8200;

        // Step 1 — ZMK encrypted under KEK
        log.info("[KEY-EXCHANGE] Step 1/3 — ZMK{{KEK}}...");
        HsmKeyResult zmk = hsm.generateZmk();
        if (!zmk.isSuccess())
            throw new Exception("ZMK generation failed : " + zmk.getErrorMessage());

        McKeyExchangeResult zmkEx = sendKeyExchange(
                zmk.getKeyEncryptedUnderKek(),
                zmk.getKeyCheckValue(), FC_ZMK, host, port);
        if (!zmkEx.isSuccess())
            throw new Exception("ZMK exchange failed : " + zmkEx.getMessage());
        log.info("[KEY-EXCHANGE] ZMK accepted — KCV={}", zmk.getKeyCheckValue());

        // Step 2 — ZPK encrypted under ZMK
        log.info("[KEY-EXCHANGE] Step 2/3 — ZPK{{ZMK}}...");
        HsmKeyResult zpk = hsm.generateZpk(zmk.getKeyValue());
        if (!zpk.isSuccess())
            throw new Exception("ZPK generation failed : " + zpk.getErrorMessage());

        McKeyExchangeResult zpkEx = sendKeyExchange(
                zpk.getKeyEncryptedUnderZmk(),
                zpk.getKeyCheckValue(), FC_ZPK, host, port);
        if (!zpkEx.isSuccess())
            throw new Exception("ZPK exchange failed : " + zpkEx.getMessage());
        log.info("[KEY-EXCHANGE] ZPK accepted — KCV={}", zpk.getKeyCheckValue());

        // Step 3 — ZAK encrypted under ZMK
        log.info("[KEY-EXCHANGE] Step 3/3 — ZAK{{ZMK}}...");
        HsmKeyResult zak = hsm.generateZak(zmk.getKeyValue());
        if (!zak.isSuccess())
            throw new Exception("ZAK generation failed : " + zak.getErrorMessage());

        McKeyExchangeResult zakEx = sendKeyExchange(
                zak.getKeyEncryptedUnderZmk(),
                zak.getKeyCheckValue(), FC_ZAK, host, port);
        if (!zakEx.isSuccess())
            throw new Exception("ZAK exchange failed : " + zakEx.getMessage());
        log.info("[KEY-EXCHANGE] ZAK accepted — KCV={}", zak.getKeyCheckValue());

        // Step 4 — Load session keys
        hsm.setSessionKeys(zmk.getKeyValue(), zpk.getKeyValue(), zak.getKeyValue());

        log.info("[KEY-EXCHANGE] Completed — ZMK={} ZPK={} ZAK={}",
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
                                                  String functionCode,
                                                  String host, int port) throws Exception {
        String stan = net.generateStan();

        ISOMsg request = new ISOMsg();
        request.setPackager(net.getPackager());
        request.setMTI("0820");
        request.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        request.set(11, stan);
        request.set(53, buildDe53(kcv, encryptedKey));
        request.set(70, functionCode);

        log.info("[KEY-EXCHANGE] Sending 0820 FC={} KCV={} to {}:{}",
                functionCode, kcv, host, port);

        ISOMsg response = net.sendAndReceive(request, host, port, timeoutSeconds);

        String responseCode = net.safeGet(response, 39);
        boolean success = "00".equals(responseCode);

        log.info("[KEY-EXCHANGE] 0830 received — FC={} DE39={}", functionCode, responseCode);

        return McKeyExchangeResult.builder()
                .success(success)
                .message(success ? "Accepted" : "Rejected DE39=" + responseCode)
                .requestHex(ISOUtil.hexString(request.pack()))
                .responseHex(ISOUtil.hexString(response.pack()))
                .build();
    }

    private String buildDe53(String kcv, byte[] encryptedKey) {
        String kcvSafe = kcv != null ? kcv : "000000";
        String keyHex  = encryptedKey != null ? hsm.bytesToHex(encryptedKey) : "00000000000000000000000000000000";
        return kcvSafe + keyHex;
    }
}
