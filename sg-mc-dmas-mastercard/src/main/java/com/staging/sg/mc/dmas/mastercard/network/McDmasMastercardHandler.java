package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.KeyStore;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.KeyStoreRepository;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import com.staging.sg.common.repository.McDmasCardRepository;
import com.staging.sg.common.entity.McDmasCard;
import com.staging.sg.common.repository.McDmasTransactionRepository;
import com.staging.sg.common.entity.McDmasTransaction;
import com.staging.sg.common.entity.McDmasMastercardKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Issuer DMAS — Socket server EBCDIC.
 * Traite 0800 :
 *   - sign-on  (DE070 001/002/270)         -> 0810 DE39=00
 *   - key exch (DE070 101 PEK / 102 MAK)   -> importe sous kek_under_iss_lmk,
 *                                              vérifie KCV, stocke key_store,
 *                                              0810 DE39=00 (ou 30 si KCV KO)
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

    @Value("${dmas.iso-port:8500}")
    private int isoPort;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    private Thread       serverThread;
    private ServerSocket serverSocket;
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

    @PostConstruct
    public void start() {
        serverThread = new Thread(this::runServer, "dmas-issuer-server");
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("[DMAS-ISS] Server starting — port {}", isoPort);
    }

    @PreDestroy
    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (serverThread != null) serverThread.interrupt();
        log.info("[DMAS-ISS] Server stopped");
    }

    public long getMessageCount() { return msgCount.get(); }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(isoPort);
            log.info("[DMAS-ISS] Listening on :{} (EBCDIC)", isoPort);
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                long id = msgCount.incrementAndGet();
                Thread t = new Thread(() -> handleConnection(client), "dmas-issuer-client-" + id);
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted())
                log.error("[DMAS-ISS] Server error : {}", e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        try {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ISOMsg request = net.receive(in);
            String mti = request.getMTI();
            switch (mti) {
                case "0800" -> handleNetworkMessage(request, out);
                case "0100" -> handleAuthorization(request, out);
                case "0400" -> handleReversal(request, out);
                case "0120" -> handleAdvice(request, out);
                case "0420" -> handleReversalAdvice(request, out);
                default     -> log.warn("[DMAS-ISS] MTI non géré : {}", mti);
            }
        } catch (Exception e) {
            log.error("[DMAS-ISS] Connection error : {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleNetworkMessage(ISOMsg request, DataOutputStream out) throws Exception {
        String de70 = net.safeGet(request, 70);
        if ("161".equals(de70)) {
            handleKeyExchange(request, out, de70);
        } else {
            handleSignOn(request, out, de70);
        }
    }

    private void handleSignOn(ISOMsg request, DataOutputStream out, String de70) throws Exception {
        String stan = net.safeGet(request, 11);
        String label = switch (de70 != null ? de70 : "") {
            case "001" -> "SIGN-ON";
            case "002" -> "SIGN-OFF";
            case "270" -> "ECHO";
            default    -> "NETWORK(" + de70 + ")";
        };
        log.info("[DMAS-ISS] Reçu 0800 {} — STAN={}", label, stan);

        ISOMsg response = buildResponse(request, "00");
        net.send(out, response);
        log.info("[DMAS-ISS] {} -> réponse 0810 DE39=00", label);
    }
    private void handleKeyExchange(ISOMsg request, DataOutputStream out, String de70) throws Exception {
        // LOG detaille de tous les DE recus du 0800
        log.info("[DMAS-ISS] === Recu 0800 PEK exchange (DE70=161) ===");
        log.info("[DMAS-ISS] DE2  Member Group ID      = {}", net.safeGet(request, 2));
        log.info("[DMAS-ISS] DE7  Transmission DateTime = {}", net.safeGet(request, 7));
        log.info("[DMAS-ISS] DE11 STAN                  = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE33 Forwarding Inst ID    = {}", net.safeGet(request, 33));
        log.info("[DMAS-ISS] DE63 Network Data          = {}", net.safeGet(request, 63));
        log.info("[DMAS-ISS] DE70 Network Mgmt Code     = {}", de70);

        String de048 = net.safeGet(request, 48);
        String rc = "00";
        String keyType = "PEK";
        try {
            // Parser le DE48 subelement 11 (Key Exchange Block officiel)
            KeyExchangeBlock keb = KeyExchangeBlock.parseDe48(de048);
            keb.logDetail("0800 recu (DE48)");

            // Key Class ID PK = PIN key (PEK)
            if (!KeyExchangeBlock.KEY_CLASS_PIN.equals(keb.keyClassId)) {
                log.warn("[DMAS-ISS] Key Class ID inattendu : {}", keb.keyClassId);
            }

            String keyUnderKekHex = keb.encryptedKeyHex;
            // KCV recu sur 16 hex : on compare sur les 6 premiers (KCV jPOS = 3 octets)
            String kcvReceived = keb.kcv != null && keb.kcv.length() >= 6
                    ? keb.kcv.substring(0, 6) : keb.kcv;
            int keyLen = keyUnderKekHex.length() / 2;

            McDmasKek kek = kekRepo.findByMemberGroupId(memberGroup)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable " + memberGroup));

            // Importer le PEK sous notre LMK (via KEK clair)
            HsmService.KeyResult imp = hsm.importWorkingKey(keyType, keyUnderKekHex, kek.getKekClear(), keyLen);

            boolean kcvOk = imp.kcv.equalsIgnoreCase(kcvReceived);
            log.info("[DMAS-ISS] {} import — KCV recu={} calcule={} match={}",
                    keyType, kcvReceived, imp.kcv, kcvOk);

            if (kcvOk) {
                McDmasMastercardKey ik = issKeyRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, keyType, "ACTIVE")
                        .orElseGet(McDmasMastercardKey::new);
                ik.setMemberGroupId(memberGroup);
                ik.setKeyType(keyType);
                ik.setKeyLength(keyLen);
                ik.setKeyUnderLmk(imp.keyUnderLmkHex);
                ik.setKeyUnderKek(keyUnderKekHex.length() > 64 ? keyUnderKekHex.substring(0,64) : keyUnderKekHex);
                ik.setKcv(imp.kcv);
                ik.setStatus("ACTIVE");
                issKeyRepo.save(ik);
                log.info("[DMAS-ISS] {} stocke dans dmas_iss_keys (KCV={})", keyType, imp.kcv);
            } else {
                rc = "30"; // format error / KCV mismatch
            }
        } catch (Exception e) {
            log.error("[DMAS-ISS] Key exchange error : {}", e.getMessage(), e);
            rc = "30";
        }

        ISOMsg response = buildResponse(request, rc);
        net.send(out, response);
        log.info("[DMAS-ISS] -> reponse 0810 PEK exchange DE39={}", rc);
    }

    /**
     * Traite une Authorization Request/0100 (côté BANQUE).
     * Moteur de décision : carte existe ? PIN correct ? solde suffisant ?
     * Échoe DE2,3,4,7,11 + DE39 (response code) et renvoie 0110.
     */
    private void handleAuthorization(ISOMsg request, DataOutputStream out) throws Exception {
        String pan    = net.safeGet(request, 2);
        String procCode = net.safeGet(request, 3);
        String amountS  = net.safeGet(request, 4);
        String stan   = net.safeGet(request, 11);

        // LOG détaillé de tous les DE reçus
        log.info("[DMAS-ISS] === Reçu 0100 Authorization ===");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE3  Processing Code  = {}", procCode);
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE7  Transmission DT   = {}", net.safeGet(request, 7));
        log.info("[DMAS-ISS] DE11 STAN             = {}", stan);
        log.info("[DMAS-ISS] DE18 Merchant Type    = {}", net.safeGet(request, 18));
        log.info("[DMAS-ISS] DE22 POS Entry Mode   = {}", net.safeGet(request, 22));
        log.info("[DMAS-ISS] DE32 Acquiring Inst   = {}", net.safeGet(request, 32));
        log.info("[DMAS-ISS] DE41 Terminal ID      = {}", net.safeGet(request, 41));
        log.info("[DMAS-ISS] DE42 Acceptor ID      = {}", net.safeGet(request, 42));
        log.info("[DMAS-ISS] DE49 Currency         = {}", net.safeGet(request, 49));
        log.info("[DMAS-ISS] DE52 PIN block        = {}", request.hasField(52) ? "présent (8o)" : "absent");
        log.info("[DMAS-ISS] DE61 POS Data         = {}", net.safeGet(request, 61));

        String rc = decide(request, pan, amountS);

        // Construire la reponse 0110 via methode reutilisable, puis envoyer sur le socket
        ISOMsg resp = buildAuthResponse(request);
        net.send(out, resp);
        log.info("[DMAS-ISS] -> reponse 0110 (transport socket) envoyee");
    }

    /**
     * Construit la reponse 0110 a partir d'un 0100 (decision metier + echo DE) SANS l'envoyer.
     * Reutilisable par le transport jPOS permanent (McDmasMastercardServer) comme par le socket.
     */
    public ISOMsg buildAuthResponse(ISOMsg request) throws ISOException {
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
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

    /** Moteur de décision : retourne le response code DE39. */
    private String decide(ISOMsg request, String pan, String amountS) {
        try {
            // 1. Carte existe ?
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Décision : carte introuvable -> 14");
                return "14"; // invalid card number
            }
            if (!"ACTIVE".equals(card.getStatus())) {
                log.info("[DMAS-ISS] Décision : carte non active -> 62");
                return "62"; // restricted card
            }

            // 2. PIN correct ? (déchiffrer DE52 sous PEK puis comparer)
            if (request.hasField(52)) {
                byte[] pinBlock = request.getBytes(52);
                McDmasMastercardKey pek = issKeyRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                        .orElse(null);
                if (pek == null) {
                    log.warn("[DMAS-ISS] PEK introuvable pour déchiffrer le PIN -> 96");
                    return "96"; // system malfunction
                }
                String pinClair = hsm.decryptPinBlock(pinBlock, pan,
                        pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
                log.info("[DMAS-ISS] PIN déchiffré (len={}) — comparaison avec PIN carte", pinClair.length());
                if (!pinClair.equals(card.getPin())) {
                    log.info("[DMAS-ISS] Décision : PIN incorrect -> 55");
                    return "55"; // incorrect PIN
                }
                log.info("[DMAS-ISS] PIN correct ✓");
            }

            // 3. Sens de l'operation selon DE3 subfield 1 (Cardholder Transaction Type Code)
            long amount = (amountS != null && !amountS.isEmpty()) ? Long.parseLong(amountS) : 0L;
            String procCode = net.safeGet(request, 3);
            String txType = (procCode != null && procCode.length() >= 2) ? procCode.substring(0, 2) : "00";
            String sens = operationSens(txType);
            log.info("[DMAS-ISS] Type transaction DE3 sf1={} -> {}", txType, sens);

            switch (sens) {
                case "DEBIT" -> {
                    if (card.getBalance() < amount) {
                        log.info("[DMAS-ISS] Decision : solde insuffisant ({} < {}) -> 51", card.getBalance(), amount);
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
                case "INQUIRY" -> {
                    log.info("[DMAS-ISS] INQUIRY : consultation solde={} (aucun mouvement)", card.getBalance());
                }
                default -> {
                    log.info("[DMAS-ISS] Type {} : approuve sans mouvement de solde", txType);
                }
            }

            // 4. Memoriser la transaction approuvee (pour reversal eventuel)
            String stan = net.safeGet(request, 11);
            String dt   = net.safeGet(request, 7);
            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(stan, dt).orElseGet(McDmasTransaction::new);
            tx.setPan(pan);
            tx.setStan(stan);
            tx.setTransmissionDt(dt);
            tx.setMti("0100");
            tx.setProcessingCode(procCode);
            tx.setAmount(amount);
            tx.setCurrency(net.safeGet(request, 49));
            tx.setResponseCode("00");
            tx.setStatus("APPROVED");
            txRepo.save(tx);
            log.info("[DMAS-ISS] Decision : APPROUVE -> 00 (solde={}, tx STAN={})", card.getBalance(), stan);
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur moteur décision : {}", e.getMessage(), e);
            return "96"; // system malfunction
        }
    }

    /** Sens de l'opération selon le Cardholder Transaction Type Code (DE3 sf1). */
    private String operationSens(String txType) {
        return switch (txType) {
            case "00", "01", "09", "17", "18" -> "DEBIT";   // purchase, withdrawal, cashback, cash disb, scrip
            case "20", "21", "22", "28"       -> "CREDIT";  // refund, deposit, credit adj, payment
            case "30"                         -> "INQUIRY"; // balance inquiry
            default                            -> "NEUTRAL"; // transfer, PIN change/unblock...
        };
    }

    private String rcLabel(String rc) {
        return switch (rc) {
            case "00" -> "approuvé";
            case "14" -> "carte invalide";
            case "51" -> "fonds insuffisants";
            case "55" -> "PIN incorrect";
            case "62" -> "carte restreinte";
            case "96" -> "erreur système";
            default   -> "?";
        };
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }

    /**
     * Traite un Reversal Request/0400 (côté BANQUE).
     * Retrouve la transaction originale via DE90, recrédite le solde,
     * marque REVERSED (anti double-reversal), répond 0410.
     */
    private void handleReversal(ISOMsg request, DataOutputStream out) throws Exception {
        String pan    = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de90   = net.safeGet(request, 90);

        log.info("[DMAS-ISS] === Reçu 0400 Reversal ===");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN (nouveau)   = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE90 Original Data    = {}", de90);

        String rc = doReverse(pan, amountS, de90);

        // Réponse 0410 : échoe DE2,3,4,7,11,90 + DE39
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

        net.send(out, resp);
        log.info("[DMAS-ISS] -> réponse 0410 DE39={} ({})", rc, rcLabel(rc));
    }

    /** Recrédite le solde en retrouvant la transaction via DE90. */
    private String doReverse(String pan, String amountS, String de90) {
        try {
            // Parser le DE90 : [MTI 4][STAN 6][DT 10][DE32 11][DE33 11]
            if (de90 == null || de90.length() < 20) {
                log.warn("[DMAS-ISS] DE90 absent ou invalide -> 30");
                return "30"; // format error
            }
            String origStan = de90.substring(4, 10);
            String origDt   = de90.substring(10, 20);
            log.info("[DMAS-ISS] Reversal : recherche tx originale STAN={} DT={}", origStan, origDt);

            // Retrouver la transaction originale
            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(origStan, origDt).orElse(null);
            if (tx == null) {
                log.info("[DMAS-ISS] Transaction originale introuvable -> 25");
                return "25"; // unable to locate record
            }
            if ("REVERSED".equals(tx.getStatus())) {
                log.info("[DMAS-ISS] Transaction déjà annulée (anti double-reversal) -> 00 (idempotent)");
                return "00"; // déjà reversée : on acquitte sans recréditer
            }

            // Recréditer le solde de la carte
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) {
                log.info("[DMAS-ISS] Carte introuvable pour reversal -> 14");
                return "14";
            }
            long amount = tx.getAmount() != null ? tx.getAmount() : 0L;
            card.setBalance(card.getBalance() + amount);
            cardRepo.save(card);

            // Marquer la transaction REVERSED
            tx.setStatus("REVERSED");
            tx.setReversedAt(java.time.LocalDateTime.now());
            txRepo.save(tx);

            log.info("[DMAS-ISS] Reversal APPROUVÉ -> 00 (recrédité {} centimes, nouveau solde={})",
                    amount, card.getBalance());
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur reversal : {}", e.getMessage(), e);
            return "96";
        }
    }

    /**
     * Traite un Authorization Advice/0120 (côté BANQUE).
     * - completion (DE90 présent) : ajuste le solde par rapport à la preauth originale
     * - advice simple (pas de DE90) : enregistre + débite la transaction offline
     * Accuse réception via 0130 en échoant le DE48 subelement 15.
     */
    private void handleAdvice(ISOMsg request, DataOutputStream out) throws Exception {
        String pan     = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de48    = net.safeGet(request, 48);
        String de90    = net.safeGet(request, 90);
        boolean isCompletion = (de90 != null && de90.length() >= 20);

        log.info("[DMAS-ISS] === Reçu 0120 Advice ({}) ===", isCompletion ? "COMPLETION" : "SIMPLE");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN             = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE48 sub15 AdviceDT   = {}", de48);
        if (isCompletion) log.info("[DMAS-ISS] DE90 Original (preauth)= {}", de90);

        String rc = isCompletion ? doCompletion(pan, amountS, de90) : doAdvice(pan, amountS, request);

        // Réponse 0130 : échoe DE2,3,4,7,11,90 + DE48 sub15 + DE39
        ISOMsg resp = new ISOMsg();
        resp.setPackager(net.getPackager());
        resp.setMTI("0130");
        if (request.hasField(2))  resp.set(2,  request.getString(2));
        if (request.hasField(3))  resp.set(3,  request.getString(3));
        if (request.hasField(4))  resp.set(4,  request.getString(4));
        if (request.hasField(7))  resp.set(7,  request.getString(7));
        if (request.hasField(11)) resp.set(11, request.getString(11));
        if (request.hasField(48)) resp.set(48, request.getString(48)); // écho DE48 sub15
        if (request.hasField(90)) resp.set(90, request.getString(90));
        resp.set(39, rc);

        net.send(out, resp);
        log.info("[DMAS-ISS] -> réponse 0130 DE39={} ({})", rc, rcLabel(rc));
    }

    /** Advice simple : enregistre + débite la transaction offline. */
    private String doAdvice(String pan, String amountS, ISOMsg request) {
        try {
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) { log.info("[DMAS-ISS] Advice : carte introuvable -> 14"); return "14"; }

            long amount = (amountS != null && !amountS.isEmpty()) ? Long.parseLong(amountS) : 0L;
            card.setBalance(card.getBalance() - amount);
            cardRepo.save(card);

            // Mémoriser comme transaction (advice = transaction effective)
            String stan = net.safeGet(request, 11);
            String dt   = net.safeGet(request, 7);
            McDmasTransaction tx = txRepo.findByStanAndTransmissionDt(stan, dt).orElseGet(McDmasTransaction::new);
            tx.setPan(pan); tx.setStan(stan); tx.setTransmissionDt(dt);
            tx.setMti("0120"); tx.setProcessingCode(net.safeGet(request, 3));
            tx.setAmount(amount); tx.setCurrency(net.safeGet(request, 49));
            tx.setResponseCode("00"); tx.setStatus("APPROVED");
            txRepo.save(tx);

            log.info("[DMAS-ISS] Advice simple : enregistré + débité {} (nouveau solde={}) -> 00", amount, card.getBalance());
            return "00";
        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur advice : {}", e.getMessage(), e);
            return "96";
        }
    }

    /** Completion : ajuste le solde par rapport à la preauth originale (rembourse ou débite la différence). */
    private String doCompletion(String pan, String finalAmountS, String de90) {
        try {
            String origStan = de90.substring(4, 10);
            String origDt   = de90.substring(10, 20);
            log.info("[DMAS-ISS] Completion : recherche preauth STAN={} DT={}", origStan, origDt);

            McDmasTransaction preauth = txRepo.findByStanAndTransmissionDt(origStan, origDt).orElse(null);
            if (preauth == null) { log.info("[DMAS-ISS] Preauth introuvable -> 25"); return "25"; }

            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) { log.info("[DMAS-ISS] Carte introuvable -> 14"); return "14"; }

            long estimated = preauth.getAmount() != null ? preauth.getAmount() : 0L;
            long finalAmt  = (finalAmountS != null && !finalAmountS.isEmpty()) ? Long.parseLong(finalAmountS) : 0L;
            long delta = finalAmt - estimated; // >0 = débiter plus ; <0 = rembourser

            // La preauth a débité 'estimated'. On ajuste de 'delta'.
            card.setBalance(card.getBalance() - delta);
            cardRepo.save(card);

            // Marquer la preauth comme complétée + maj montant final
            preauth.setStatus("COMPLETED");
            preauth.setAmount(finalAmt);
            txRepo.save(preauth);

            log.info("[DMAS-ISS] Completion : estimé={} final={} delta={} (nouveau solde={}) -> 00",
                    estimated, finalAmt, delta, card.getBalance());
            return "00";
        } catch (Exception e) {
            log.error("[DMAS-ISS] Erreur completion : {}", e.getMessage(), e);
            return "96";
        }
    }

    /**
     * Traite un Reversal Advice/0420 (cote BANQUE).
     * Le RESEAU (Stand-In) notifie un reversal traite en son nom (DE60 = raison, ex 402 timeout).
     * La banque applique le reversal (recredite si pas deja reverse) et accuse via 0430.
     */
    private void handleReversalAdvice(ISOMsg request, DataOutputStream out) throws Exception {
        String pan    = net.safeGet(request, 2);
        String amountS = net.safeGet(request, 4);
        String de60   = net.safeGet(request, 60);
        String de90   = net.safeGet(request, 90);

        log.info("[DMAS-ISS] === Recu 0420 Reversal Advice (Stand-In) ===");
        log.info("[DMAS-ISS] DE2  PAN              = {}", maskPan(pan));
        log.info("[DMAS-ISS] DE4  Amount           = {}", amountS);
        log.info("[DMAS-ISS] DE11 STAN             = {}", net.safeGet(request, 11));
        log.info("[DMAS-ISS] DE60 Advice Reason    = {} ({})", de60, adviceReasonLabel(de60));
        log.info("[DMAS-ISS] DE90 Original Data    = {}", de90);

        // Reutilise la logique de reversal (recredite si pas deja reverse)
        String rc = doReverse(pan, amountS, de90);

        // Reponse 0430 : echoe DE2,3,4,7,11,60,90 + DE39
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

        net.send(out, resp);
        log.info("[DMAS-ISS] -> reponse 0430 DE39={} ({})", rc, rcLabel(rc));
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

    private ISOMsg buildResponse(ISOMsg request, String rc) throws Exception {
        ISOMsg response = new ISOMsg();
        response.setPackager(net.getPackager());
        response.setMTI("0810");
        if (request.hasField(7))  response.set(7,  request.getString(7));
        if (request.hasField(11)) response.set(11, request.getString(11));
        if (request.hasField(70)) response.set(70, request.getString(70));
        response.set(39, rc);
        return response;
    }
}
