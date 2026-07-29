package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.entity.McDmasMemberTransaction;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import com.staging.sg.common.repository.McDmasMemberTransactionRepository;
import com.staging.sg.common.service.McDmasAuthorizationJournalMapper;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authorization Advice/0120 côté RESEAU (acquéreur).
 *
 * Deux modes :
 *  - simple     : notification d'une transaction déjà autorisée offline (la banque enregistre + débite)
 *  - completion : finalise une preauth (référence l'originale via DE90, ajuste le solde)
 *
 * Le réseau insère DE48 subelement 15 (Authorization System Advice Date and Time) : [15][10][MMDDhhmmss].
 */
@Service
public class McDmasAdvice {

    private static final Logger log = LoggerFactory.getLogger(McDmasAdvice.class);

    private final McDmasNetworkUtil net;
    private final HsmService hsm;
    private final McDmasMemberKeyRepository acqKeyRepo;
    private final McDmasMemberTransactionRepository transactionRepo;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;
    @Value("${dmas.member-group-id:TESTGRP01}") private String memberGroup;
    @Value("${dmas.acquirer-id:111111}")    private String acquirerId;
    @Value("${dmas.default-currency:840}")  private String defaultCurrency;
    @Value("${dmas.default-mcc:5999}")      private String defaultMcc;

    public McDmasAdvice(McDmasNetworkUtil net, HsmService hsm,
                        McDmasMemberKeyRepository acqKeyRepo,
                        McDmasMemberTransactionRepository transactionRepo) {
        this.net = net;
        this.hsm = hsm;
        this.acqKeyRepo = acqKeyRepo;
        this.transactionRepo = transactionRepo;
    }

    /** Advice simple : notification d'une transaction offline. */
    public Map<String,Object> sendAdvice(String pan, String amount, String processingCode,
                                         String pin, String terminalId, String acceptorId) throws Exception {
        return send(pan, amount, processingCode != null ? processingCode : "000000",
                pin, terminalId, acceptorId, null, null, "0", "ADVICE");
    }

    /** Completion : finalise une preauth (référence l'originale via DE90, DE61 sf7=4). */
    public Map<String,Object> sendCompletion(String pan, String finalAmount, String processingCode,
                                             String originalStan, String originalDt) throws Exception {
        return send(pan, finalAmount, processingCode != null ? processingCode : "000000",
                null, null, null, originalStan, originalDt, "4", "COMPLETION");
    }

    private Map<String,Object> send(String pan, String amount, String processingCode,
                                    String pin, String terminalId, String acceptorId,
                                    String originalStan, String originalDt, String posStatus,
                                    String label) throws Exception {
        String stan = net.generateStan();
        String dtUtc = new SimpleDateFormat("MMddHHmmss").format(new Date());

        // DE48 subelement 15 : [15][10][MMDDhhmmss] inséré par le réseau
        String de48sub15 = "15" + "10" + dtUtc;

        ISOMsg msg = new ISOMsg();
        msg.setPackager(net.getPackager());
        msg.setMTI("0120");
        msg.set(2,  pan);
        msg.set(3,  processingCode);
        msg.set(4,  amount);
        msg.set(7,  dtUtc);
        msg.set(11, stan);
        msg.set(18, defaultMcc);
        msg.set(32, acquirerId);
        msg.set(48, de48sub15);
        msg.set(49, defaultCurrency);
        msg.set(61, buildPosData(posStatus));

        // DE90 pour la completion (référence la preauth)
        String de90 = null;
        if (originalStan != null && originalDt != null) {
            de90 = buildDe90("0100", originalStan, originalDt, acquirerId, "00000000000");
            msg.set(90, de90);
        }

        // PIN si fourni (advice simple peut en avoir un)
        if (pin != null && !pin.isEmpty()) {
            McDmasMemberKey pek = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("PEK introuvable"));
            byte[] pinBlock = hsm.encryptPinBlock(pin, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
            msg.set(52, pinBlock);
        }

        log.info("[DMAS-ADV] === 0120 {} (DE61sf7={}) ===", label, posStatus);
        log.info("[DMAS-ADV] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ADV] DE3  Processing Code  = {}", processingCode);
        log.info("[DMAS-ADV] DE4  Amount           = {}", amount);
        log.info("[DMAS-ADV] DE11 STAN             = {}", stan);
        log.info("[DMAS-ADV] DE48 sub15 AdviceDT   = {} (ID=15 len=10 dt={})", de48sub15, dtUtc);
        log.info("[DMAS-ADV] DE61 POS Data         = {}", buildPosData(posStatus));
        if (de90 != null) log.info("[DMAS-ADV] DE90 Original (preauth)= {} (STAN orig={})", de90, originalStan);

        String reqHex = ISOUtil.hexString(msg.pack());
        LocalDateTime requestAt = LocalDateTime.now();
        ISOMsg resp = net.sendAndReceive(msg, issuerHost, issuerPort, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);
        persistAdvice(msg, resp, requestAt, LocalDateTime.now());

        log.info("[DMAS-ADV] <- 0130 DE39={} ok={} (DE48 sub15 echo={})", rc, ok, net.safeGet(resp, 48));

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("mode", label);
        r.put("mti_response", resp.getMTI());
        r.put("de011_stan", stan);
        r.put("de048_sub15_sent", de48sub15);
        r.put("de048_sub15_echo", net.safeGet(resp, 48));
        if (de90 != null) r.put("de090_original", de90);
        r.put("de039_response_code", rc);
        r.put("acknowledged", ok);
        r.put("request_hex", reqHex);
        r.put("response_hex", ISOUtil.hexString(resp.pack()));
        return r;
    }

    private void persistAdvice(ISOMsg request, ISOMsg response,
                               LocalDateTime requestAt, LocalDateTime responseAt)
            throws Exception {
        String stan = net.safeGet(request, 11);
        String transmissionDatetime = net.safeGet(request, 7);
        McDmasMemberTransaction transaction = transactionRepo
                .findByBankCodeAndStanAndTransmissionDatetime(
                        acquirerId, stan, transmissionDatetime)
                .orElseGet(McDmasMemberTransaction::new);
        McDmasAuthorizationJournalMapper.populate(
                transaction, request, response, acquirerId, memberGroup,
                requestAt, responseAt);
        transactionRepo.save(transaction);
        log.info("[DMAS-ADV] Journal membre enregistre STAN={} DE39={} clearingEligible={}",
                stan, net.safeGet(response, 39), transaction.isClearingEligible());
    }

    private String buildPosData(String sf7) {
        StringBuilder sb = new StringBuilder("000000000000");
        sb.setCharAt(6, sf7.charAt(0));
        return sb.toString();
    }

    private String buildDe90(String mti, String stan, String dt, String de32, String de33) {
        return pad(mti,4) + pad(stan,6) + pad(dt,10) + padLeft(de32,11) + padLeft(de33,11);
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
