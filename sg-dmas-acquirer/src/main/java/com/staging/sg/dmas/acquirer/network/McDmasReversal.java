package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.common.iso.DmasNetworkUtil;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construction et envoi des Reversal Request/0400 côté RESEAU (acquéreur).
 *
 * Le 0400 reprend les DE de la transaction 0100 originale (DE2,3,4,7,11,18,32,49)
 * + ajoute le DE90 (Original Data Elements) qui identifie la transaction à annuler :
 *   DE90 = [MTI orig 4][STAN orig 6][DE7 orig 10][DE32 orig 11][DE33 orig 11] = n-42
 */
@Service
public class McDmasReversal {

    private static final Logger log = LoggerFactory.getLogger(McDmasReversal.class);

    private final DmasNetworkUtil net;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;
    @Value("${dmas.acquirer-id:111111}")    private String acquirerId;
    @Value("${dmas.default-currency:840}")  private String defaultCurrency;
    @Value("${dmas.default-mcc:5999}")      private String defaultMcc;

    public McDmasReversal(DmasNetworkUtil net) {
        this.net = net;
    }

    /**
     * Construit et envoie un 0400 pour annuler une transaction 0100.
     * @param pan            PAN original
     * @param amount         montant original n-12
     * @param processingCode DE3 original
     * @param originalStan   DE11 de la transaction à annuler
     * @param originalDt     DE7 de la transaction à annuler (MMDDhhmmss)
     */
    public Map<String,Object> sendReversal(String pan, String amount, String processingCode,
                                           String originalStan, String originalDt) throws Exception {
        // Construire le DE90 = [MTI 0100][STAN orig 6][DE7 orig 10][DE32 11][DE33 11]
        String de90 = buildDe90("0100", originalStan, originalDt, acquirerId, "00000000000");

        String stan = net.generateStan();
        String dtUtc = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg msg = new ISOMsg();
        msg.setPackager(net.getPackager());
        msg.setMTI("0400");
        msg.set(2,  pan);
        msg.set(3,  processingCode != null ? processingCode : "000000");
        msg.set(4,  amount);
        msg.set(7,  dtUtc);
        msg.set(11, stan);
        msg.set(18, defaultMcc);
        msg.set(32, acquirerId);
        msg.set(49, defaultCurrency);
        msg.set(90, de90);

        // LOG détaillé
        log.info("[DMAS-REV] === 0400 Reversal ===");
        log.info("[DMAS-REV] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-REV] DE3  Processing Code  = {}", processingCode);
        log.info("[DMAS-REV] DE4  Amount           = {}", amount);
        log.info("[DMAS-REV] DE7  Transmission DT   = {}", dtUtc);
        log.info("[DMAS-REV] DE11 STAN (nouveau)   = {}", stan);
        log.info("[DMAS-REV] DE32 Acquiring Inst   = {}", acquirerId);
        log.info("[DMAS-REV] DE49 Currency         = {}", defaultCurrency);
        log.info("[DMAS-REV] DE90 Original Data    = {}", de90);
        log.info("[DMAS-REV]   -> MTI orig={} STAN orig={} DT orig={}", "0100", originalStan, originalDt);

        String reqHex = ISOUtil.hexString(msg.pack());
        ISOMsg resp = net.sendAndReceive(msg, issuerHost, issuerPort, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);

        log.info("[DMAS-REV] <- 0410 DE39={} ok={}", rc, ok);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("mti_response", resp.getMTI());
        r.put("de090_original", de90);
        r.put("original_stan", originalStan);
        r.put("de039_response_code", rc);
        r.put("reversed", ok);
        r.put("request_hex", reqHex);
        r.put("response_hex", ISOUtil.hexString(resp.pack()));
        return r;
    }

    /** DE90 = [MTI 4][STAN 6][DT 10][DE32 11][DE33 11] = 42 chiffres, right-justified leading zeros. */
    private String buildDe90(String mti, String stan, String dt, String de32, String de33) {
        return pad(mti, 4) + pad(stan, 6) + pad(dt, 10) + padLeft(de32, 11) + padLeft(de33, 11);
    }

    private String pad(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append('0');
        return sb.toString();
    }

    private String padLeft(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(s.length() - n);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n - s.length()) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
