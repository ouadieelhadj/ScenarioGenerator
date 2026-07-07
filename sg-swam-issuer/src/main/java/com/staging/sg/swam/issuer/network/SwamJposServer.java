package com.staging.sg.swam.issuer.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.entity.SwamCard;
import com.staging.sg.common.entity.SwamIssTransaction;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.SwamLengthChannel;
import com.staging.sg.common.repository.NetworkRepository;
import com.staging.sg.common.repository.SwamCardRepository;
import com.staging.sg.common.repository.SwamIssTransactionRepository;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.entity.SwamIssKey;
import com.staging.sg.common.repository.SwamKekRepository;
import com.staging.sg.common.repository.SwamIssKeyRepository;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.SwamDe48;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

/**
 * Serveur jPOS cote CENTRE/SWITCH (SWAM issuer).
 * Logique d'autorisation REELLE (option a) : verifie la carte dans swam_cards,
 * debite le solde si suffisant, persiste dans swam_iss_transactions.
 *
 * Codes DE39 (spec HPS annexe A) :
 *   000 = approuve
 *   116 = solde insuffisant
 *   114 = compte inexistant (carte inconnue)
 *   062 = carte restreinte / inactive
 */
@Component
public class SwamJposServer {

    private static final Logger log = LoggerFactory.getLogger(SwamJposServer.class);
    private static final int DEFAULT_ISO_PORT = 8510;

    private final NetworkRepository networkRepository;
    private final SwamCardRepository cardRepository;
    private final SwamIssTransactionRepository txRepository;
    private final SwamKekRepository kekRepository;
    private final SwamIssKeyRepository issKeyRepository;
    private final JposHsmService hsm;

    private ISOServer isoServer;
    private Thread serverThread;

    public SwamJposServer(NetworkRepository networkRepository,
                          SwamCardRepository cardRepository,
                          SwamIssTransactionRepository txRepository,
                          SwamKekRepository kekRepository,
                          SwamIssKeyRepository issKeyRepository,
                          JposHsmService hsm) {
        this.networkRepository = networkRepository;
        this.cardRepository = cardRepository;
        this.txRepository = txRepository;
        this.kekRepository = kekRepository;
        this.issKeyRepository = issKeyRepository;
        this.hsm = hsm;
    }

    private int resolvePort() {
        try {
            Optional<NetworkRef> swam = networkRepository.findByCode("SWAM");
            if (swam.isPresent() && swam.get().getIssuerIsoPort() != null) {
                int p = swam.get().getIssuerIsoPort();
                log.info("[SWAM-SRV] Port ISO lu depuis networks : {}", p);
                return p;
            }
            log.warn("[SWAM-SRV] Port ISO absent en base, fallback {}", DEFAULT_ISO_PORT);
        } catch (Exception e) {
            log.warn("[SWAM-SRV] Lecture port base KO ({}), fallback {}", e.getMessage(), DEFAULT_ISO_PORT);
        }
        return DEFAULT_ISO_PORT;
    }

    @PostConstruct
    public void start() {
        int port = resolvePort();
        try {
            SwamPackager packager = new SwamPackager();
            SwamLengthChannel channel = new SwamLengthChannel();
            channel.setPackager(packager);
            isoServer = new ISOServer(port, channel, null);
            isoServer.addISORequestListener(new SwamListener());
            serverThread = new Thread(isoServer, "swam-jpos-server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("[SWAM-SRV] ISOServer demarre sur :{} (SwamLengthChannel/ASCII)", port);
        } catch (Exception e) {
            log.error("[SWAM-SRV] Echec demarrage : {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (isoServer != null) isoServer.shutdown();
        if (serverThread != null) serverThread.interrupt();
        log.info("[SWAM-SRV] ISOServer arrete");
    }

    private class SwamListener implements ISORequestListener {
        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti = m.getMTI();
                log.info("[SWAM-SRV] Recu MTI={} STAN={}", mti, m.hasField(11) ? m.getString(11) : "?");
                if ("1804".equals(mti)) return handleNetwork(source, m);
                if ("1100".equals(mti)) return handleAuthorization(source, m);
                log.warn("[SWAM-SRV] MTI non gere : {}", mti);
                return false;
            } catch (Exception e) {
                log.error("[SWAM-SRV] Erreur : {}", e.getMessage(), e);
                return false;
            }
        }

        private boolean handleNetwork(ISOSource source, ISOMsg m) throws Exception {
            String func = m.hasField(24) ? m.getString(24) : "?";
            String label = switch (func) {
                case "801" -> "SIGN-ON"; case "803" -> "ECHO-TEST";
                case "802" -> "SIGN-OFF"; default -> "FUNC-" + func;
            };
            log.info("[SWAM-SRV] Gestion reseau {} (DE24={})", label, func);
            if ("811".equals(func) || "899".equals(func)) {
                return handleKeyExchange(source, m, func);
            }
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1814");
            r.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(24)) r.set(24, m.getString(24));
            r.set(39, "800");
            source.send(r);
            log.info("[SWAM-SRV] Repondu 1814 DE39=800 ({})", label);
            return true;
        }

        /**
         * Echange de cles (spec HPS) : le CENTRE genere la cle sous ZMK et la
         * renvoie dans le 1814 (DE48). 811 -> ZPK (P16), 899 -> ZAK (P10).
         * Persiste dans swam_iss_keys.
         */
        private boolean handleKeyExchange(ISOSource source, ISOMsg m, String func) throws Exception {
            String mgid = "TESTGRP01";
            String keyType = "811".equals(func) ? "PEK" : "MAK";
            String tagKey  = "811".equals(func) ? SwamDe48.TAG_ZPK : SwamDe48.TAG_ZAK;
            String tagKcv  = "811".equals(func) ? SwamDe48.TAG_ZPK_KCV : SwamDe48.TAG_ZAK_KCV;

            SwamKek kek = kekRepository.findByMemberGroupId(mgid)
                    .orElseThrow(() -> new IllegalStateException("KEK SWAM introuvable pour " + mgid));
            if (kek.getKekClear() == null)
                throw new IllegalStateException("kek_clear absent pour " + mgid);

            // Generation cote CENTRE : ZPK double (16o) / ZAK simple (8o)
            HsmService.KeyResult gen = "811".equals(func)
                    ? hsm.generateWorkingKey(keyType, 16, kek.getKekClear())
                    : hsm.generateWorkingKeySingle(keyType, kek.getKekClear());
            int keyLen = gen.keyUnderKekHex.length() / 2;

            // Persister la cle emise cote issuer
            SwamIssKey ik = issKeyRepository
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, keyType, "ACTIVE")
                    .orElseGet(SwamIssKey::new);
            ik.setMemberGroupId(mgid);
            ik.setKeyType(keyType);
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(gen.keyUnderLmkHex);
            ik.setKeyUnderKek(gen.keyUnderKekHex.length() > 64 ? gen.keyUnderKekHex.substring(0,64) : gen.keyUnderKekHex);
            ik.setKcv(gen.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepository.save(ik);
            log.info("[SWAM-SRV] {} genere+persiste (KCV={}, {}hex) -> {}",
                    keyType, gen.kcv, gen.keyUnderKekHex.length(), tagKey);

            // Construire DE48 = tagKey<cle hex> + tagKcv<kcv>
            String de48 = new SwamDe48()
                    .put(tagKey, gen.keyUnderKekHex)
                    .put(tagKcv, gen.kcv)
                    .build();

            // Reponse 1814 DE39=800 + DE48
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1814");
            r.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(24)) r.set(24, m.getString(24));
            r.set(39, "800");
            r.set(48, de48);
            source.send(r);
            log.info("[SWAM-SRV] Repondu 1814 DE39=800 (key exchange {}) DE48len={}", func, de48.length());
            return true;
        }

        /**
         * Logique d'autorisation (a) : debit reel.
         * Cherche la carte, verifie statut + solde, debite si OK, persiste.
         */
        private boolean handleAuthorization(ISOSource source, ISOMsg m) throws Exception {
            String pan = m.hasField(2) ? m.getString(2) : null;
            long amount = m.hasField(4) ? Long.parseLong(m.getString(4)) : 0L;
            String stan = m.hasField(11) ? m.getString(11) : "";
            log.info("[SWAM-SRV] Autorisation 1100 PAN={} montant={}", maskPan(pan), amount);

            String responseCode;
            String status;

            Optional<SwamCard> opt = (pan != null) ? cardRepository.findByPan(pan) : Optional.empty();
            if (opt.isEmpty()) {
                responseCode = "114";                 // compte inexistant
                status = "DECLINED";
                log.info("[SWAM-SRV] Carte inconnue -> DE39=114");
            } else {
                SwamCard card = opt.get();
                if (!"ACTIVE".equals(card.getStatus())) {
                    responseCode = "062";             // carte restreinte/inactive
                    status = "DECLINED";
                    log.info("[SWAM-SRV] Carte inactive ({}) -> DE39=062", card.getStatus());
                } else if (card.getBalance() < amount) {
                    responseCode = "116";             // solde insuffisant
                    status = "DECLINED";
                    log.info("[SWAM-SRV] Solde insuffisant ({} < {}) -> DE39=116", card.getBalance(), amount);
                } else {
                    // Debit reel
                    card.setBalance(card.getBalance() - amount);
                    card.setUpdatedAt(java.time.LocalDateTime.now());
                    cardRepository.save(card);
                    responseCode = "000";             // approuve
                    status = "APPROVED";
                    log.info("[SWAM-SRV] APPROUVE, nouveau solde={} -> DE39=000", card.getBalance());
                }
            }

            // Persister la transaction cote issuer
            try {
                SwamIssTransaction tx = new SwamIssTransaction();
                tx.setPan(pan != null ? pan : "");
                tx.setStan(stan);
                tx.setTransmissionDt(m.hasField(7) ? m.getString(7) : "");
                tx.setMti("1100");
                tx.setProcessingCode(m.hasField(3) ? m.getString(3) : null);
                tx.setAmount(amount);
                tx.setCurrency(m.hasField(49) ? m.getString(49) : null);
                tx.setResponseCode(responseCode);
                tx.setStatus(status);
                txRepository.save(tx);
            } catch (Exception e) {
                log.error("[SWAM-SRV] Persistance tx KO : {}", e.getMessage());
            }

            // Reponse 1110
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1110");
            if (m.hasField(2))  r.set(2,  m.getString(2));
            if (m.hasField(3))  r.set(3,  m.getString(3));
            if (m.hasField(4))  r.set(4,  m.getString(4));
            r.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(12)) r.set(12, m.getString(12));
            if (m.hasField(32)) r.set(32, m.getString(32));
            if (m.hasField(37)) r.set(37, m.getString(37));
            if ("000".equals(responseCode)) r.set(38, "123456");
            r.set(39, responseCode);
            if (m.hasField(41)) r.set(41, m.getString(41));
            if (m.hasField(49)) r.set(49, m.getString(49));
            source.send(r);
            log.info("[SWAM-SRV] Repondu 1110 DE39={}", responseCode);
            return true;
        }

        private String maskPan(String pan) {
            if (pan == null || pan.length() < 10) return pan;
            return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
        }
    }
}
