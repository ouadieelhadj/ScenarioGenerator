package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.entity.McDmasMemberTransaction;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import com.staging.sg.common.repository.McDmasMemberTransactionRepository;
import com.staging.sg.common.service.McDmasAuthorizationJournalMapper;
import org.jpos.iso.ISOException;
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
 * Construction et envoi des transactions Authorization Request/0100
 * côté RESEAU (acquéreur) vers la BANQUE (issuer).
 *
 * Le "type" métier est mappé vers DE3 (Processing Code) + DE61 sf7 (POS Transaction Status).
 * Le PIN (DE52) est chiffré sous PEK (FORMAT00) quand le type le requiert.
 */
@Service
public class McDmasAuthorization {

    private static final Logger log = LoggerFactory.getLogger(McDmasAuthorization.class);

    private final McDmasNetworkUtil net;
    private final HsmService hsm;
    private final McDmasMemberKeyRepository acqKeyRepo;
    private final McDmasMemberTransactionRepository transactionRepo;
    private final McDmasMemberClient jposServer;
    private final com.staging.sg.common.emv.McDmasEmv emv;
    private final com.staging.sg.common.repository.McDmasCardRepository cardRepo;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;
    @Value("${dmas.member-group-id:TESTGRP01}") private String memberGroup;
    @Value("${dmas.acquirer-id:022905}")    private String acquirerId;
    @Value("${dmas.default-currency:504}")  private String defaultCurrency;
    @Value("${dmas.default-mcc:5999}")      private String defaultMcc;
    @Value("${dmas.default-merchant-name-location:TEST MERCHANT CASABLANCA MA}")
    private String defaultMerchantNameLocation;

    public McDmasAuthorization(McDmasNetworkUtil net, HsmService hsm, McDmasMemberKeyRepository acqKeyRepo,
                               McDmasMemberTransactionRepository transactionRepo,
                               McDmasMemberClient jposServer,
                               com.staging.sg.common.emv.McDmasEmv emv,
                               com.staging.sg.common.repository.McDmasCardRepository cardRepo) {
        this.net = net;
        this.hsm = hsm;
        this.acqKeyRepo = acqKeyRepo;
        this.transactionRepo = transactionRepo;
        this.jposServer = jposServer;
        this.emv = emv;
        this.cardRepo = cardRepo;
    }

    /** Type métier -> (DE3 sf1, DE61 sf7, PIN requis). */
    public enum TxType {
        PURCHASE        ("00", "0", false),
        WITHDRAWAL      ("01", "0", true),
        PURCHASE_CASHBACK("09", "0", true),
        CASH_DISBURSEMENT("17", "0", true),
        REFUND          ("20", "0", false),
        PAYMENT         ("28", "0", false),
        BALANCE_INQUIRY ("30", "0", true),
        TRANSFER        ("40", "0", true),
        PREAUTH         ("00", "4", false),
        PIN_CHANGE      ("92", "0", true),
        PIN_UNBLOCK     ("91", "0", true);

        public final String de3sf1;   // Cardholder Transaction Type Code
        public final String de61sf7;  // POS Transaction Status
        public final boolean pinRequired;

        TxType(String de3sf1, String de61sf7, boolean pinRequired) {
            this.de3sf1 = de3sf1; this.de61sf7 = de61sf7; this.pinRequired = pinRequired;
        }

        public static TxType from(String s) {
            return TxType.valueOf(s.trim().toUpperCase());
        }
    }

    /**
     * Construit et envoie un 0100 selon le type.
     * @param typeStr  type métier (purchase, withdrawal, preauth, refund, balance_inquiry...)
     * @param pan      PAN du porteur
     * @param amount   montant n-12 (ex "000000010000")
     * @param pin      PIN clair (peut être null si type sans PIN)
     */
    public Map<String,Object> sendAuthorization(String typeStr, String pan, String amount,
                                                String pin, String terminalId, String acceptorId, String entryMode,
                                                String transport) throws Exception {
        TxType type = TxType.from(typeStr);

        // Processing code DE3 = [sf1][00][00] (from/to account par défaut)
        String processingCode = type.de3sf1 + "0000";
        // POS data DE61 : on place le sf7 en position 7 d'une chaîne de subfields simplifiée
        // Format simplifié : positions 1-12 (sf1..sf12), on remplit sf7 et le reste à 0
        String de61 = buildPosData(type.de61sf7);

        String stan = net.generateStan();
        Date now = new Date();
        String dtUtc = new SimpleDateFormat("MMddHHmmss").format(now);
        LocalDateTime requestAt = LocalDateTime.now();

        ISOMsg msg = new ISOMsg();
        msg.setPackager(net.getPackager());
        msg.setMTI("0100");
        msg.set(2,  pan);
        msg.set(3,  processingCode);
        msg.set(4,  amount);
        msg.set(7,  dtUtc);
        msg.set(11, stan);
        msg.set(12, new SimpleDateFormat("HHmmss").format(now));
        msg.set(13, new SimpleDateFormat("MMdd").format(now));
        msg.set(18, defaultMcc);
        msg.set(22, "052");
        msg.set(23, "000");   // Card Sequence Number (PSN) — requis pour la derivation ICC                 // POS entry mode : 051 = chip
        msg.set(32, acquirerId);
        msg.set(37, generateRrn());
        if (terminalId != null) msg.set(41, terminalId);
        if (acceptorId != null) msg.set(42, acceptorId);
        msg.set(43, defaultMerchantNameLocation);
        msg.set(49, defaultCurrency);
        msg.set(61, de61);
        msg.set(48, buildDe48(entryMode));
        log.info("[DMAS-AUTH] DE48 Additional Data    = {} (entryMode={})", msg.getString(48), entryMode);

        // PIN (DE52) chiffré sous PEK si fourni
        String pinKcv = null;
        if (pin != null && !pin.isEmpty()) {
            McDmasMemberKey pek = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("PEK introuvable — faire un key exchange d'abord"));
            byte[] pinBlock = hsm.encryptPinBlock(pin, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
            msg.set(52, pinBlock);
            pinKcv = pek.getKcv();
        } else if (type.pinRequired) {
            log.warn("[DMAS-AUTH] Type {} requiert un PIN mais aucun fourni", type);
        }

        // LOG détaillé de tous les DE
        log.info("[DMAS-AUTH] === 0100 {} (DE3={} DE61sf7={}) ===", type, processingCode, type.de61sf7);
        log.info("[DMAS-AUTH] DE2  PAN                = {}", maskPan(pan));
        log.info("[DMAS-AUTH] DE3  Processing Code    = {}", processingCode);
        log.info("[DMAS-AUTH] DE4  Amount             = {}", amount);
        log.info("[DMAS-AUTH] DE7  Transmission DT     = {}", dtUtc);
        log.info("[DMAS-AUTH] DE11 STAN               = {}", stan);
        log.info("[DMAS-AUTH] DE18 Merchant Type      = {}", defaultMcc);
        log.info("[DMAS-AUTH] DE22 POS Entry Mode     = 051");
        log.info("[DMAS-AUTH] DE32 Acquiring Inst ID  = {}", acquirerId);
        log.info("[DMAS-AUTH] DE41 Terminal ID        = {}", terminalId);
        log.info("[DMAS-AUTH] DE42 Acceptor ID        = {}", acceptorId);
        log.info("[DMAS-AUTH] DE49 Currency           = {}", defaultCurrency);
        log.info("[DMAS-AUTH] DE52 PIN block          = {}", msg.hasField(52) ? "présent (8o, sous PEK kcv="+pinKcv+")" : "absent");
        log.info("[DMAS-AUTH] DE61 POS Data           = {}", de61);

        String reqHex = ISOUtil.hexString(msg.pack());
        boolean jpos = "jpos".equalsIgnoreCase(transport == null ? "" : transport.trim());
        ISOMsg resp;
        if (jpos) {
            log.info("[DMAS-AUTH] Transport = JPOS (connexion permanente, pushAndWait STAN={})", stan);
            resp = jposServer.pushAndWait(msg, timeoutSeconds);
        } else {
            log.info("[DMAS-AUTH] Transport = SOCKET ephemere ({}:{})", issuerHost, issuerPort);
            resp = net.sendAndReceive(msg, issuerHost, issuerPort, timeoutSeconds);
        }
        String rc = net.safeGet(resp, 39);
        boolean approved = "00".equals(rc);
        persistAuthorization(msg, resp, requestAt, LocalDateTime.now());

        log.info("[DMAS-AUTH] <- 0110 DE39={} approved={}", rc, approved);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("transport", jpos ? "jpos" : "socket");
        r.put("type", type.name());
        r.put("mti_response", resp.getMTI());
        r.put("de003_processing_code", processingCode);
        r.put("de004_amount", amount);
        r.put("de011_stan", stan);
        r.put("de039_response_code", rc);
        r.put("approved", approved);
        r.put("pin_included", msg.hasField(52));
        r.put("request_hex", reqHex);
        r.put("response_hex", ISOUtil.hexString(resp.pack()));
        return r;
    }

    /** Construit le DE61 POS Data avec le sf7 (POS Transaction Status) à la bonne position. */
    /**
     * Construit un 0100 minimal pour le LOAD TEST (pas de PIN, STAN fourni par l'appelant
     * pour garantir l'unicite en charge concurrente). Reutilise buildDe48/buildPosData.
     */
    public org.jpos.iso.ISOMsg buildAuth0100(String pan, String amount, String entryMode, String stan) throws Exception {
        return buildAuth0100("0100", pan, amount, entryMode, stan);
    }

    /** Variante MTI-parametrique (etape 3 multi-reseau). Defaut 0100 conserve le comportement. */
    public org.jpos.iso.ISOMsg buildAuth0100(String mti, String pan, String amount, String entryMode, String stan) throws Exception {
        String processingCode = "000000";
        String de61 = buildPosData("0");
        String dtUtc = new java.text.SimpleDateFormat("MMddHHmmss").format(new java.util.Date());
        org.jpos.iso.ISOMsg msg = new org.jpos.iso.ISOMsg();
        msg.setPackager(net.getPackager());
        msg.setMTI(mti);
        msg.set(2,  pan);
        msg.set(3,  processingCode);
        msg.set(4,  amount);
        msg.set(7,  dtUtc);
        msg.set(11, stan);
        msg.set(18, defaultMcc);
        msg.set(22, "052");
        msg.set(23, "000");   // Card Sequence Number (PSN) — requis pour la derivation ICC
        msg.set(32, acquirerId);
        msg.set(41, "12499991");                                  // Terminal ID
        msg.set(42, "20251015       ");                          // Card Acceptor ID (15)
        msg.set(43, "TEST MERCHANT 20251015 CASABLANCA    MAR");  // Name/Location (40)
        msg.set(49, defaultCurrency);
        msg.set(61, de61);
        msg.set(48, buildDe48(entryMode));
        return msg;
    }

    /**
     * Variante du 0100 LOAD TEST AVEC PIN : chiffre le PIN block sous PEK (comme le flux normal).
     * Reutilise hsm.encryptPinBlock + la PEK ACTIVE. Necessite un key exchange prealable.
     */
    public org.jpos.iso.ISOMsg buildAuth0100WithPin(String pan, String pin, String amount,
                                                    String entryMode, String stan) throws Exception {
        return buildAuth0100WithPin("0100", pan, pin, amount, entryMode, stan);
    }

    public org.jpos.iso.ISOMsg buildAuth0100WithPin(String mti, String pan, String pin, String amount,
                                                    String entryMode, String stan) throws Exception {
        org.jpos.iso.ISOMsg msg = buildAuth0100(mti, pan, amount, entryMode, stan);
        if (pin != null && !pin.isEmpty()) {
            com.staging.sg.common.entity.McDmasMemberKey pek = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("PEK introuvable - faire un key exchange d'abord"));
            byte[] pinBlock = hsm.encryptPinBlock(pin, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
            msg.set(52, pinBlock);
        }
        return msg;
    }


    /**
     * Variante 0100 AVEC DE55 EMV construit.
     *
     * Charge les donnees EMV de la carte et sa MDK (sous LMK), fait
     * construire le cryptogramme par McDmasEmv, et pose le DE55.
     * L'ATC est incremente et persiste par carte a chaque appel.
     *
     * Activable independamment : les methodes existantes ne changent
     * pas, le stress test reste inchange.
     */
    public org.jpos.iso.ISOMsg buildAuth0100WithEmv(String mti, String pan, String amount,
                                                    String entryMode, String stan) throws Exception {
        org.jpos.iso.ISOMsg msg = buildAuth0100(mti, pan, amount, entryMode, stan);

        com.staging.sg.common.entity.McDmasCard card = cardRepo.findByPan(pan)
                .orElseThrow(() -> new IllegalStateException("Carte inconnue : " + pan));

        com.staging.sg.common.entity.McDmasMemberKey mdk = acqKeyRepo
                .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "MDK", "ACTIVE")
                .orElseThrow(() -> new IllegalStateException(
                        "MDK introuvable — faire le bootstrap MDK d'abord"));

        // ATC incremente par carte, en base
        int atc = (card.getEmvAtc() == null ? 0 : card.getEmvAtc()) + 1;
        card.setEmvAtc(atc);
        cardRepo.save(card);

        com.staging.sg.common.emv.McDmasEmv.EmvInput in =
                new com.staging.sg.common.emv.McDmasEmv.EmvInput();
        in.mdkUnderLmk = mdk.getKeyUnderLmk();
        in.mdkKcv      = mdk.getKcv();
        in.mdkLenBytes = mdk.getKeyLength() != null ? mdk.getKeyLength() : 16;
        in.pan         = pan;
        in.psn         = card.getEmvPsn()        != null ? card.getEmvPsn()        : "00";
        in.atc         = atc;
        in.aid         = card.getEmvAid();
        in.aip         = card.getEmvAip();
        in.iad         = card.getEmvIad();
        in.appVersion  = card.getEmvAppVersion();
        in.cvmResults  = card.getEmvCvmResults() != null ? card.getEmvCvmResults() : "010002";
        in.amount      = amount;
        in.currency    = defaultCurrency;
        in.countryCode = "0504";
        in.date        = new java.text.SimpleDateFormat("yyMMdd").format(new java.util.Date());

        com.staging.sg.common.emv.McDmasEmv.EmvResult r = emv.build(in);
        // --- champs chip exiges par le reseau quand DE22.1 = 05 ---

        java.util.Date __now = new java.util.Date();

        msg.set(12, new java.text.SimpleDateFormat("HHmmss").format(__now));   // heure locale

        msg.set(13, new java.text.SimpleDateFormat("MMdd").format(__now));     // date locale

        String __exp = (card.getExpiry() != null && card.getExpiry().length() == 4) ? card.getExpiry() : "2906";

        msg.set(14, __exp);                                                    // expiration yymm

        msg.set(35, pan + "D" + __exp + "201" + "0000000");                    // Track2

        msg.set(37, String.format("%012d", (System.currentTimeMillis() % 1000000000000L))); // RRN

        msg.set(55, r.de55);

        log.info("[DMAS-AUTH] DE55 construit — ARQC={} ATC={} pan={}",
                r.arqc, atc, maskPan(pan));
        return msg;
    }

    private String buildPosData(String sf7) {
        // Structure POS Data alignee sur le membre reel (accepte par le simulateur).
        // 21 positions ; sf7 porte le POS Transaction Status.
        StringBuilder value = new StringBuilder("000001000030050420100");
        value.setCharAt(6, (sf7 == null || sf7.isEmpty()) ? '0' : sf7.charAt(0));
        return value.toString();
    }

    private void persistAuthorization(ISOMsg request, ISOMsg response,
                                      LocalDateTime requestAt, LocalDateTime responseAt)
            throws ISOException {
        String stan = net.safeGet(request, 11);
        String transmissionDatetime = net.safeGet(request, 7);
        McDmasMemberTransaction transaction = transactionRepo
                .findByBankCodeAndStanAndTransmissionDatetime(
                        acquirerId, stan, transmissionDatetime)
                .orElseGet(McDmasMemberTransaction::new);
        McDmasAuthorizationJournalMapper.populate(
                transaction, request, response, acquirerId, memberGroup, requestAt, responseAt);
        transactionRepo.save(transaction);
        log.info("[DMAS-AUTH] Journal membre enregistre STAN={} DE39={} clearingEligible={}",
                stan, net.safeGet(response, 39), transaction.isClearingEligible());
    }

    private String generateRrn() {
        return String.format("%012d", System.currentTimeMillis() % 1_000_000_000_000L);
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }

    /**
     * Construit DE48 (Additional Data: Private Use) selon le mode d'entree.
     * Structure subelement : SE-ID(2) + SE-len(2) + data.
     *  - CARD_PRESENT : SE61 (POS Data Extended) n-5 = "00001"
     *  - ECOM         : SE42 (E-Commerce Indicators) n-7 = "0103210" (obligatoire e-commerce)
     *                   + SE61 n-5 = "00001"
     * Conforme trace CIS (Tag42=[0103210], Tag61=[00001]).
     */
    private String buildDe48(String entryMode) {
        boolean ecom = "ECOM".equalsIgnoreCase(entryMode == null ? "" : entryMode.trim());
        StringBuilder sb = new StringBuilder();
        sb.append(" ");                                  // TCC = espace (comme le membre reel)
        sb.append("22").append("05").append("0601A");   // SE22 Multi-Purpose Merchant Indicator
        if (ecom) {
            sb.append("42").append("07").append("0103210"); // SE42 e-commerce
        }
        sb.append("61").append("05").append("00001");        // SE61 POS data extended
        return sb.toString();
    }
}
