package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.McDmasCard;
import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.entity.McDmasTransaction;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.repository.KeyStoreRepository;
import com.staging.sg.common.repository.McDmasCardRepository;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import com.staging.sg.common.repository.McDmasTransactionRepository;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Logique metier du reseau Mastercard DMAS simule.
 *
 * ------------------------------------------------------------------
 *  CETTE CLASSE N'OUVRE PLUS DE SOCKET
 * ------------------------------------------------------------------
 * Elle hebergeait un ServerSocket sur le port 8500, en concurrence avec
 * l'ISOServer de McDmasMastercardServer — d'ou le "Address already in
 * use: bind" au demarrage. Le transport est desormais entierement
 * assure par McDmasMastercardServer (liaison permanente jPOS).
 *
 * Ici ne subsiste que la DECISION : chaque methode buildXxxResponse
 * prend le message recu, applique la regle metier, et RETOURNE la
 * reponse. C'est l'appelant qui l'envoie.
 *
 *   0800  ->  buildNetworkResponse         (sign-on, echo, echange de cles)
 *   0100  ->  buildAuthResponse            (autorisation)
 *   0400  ->  buildReversalResponse        (annulation)
 *   0120  ->  buildAdviceResponse          (advice simple ou completion)
 *   0420  ->  buildReversalAdviceResponse  (reversal Stand-In)
 */
@Service
public class McDmasMastercardHandler {

    private static final Logger log = LoggerFactory.getLogger(McDmasMastercardHandler.class);

    private final McDmasNetworkUtil net;
    private final HsmService hsm;
    private final McDmasKekRepository kekRepo;
    private final KeyStoreRepository keyStoreRepo;
    private final McDmasMastercardKeyRepository issKeyRepo;
    private final McDmasCardRepository cardRepo;
    private final McDmasTransactionRepository txRepo;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    private final AtomicLong msgCount = new AtomicLong(0);

    public McDmasMastercardHandler(McDmasNetworkUtil net, HsmService hsm,
                                   McDmasKekRepository kekRepo, KeyStoreRepository keyStoreRepo,
                                   McDmasMastercardKeyRepository issKeyRepo,
                                   McDmasCardRepository cardRepo,
                                   McDmasTransactionRepository txRepo) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.keyStoreRepo = keyStoreRepo;
        this.issKeyRepo = issKeyRepo;
        this.cardRepo = cardRepo;
        this.txRepo = txRepo;
    }

    public long getMessageCount() { return msgCount.get(); }

    // ====================================================================
    //  0800 — GESTION RESEAU
    // ====================================================================

    /**
     * Construit la reponse 0810 a un message de gestion reseau.
     * DE70=161 declenche l'import de la cle transportee dans le DE48.
     */
    public ISOMsg buildNetworkResponse(ISOMsg request) throws ISOException {
        msgCount.incrementAndGet();
        String de70 = net.safeGet(request, 70);

        if ("161".equals(de70)) {
            String rc = importKeyFromDe48(request, de70);
            return buildResponse(request, rc);
        }

        String stan = net.safeGet(request, 11);
        String label = switch (de70 != null ? de70 : "") {
            case "001", "061" -> "SIGN-ON";
            case "002", "062" -> "SIGN-OFF";
            case "162"        -> "SOLLICITATION DE CLE";
            case "164"        -> "CONFIRMATION DE SUCCES";
            case "165"        -> "AVIS D ECHEC";
            case "270"        -> "ECHO";
            default           -> "NETWORK(" + de70 + ")";
        };
        log.info("[DMAS-ISS] Recu 0800 {} — STAN={}", label, stan);

        ISOMsg response = buildResponse(request, "00");
        log.info("[DMAS-ISS] {} -> 0810 DE39=00", label);
        return response;
    }

    /**
     * Importe la cle du DE48 subelement 11 sous le LMK local, verifie le
     * KCV, persiste. Retourne le code DE39 a renvoyer.
     */
    private String importKeyFromDe48(ISOMsg request, String de70) {
        log.info("[DMAS-ISS] === Recu 0800 PEK exchange (DE70=161) ===");
        log.info("[DMAS-ISS] DE2  Member Group ID       = {}", net.safeGet(request, 2));
        log.info("[DMAS-ISS] DE7  Transmission DateTime = {}", net.safeGet(request, 7));
        log.info("[DMAS-ISS] DE11 STAN                  = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE33 Forwarding Inst ID    = {}", net.safeGet(request, 33));
        log.info("[DMAS-ISS] DE63 Network Data          = {}", net.safeGet(request, 63));
        log.info("[DMAS-ISS] DE70 Network Mgmt Code     = {}", de70);

        String de048 = net.safeGet(request, 48);
        String keyType = "PEK";
        try {
            KeyExchangeBlock keb = KeyExchangeBlock.parseDe48(de048);
            keb.logDetail("0800 recu (DE48)");

            if (!KeyExchangeBlock.KEY_CLASS_PIN.equals(keb.keyClassId)) {
                log.warn("[DMAS-ISS] Key Class ID inattendu : {}", keb.keyClassId);
            }

            String keyUnderKekHex = keb.encryptedKeyHex;
            // KCV recu sur 16 hex : comparaison sur les 6 premiers (KCV jPOS = 3 octets)
            String kcvReceived = (keb.kcv != null && keb.kcv.length() >= 6)
                    ? keb.kcv.substring(0, 6) : keb.kcv;
            int keyLen = keyUnderKekHex.length() / 2;

            McDmasKek kek = kekRepo.findByMemberGroupId(memberGroup)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable " + memberGroup));

            HsmService.KeyResult imp =
                    hsm.importWorkingKey(keyType, keyUnderKekHex, kek.getKekClear(), keyLen);

            boolean kcvOk = imp.kcv.equalsIgnoreCase(kcvReceived);
            log.info("[DMAS-ISS] {} import — KCV recu={} calcule={} match={}",
                    keyType, kcvReceived, imp.kcv, kcvOk);

            if (!kcvOk) return "30";   // format error / KCV mismatch

            McDmasMastercardKey ik = issKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, keyType, "ACTIVE")
                    .orElseGet(McDmasMastercardKey::new);
            ik.setMemberGroupId(memberGroup);
            ik.setKeyType(keyType);
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(imp.keyUnderLmkHex);
            ik.setKeyUnderKek(keyUnderKekHex.length() > 64
                    ? keyUnderKekHex.substring(0, 64) : keyUnderKekHex);
            ik.setKcv(imp.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepo.save(ik);
            log.info("[DMAS-ISS] {} stocke dans mc_dmas_mastercard_keys (KCV={})", keyType, imp.kcv);
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-ISS] Key exchange error : {}", e.getMessage(), e);
            return "30";
        }
    }

    // ====================================================================
    //  0100 — AUTORISATION
    // ====================================================================

    /**
     * Construit la reponse 0110 : decision metier + echo des DE d'origine.
     */
    public ISOMsg buildAuthResponse(ISOMsg request) throws ISOException {
        msgCount.incrementAndGet();
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);

        log.info("[DMAS-ISS] === Recu {} Authorization ===", request.getMTI());
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE3  Processing Code  = {}", net.safeGet(request, 3));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE7  Transmission DT  = {}", net.safeGet(request, 7));
        log.info("[DMAS-ISS] DE11 STAN             = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE18 Merchant Type    = {}", net.safeGet(request, 18));
        log.info("[DMAS-ISS] DE22 POS Entry Mode   = {}", net.safeGet(request, 22));
        log.info("[DMAS-ISS] DE32 Acquiring Inst   = {}", net.safeGet(request, 32));
        log.info("[DMAS-ISS] DE41 Terminal ID      = {}", net.safeGet(request, 41));
        log.info("[DMAS-ISS] DE42 Acceptor ID      = {}", net.safeGet(request, 42));
        log.info("[DMAS-ISS] DE49 Currency         = {}", net.safeGet(request, 49));
        log.info("[DMAS-ISS] DE52 PIN block        = {}", request.hasField(52) ? "present (8o)" : "absent");
        log.info("[DMAS-ISS] DE61 POS Data         = {}", net.safeGet(request, 61));

        String rc = decide(request, pan, amountS);

        ISOMsg resp = new ISOMsg();
        resp.setPackager(net.getPackager());
        resp.setMTI("0110");
        if (request.hasField(2))  resp.set(2,  request.getString(2));
        if (request.hasField(3))  resp.set(3,  request.getString(3));
        if (request.hasField(4))  resp.set(4,  request.getString(4));
        if (request.hasField(7))  resp.set(7,  request.getString(7));
        if (request.hasField(11)) resp.set(11, request.getString(11));
        resp.set(39, rc);
        log.info("[DMAS-ISS] 0110 construit DE39={} ({})", rc, rcLabel(rc));
        return resp;
    }

    /** Moteur de decision : retourne le response code DE39. */
    private String decide(ISOMsg request, String pan, String amountS) {
        try {
            // 1. Carte existe et active ?
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Decision : carte introuvable -> 14");
                return "14";
            }
            if (!"ACTIVE".equals(card.getStatus())) {
                log.info("[DMAS-ISS] Decision : carte non active -> 62");
                return "62";
            }

            // 2. PIN correct ? (dechiffrement du DE52 sous PEK)
            if (request.hasField(52)) {
                byte[] pinBlock = request.getBytes(52);
                McDmasMastercardKey pek = issKeyRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                        .orElse(null);
                if (pek == null) {
                    log.warn("[DMAS-ISS] PEK introuvable pour dechiffrer le PIN -> 96");
                    return "96";
                }
                String pinClair = hsm.decryptPinBlock(pinBlock, pan,
                        pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
                log.info("[DMAS-ISS] PIN dechiffre (len={}) — comparaison avec le PIN carte",
                        pinClair.length());
                if (!pinClair.equals(card.getPin())) {
                    log.info("[DMAS-ISS] Decision : PIN incorrect -> 55");
                    return "55";
                }
                log.info("[DMAS-ISS] PIN correct");
            }

            // 3. Sens de l'operation selon DE3 sous-champ 1
            long amount = (amountS != null && !amountS.isEmpty()) ? Long.parseLong(amountS) : 0L;
            String procCode = net.safeGet(request, 3);
            String txType = (procCode != null && procCode.length() >= 2) ? procCode.substring(0, 2) : "00";
            String sens = operationSens(txType);
            log.info("[DMAS-ISS] Type transaction DE3 sf1={} -> {}", txType, sens);

            switch (sens) {
                case "DEBIT" -> {
                    if (card.getBalance() < amount) {
                        log.info("[DMAS-ISS] Decision : solde insuffisant ({} < {}) -> 51",
                                card.getBalance(), amount);
                        return "51";
                    }
                    card.setBalance(card.getBalance() - amount);
                    cardRepo.save(card);
                    log.info("[DMAS-ISS] DEBIT {} -> nouveau solde={}", amount, card.getBalance());
                }
                case "CREDIT" -> {
                    card.setBalance(card.getBalance() + amount);
                    cardRepo.save(card);
                    log.info("[DMAS-ISS] CREDIT {} -> nouveau solde={}", amount, card.getBalance());
                }
                case "INQUIRY" ->
                    log.info("[DMAS-ISS] INQUIRY : consultation solde={} (aucun mouvement)",
                            card.getBalance());
                default ->
                    log.info("[DMAS-ISS] Type {} : approuve sans mouvement de solde", txType);
            }

            // 4. Memoriser la transaction (pour un eventuel reversal)
            String stan = net.safeGet(request, 11);
            String dt   = net.safeGet(request, 7);
            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(stan, dt)
                    .orElseGet(McDmasTransaction::new);
            tx.setPan(pan);
            tx.setStan(stan);
            tx.setTransmissionDt(dt);
            tx.setMti(request.getMTI());
            tx.setProcessingCode(procCode);
            tx.setAmount(amount);
            tx.setCurrency(net.safeGet(request, 49));
            tx.setResponseCode("00");
            tx.setStatus("APPROVED");
            txRepo.save(tx);
            log.info("[DMAS-ISS] Decision : APPROUVE -> 00 (solde={}, tx STAN={})",
                    card.getBalance(), stan);
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur du moteur de decision : {}", e.getMessage(), e);
            return "96";
        }
    }

    // ====================================================================
    //  0400 — REVERSAL
    // ====================================================================

    /** Construit la reponse 0410 a une demande d'annulation. */
    public ISOMsg buildReversalResponse(ISOMsg request) throws ISOException {
        msgCount.incrementAndGet();
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de90    = net.safeGet(request, 90);

        log.info("[DMAS-ISS] === Recu 0400 Reversal ===");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN (nouveau)   = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE90 Original Data    = {}", de90);

        String rc = doReverse(pan, amountS, de90);

        ISOMsg resp = new ISOMsg();
        resp.setPackager(net.getPackager());
        resp.setMTI("0410");
        if (request.hasField(2))  resp.set(2,  request.getString(2));
        if (request.hasField(3))  resp.set(3,  request.getString(3));
        if (request.hasField(4))  resp.set(4,  request.getString(4));
        if (request.hasField(7))  resp.set(7,  request.getString(7));
        if (request.hasField(11)) resp.set(11, request.getString(11));
        if (request.hasField(90)) resp.set(90, request.getString(90));
        resp.set(39, rc);
        log.info("[DMAS-ISS] 0410 construit DE39={} ({})", rc, rcLabel(rc));
        return resp;
    }

    /** Recredite le solde en retrouvant la transaction originale via le DE90. */
    private String doReverse(String pan, String amountS, String de90) {
        try {
            // DE90 : [MTI 4][STAN 6][DT 10][DE32 11][DE33 11]
            if (de90 == null || de90.length() < 20) {
                log.warn("[DMAS-ISS] DE90 absent ou invalide -> 30");
                return "30";
            }
            String origStan = de90.substring(4, 10);
            String origDt   = de90.substring(10, 20);
            log.info("[DMAS-ISS] Reversal : recherche de la tx originale STAN={} DT={}",
                    origStan, origDt);

            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(origStan, origDt).orElse(null);
            if (tx == null) {
                log.info("[DMAS-ISS] Transaction originale introuvable -> 25");
                return "25";
            }
            if ("REVERSED".equals(tx.getStatus())) {
                log.info("[DMAS-ISS] Transaction deja annulee (anti double-reversal) -> 00");
                return "00";
            }

            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Carte introuvable pour le reversal -> 14");
                return "14";
            }
            long amount = tx.getAmount() != null ? tx.getAmount() : 0L;
            card.setBalance(card.getBalance() + amount);
            cardRepo.save(card);

            tx.setStatus("REVERSED");
            tx.setReversedAt(java.time.LocalDateTime.now());
            txRepo.save(tx);

            log.info("[DMAS-ISS] Reversal APPROUVE -> 00 (recredite {}, nouveau solde={})",
                    amount, card.getBalance());
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur reversal : {}", e.getMessage(), e);
            return "96";
        }
    }

    // ====================================================================
    //  0120 — ADVICE
    // ====================================================================

    /**
     * Construit la reponse 0130 a un advice.
     * DE90 present = completion (ajustement d'une preautorisation),
     * sinon advice simple (transaction offline).
     */
    public ISOMsg buildAdviceResponse(ISOMsg request) throws ISOException {
        msgCount.incrementAndGet();
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de90    = net.safeGet(request, 90);
        boolean isCompletion = (de90 != null && de90.length() >= 20);

        log.info("[DMAS-ISS] === Recu 0120 Advice ({}) ===",
                isCompletion ? "COMPLETION" : "SIMPLE");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN             = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE48 sub15 AdviceDT   = {}", net.safeGet(request, 48));
        if (isCompletion) log.info("[DMAS-ISS] DE90 Original (preauth) = {}", de90);

        String rc = isCompletion ? doCompletion(pan, amountS, de90)
                                 : doAdvice(pan, amountS, request);

        ISOMsg resp = new ISOMsg();
        resp.setPackager(net.getPackager());
        resp.setMTI("0130");
        if (request.hasField(2))  resp.set(2,  request.getString(2));
        if (request.hasField(3))  resp.set(3,  request.getString(3));
        if (request.hasField(4))  resp.set(4,  request.getString(4));
        if (request.hasField(7))  resp.set(7,  request.getString(7));
        if (request.hasField(11)) resp.set(11, request.getString(11));
        if (request.hasField(48)) resp.set(48, request.getString(48));
        if (request.hasField(90)) resp.set(90, request.getString(90));
        resp.set(39, rc);
        log.info("[DMAS-ISS] 0130 construit DE39={} ({})", rc, rcLabel(rc));
        return resp;
    }

    /** Advice simple : enregistre et debite la transaction offline. */
    private String doAdvice(String pan, String amountS, ISOMsg request) {
        try {
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Advice : carte introuvable -> 14");
                return "14";
            }

            long amount = (amountS != null && !amountS.isEmpty()) ? Long.parseLong(amountS) : 0L;
            card.setBalance(card.getBalance() - amount);
            cardRepo.save(card);

            String stan = net.safeGet(request, 11);
            String dt   = net.safeGet(request, 7);
            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(stan, dt)
                    .orElseGet(McDmasTransaction::new);
            tx.setPan(pan);
            tx.setStan(stan);
            tx.setTransmissionDt(dt);
            tx.setMti("0120");
            tx.setProcessingCode(net.safeGet(request, 3));
            tx.setAmount(amount);
            tx.setCurrency(net.safeGet(request, 49));
            tx.setResponseCode("00");
            tx.setStatus("APPROVED");
            txRepo.save(tx);

            log.info("[DMAS-ISS] Advice simple : enregistre et debite {} (nouveau solde={}) -> 00",
                    amount, card.getBalance());
            return "00";
        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur advice : {}", e.getMessage(), e);
            return "96";
        }
    }

    /** Completion : ajuste le solde par rapport a la preautorisation. */
    private String doCompletion(String pan, String finalAmountS, String de90) {
        try {
            String origStan = de90.substring(4, 10);
            String origDt   = de90.substring(10, 20);
            log.info("[DMAS-ISS] Completion : recherche de la preauth STAN={} DT={}",
                    origStan, origDt);

            McDmasTransaction preauth = txRepo.findByStanAndTransmissionDt(origStan, origDt)
                    .orElse(null);
            if (preauth == null) {
                log.info("[DMAS-ISS] Preauth introuvable -> 25");
                return "25";
            }

            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Carte introuvable -> 14");
                return "14";
            }

            long estimated = preauth.getAmount() != null ? preauth.getAmount() : 0L;
            long finalAmt  = (finalAmountS != null && !finalAmountS.isEmpty())
                    ? Long.parseLong(finalAmountS) : 0L;
            long delta = finalAmt - estimated;   // >0 debiter plus, <0 rembourser

            card.setBalance(card.getBalance() - delta);
            cardRepo.save(card);

            preauth.setStatus("COMPLETED");
            preauth.setAmount(finalAmt);
            txRepo.save(preauth);

            log.info("[DMAS-ISS] Completion : estime={} final={} delta={} (nouveau solde={}) -> 00",
                    estimated, finalAmt, delta, card.getBalance());
            return "00";
        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur completion : {}", e.getMessage(), e);
            return "96";
        }
    }

    // ====================================================================
    //  0420 — REVERSAL ADVICE (Stand-In)
    // ====================================================================

    /**
     * Construit la reponse 0430. Le reseau notifie un reversal traite en
     * Stand-In ; le DE60 en donne la raison.
     */
    public ISOMsg buildReversalAdviceResponse(ISOMsg request) throws ISOException {
        msgCount.incrementAndGet();
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de60    = net.safeGet(request, 60);
        String de90    = net.safeGet(request, 90);

        log.info("[DMAS-ISS] === Recu 0420 Reversal Advice (Stand-In) ===");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN             = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE60 Advice Reason    = {} ({})", de60, adviceReasonLabel(de60));
        log.info("[DMAS-ISS] DE90 Original Data    = {}", de90);

        String rc = doReverse(pan, amountS, de90);

        ISOMsg resp = new ISOMsg();
        resp.setPackager(net.getPackager());
        resp.setMTI("0430");
        if (request.hasField(2))  resp.set(2,  request.getString(2));
        if (request.hasField(3))  resp.set(3,  request.getString(3));
        if (request.hasField(4))  resp.set(4,  request.getString(4));
        if (request.hasField(7))  resp.set(7,  request.getString(7));
        if (request.hasField(11)) resp.set(11, request.getString(11));
        if (request.hasField(60)) resp.set(60, request.getString(60));
        if (request.hasField(90)) resp.set(90, request.getString(90));
        resp.set(39, rc);
        log.info("[DMAS-ISS] 0430 construit DE39={} ({})", rc, rcLabel(rc));
        return resp;
    }

    // ====================================================================
    //  UTILITAIRES
    // ====================================================================

    /** Sens de l'operation selon le Cardholder Transaction Type Code (DE3 sf1). */
    private String operationSens(String txType) {
        return switch (txType) {
            case "00", "01", "09", "17", "18" -> "DEBIT";    // achat, retrait, cashback, avance
            case "20", "21", "22", "28"       -> "CREDIT";   // remboursement, depot, paiement
            case "30"                          -> "INQUIRY";  // consultation de solde
            default                            -> "NEUTRAL";  // virement, changement de PIN...
        };
    }

    private String rcLabel(String rc) {
        return switch (rc) {
            case "00" -> "approuve";
            case "14" -> "carte invalide";
            case "25" -> "transaction introuvable";
            case "30" -> "erreur de format";
            case "51" -> "fonds insuffisants";
            case "55" -> "PIN incorrect";
            case "62" -> "carte restreinte";
            case "96" -> "erreur systeme";
            default   -> "?";
        };
    }

    private String adviceReasonLabel(String de60) {
        if (de60 == null) return "?";
        String code = de60.length() >= 3 ? de60.substring(0, 3) : de60;
        return switch (code) {
            case "400" -> "Acquirer error unable to deliver";
            case "401" -> "Acquirer error no ack";
            case "402" -> "Issuer Time-out";
            case "403" -> "Issuer Sign-out";
            case "409" -> "Issuer Response Error";
            case "410" -> "Reversal by non-Banknet system";
            case "413" -> "Issuer Undelivered";
            default    -> "?";
        };
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }

    /** Reponse 0810 generique : echo DE7, DE11, DE70 + code reponse. */
    private ISOMsg buildResponse(ISOMsg request, String rc) throws ISOException {
        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        if (request.hasField(2))  response.set(2,  request.getString(2));
        if (request.hasField(7))  response.set(7,  request.getString(7));
        if (request.hasField(11)) response.set(11, request.getString(11));
        if (request.hasField(33)) response.set(33, request.getString(33));
        if (request.hasField(70)) response.set(70, request.getString(70));
        response.set(39, rc);
        return response;
    }
}
