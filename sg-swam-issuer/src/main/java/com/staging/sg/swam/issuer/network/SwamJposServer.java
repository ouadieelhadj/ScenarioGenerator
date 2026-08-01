package com.staging.sg.swam.issuer.network;

import com.staging.sg.common.entity.SwamIssuerCard;
import com.staging.sg.common.entity.SwamIssTransaction;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.SwamLengthChannel;
import com.staging.sg.common.service.SwamInterfaceService;
import com.staging.sg.common.repository.SwamIssuerCardRepository;
import com.staging.sg.common.repository.SwamIssTransactionRepository;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.entity.SwamIssKey;
import com.staging.sg.common.repository.SwamKekRepository;
import com.staging.sg.common.repository.SwamIssKeyRepository;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.SwamDe48;
import com.staging.sg.common.iso.crypto.SwamMacBuilder;
import com.staging.sg.common.iso.sid.SidMessageValidator;
import com.staging.sg.common.iso.sid.SidValidationException;
import com.staging.sg.common.iso.sid.SidTransactionPersistenceMapper;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serveur jPOS cote CENTRE/SWITCH (SWAM issuer).
 *
 * Flux conforme spec HPS + logs reels du membre Way4 (section 20 du SESSION_RESUME) :
 *   1. Membre -> Switch : 1804 DE24=801 (Sign-on)
 *   2. Switch -> Membre : 1814 DE39=800 (Reponse sign-on)
 *   3. Switch -> Membre : 1804 DE24=811 (ZPK poussee par le switch, DE48 = P16<len>X<cle>)
 *   4. Membre -> Switch : 1814 DE39=800 (Accuse reception ZPK)
 *   5. Membre -> Switch : 1100 (Transaction)
 *   6. Switch -> Membre : 1110 (Reponse transaction)
 *
 * MAC (section 20.7) :
 *   - cle    = la ZMK utilisee comme TAK (double longueur). Il n'y a PAS de ZAK.
 *   - algo   = 3DES-CBC-MAC (ISO 9797 Algorithm 1), padding Method 1 (zeros).
 *   - donnee = message packe SANS MTI, SANS bitmap, SANS DE128 (cf SwamMacBuilder).
 *   - DE128  = les swam.mac.length premiers octets du MAC (4 en reel HPS).
 */
@Component
public class SwamJposServer {

    private static final Logger log = LoggerFactory.getLogger(SwamJposServer.class);
    private final SwamInterfaceService interfaceService;
    private final SwamIssuerCardRepository cardRepository;
    private final SwamIssTransactionRepository txRepository;
    private final SwamKekRepository kekRepository;
    private final SwamIssKeyRepository issKeyRepository;
    private final JposHsmService hsm;
    private final SwamIssuingAdapter issuingAdapter;

    /** Longueur du DE128 en octets. 4 = mode HPS reel (doit matcher SwamPackager). */
    @Value("${swam.mac.length:4}")        private int macLength;
    @Value("${swam.mac.reject-code:916}") private String macRejectCode;
    @Value("${swam.mac.enforce:true}")    private boolean macEnforce;

    /** Push spontane de la ZPK apres le sign-on (flux HPS reel). */
    @Value("${swam.keypush.enabled:true}") private boolean keyPushEnabled;
    @Value("${swam.authorization.owner:LOCAL_ISSUING}")
    private String authorizationOwner;

    private ISOServer isoServer;
    private Thread serverThread;
    private volatile ISOSource permanentSource;
    private final ConcurrentHashMap<String, ISOMsg> initiatedResponses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> initiatedLatches = new ConcurrentHashMap<>();

    /** Compteur STAN pour les messages spontanes du switch. */
    private final AtomicInteger stanCounter = new AtomicInteger(900000);

    public ISOMsg initiatePurchase(String pan, String amount) throws Exception {
        return initiateTransaction(pan, amount, false);
    }

    public ISOMsg initiateFinancial(String pan, String amount) throws Exception {
        return initiateTransaction(pan, amount, true);
    }

    private ISOMsg initiateTransaction(String pan, String amount, boolean financial) throws Exception {
        ISOSource source = permanentSource;
        if (source == null) {
            throw new IllegalStateException("Liaison permanente SWAM non etablie");
        }
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        Date now = new Date();
        ISOMsg request = new ISOMsg();
        request.setPackager(new SwamPackager());
        request.setMTI(financial ? "1200" : "1100");
        request.set(2, pan);
        request.set(3, "000000");
        request.set(4, amount);
        if (financial) request.set(5, amount);
        request.set(6, amount);
        request.set(7, new SimpleDateFormat("yyMMddHHmm").format(now));
        request.set(10, "61000000");
        if (financial) request.set(9, "61000000");
        request.set(11, stan);
        request.set(12, new SimpleDateFormat("yyMMddHHmmss").format(now));
        request.set(14, "2712");
        request.set(15, new SimpleDateFormat("yyMMdd").format(now));
        request.set(16, new SimpleDateFormat("MMdd").format(now));
        request.set(18, "5411");
        request.set(19, "504");
        request.set(21, "504");
        request.set(22, "P10101511004");
        request.set(24, financial ? "200" : "100");
        request.set(32, forwardingId());
        request.set(33, forwardingId());
        request.set(37, stan + "000000");
        request.set(41, "SWITCH01");
        request.set(42, "SWITCHMERCHANT ");
        request.set(43, "SWAM SWITCH CASABLANCA MA");
        request.set(49, "504");
        if (financial) request.set(50, "504");
        request.set(51, "504");
        request.set(53, "0099000000");
        request.set(61, "061012" + request.getString(22));
        request.set(124, memberGroupId());
        poseMac(request);
        SidMessageValidator.validate(request);

        CountDownLatch latch = new CountDownLatch(1);
        initiatedLatches.put(stan, latch);
        source.send(request);
        boolean received = latch.await(10, TimeUnit.SECONDS);
        initiatedLatches.remove(stan);
        ISOMsg response = initiatedResponses.remove(stan);
        if (!received || response == null) {
            throw new IllegalStateException("Timeout transaction initiee par le switch STAN=" + stan);
        }
        SidMessageValidator.validateResponseTo(request, response);
        persistOutgoingTransaction(request, response);
        return response;
    }

    public boolean hasPermanentConnection() {
        return permanentSource != null;
    }

    private void poseMac(ISOMsg message) {
        try {
            SwamKek kek = kekRepository.findByMemberGroupId(memberGroupId()).orElse(null);
            if (kek == null || kek.getKekClear() == null) return;
            byte[] full = hsm.generateMacZmk(SwamMacBuilder.build(message), kek.getKekClear());
            message.set(128, macLength > 0 && macLength < full.length
                    ? Arrays.copyOfRange(full, 0, macLength) : full);
        } catch (Exception e) {
            throw new IllegalStateException("Calcul MAC switch impossible", e);
        }
    }

    private void persistOutgoingTransaction(ISOMsg request, ISOMsg response) throws Exception {
        String responseCode = response.hasField(39) ? response.getString(39) : null;
        SwamIssTransaction tx = new SwamIssTransaction();
        tx.setPan(request.getString(2));
        tx.setStan(request.getString(11));
        tx.setTransmissionDt(request.getString(7));
        tx.setMti(request.getMTI());
        tx.setProcessingCode(request.getString(3));
        tx.setAmount(Long.parseLong(request.getString(4)));
        tx.setCurrency(request.getString(49));
        tx.setResponseCode(responseCode);
        tx.setStatus("000".equals(responseCode) ? "APPROVED" : "DECLINED");
        SidTransactionPersistenceMapper.populate(tx, request, response);
        txRepository.save(tx);
    }

    public SwamJposServer(SwamInterfaceService interfaceService,
                          SwamIssuerCardRepository cardRepository,
                          SwamIssTransactionRepository txRepository,
                          SwamKekRepository kekRepository,
                          SwamIssKeyRepository issKeyRepository,
                          JposHsmService hsm,
                          SwamIssuingAdapter issuingAdapter) {
        this.interfaceService = interfaceService;
        this.cardRepository = cardRepository;
        this.txRepository = txRepository;
        this.kekRepository = kekRepository;
        this.issKeyRepository = issKeyRepository;
        this.hsm = hsm;
        this.issuingAdapter = issuingAdapter;
    }

    private int resolvePort() {
        Integer port = interfaceService.get().getIsoPort();
        if (port == null) {
            throw new IllegalStateException("[SWAM-IF] iso_port obligatoire cote issuer");
        }
        return port;
    }

    private String forwardingId() {
        String value = interfaceService.get().getIssuerCodeDe33();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] issuer_code_de33 obligatoire");
        }
        return value;
    }

    private String memberGroupId() {
        String value = interfaceService.get().getMemberGroupId();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] member_group_id obligatoire");
        }
        return value;
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
            log.info("[SWAM-SRV] ISOServer demarre sur :{} (SwamLengthChannel/ASCII) — keyPush={} macLen={}o",
                    port, keyPushEnabled, macLength);
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
                permanentSource = source;
                String mti = m.getMTI();
                log.info("[SWAM-SRV] Recu MTI={} STAN={}", mti, m.hasField(11) ? m.getString(11) : "?");
                if (("1110".equals(mti) || "1210".equals(mti)) && m.hasField(11)) {
                    String stan = m.getString(11);
                    initiatedResponses.put(stan, m);
                    CountDownLatch latch = initiatedLatches.get(stan);
                    if (latch != null) latch.countDown();
                    return true;
                }
                if ("1804".equals(mti)) return handleNetwork(source, m);
                if ("1814".equals(mti)) return handleNetworkResponse(m);   // accuse reception ZPK
                if ("1100".equals(mti)) return handleAuthorization(source, m);
                if ("1200".equals(mti)) return handleFinancial(source, m, "1210");
                if ("1220".equals(mti) || "1221".equals(mti))
                    return handleFinancial(source, m, "1230");
                if ("1420".equals(mti) || "1421".equals(mti))
                    return handleReversal(source, m);
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

            // ── Key exchange A LA DEMANDE du membre (811/899) ───────────────
            // Conserve pour compatibilite avec notre acquereur SWAM interne.
            // Le vrai membre HPS n'utilise PAS ce chemin (il attend le push).
            if ("811".equals(func) || "899".equals(func)) {
                return handleKeyExchange(source, m, func);
            }

            // ── Reponse 1814 au membre ──────────────────────────────────────
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1814");
            r.set(7, new SimpleDateFormat("yyMMddHHmm").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(12)) r.set(12, m.getString(12));
            if (m.hasField(24)) r.set(24, m.getString(24));
            if (m.hasField(25)) r.set(25, m.getString(25));
            r.set(33, forwardingId());
            if (m.hasField(37)) r.set(37, m.getString(37));
            r.set(39, "800");
            poseMacOnResponse(r);
            source.send(r);
            log.info("[SWAM-SRV] Repondu 1814 DE39=800 ({})", label);

            // ── Apres sign-on : pousser la ZPK spontanement (flux HPS reel) ─
            if ("801".equals(func) && keyPushEnabled) {
                pushZpk(source, m.getPackager());
            }
            return true;
        }

        /**
         * Key exchange A LA DEMANDE : le membre envoie 1804 DE24=811/899 (sans cle),
         * le CENTRE genere la cle sous ZMK et la renvoie dans le 1814 (DE48).
         * 811 -> ZPK (P16), 899 -> ZAK (P10). Persiste dans swam_iss_keys.
         * CHEMIN INTERNE (notre acquereur) — pas utilise par le vrai membre.
         */
        private boolean handleKeyExchange(ISOSource source, ISOMsg m, String func) throws Exception {
            String keyType = "811".equals(func) ? "PEK" : "MAK";
            String tagKey  = "811".equals(func) ? SwamDe48.TAG_ZPK : SwamDe48.TAG_ZAK;
            String tagKcv  = "811".equals(func) ? SwamDe48.TAG_ZPK_KCV : SwamDe48.TAG_ZAK_KCV;

            SwamKek kek = kekRepository.findByMemberGroupId(memberGroupId())
                    .orElseThrow(() -> new IllegalStateException(
                            "KEK SWAM introuvable pour " + memberGroupId()));
            if (kek.getKekClear() == null)
                throw new IllegalStateException("kek_clear absent pour " + memberGroupId());

            HsmService.KeyResult gen = "811".equals(func)
                    ? hsm.generateWorkingKey(keyType, 16, kek.getKekClear())
                    : hsm.generateWorkingKeySingle(keyType, kek.getKekClear());
            int keyLen = gen.keyUnderKekHex.length() / 2;

            SwamIssKey ik = issKeyRepository
                    .findByMemberGroupIdAndKeyTypeAndStatus(
                            memberGroupId(), keyType, "ACTIVE")
                    .orElseGet(SwamIssKey::new);
            ik.setMemberGroupId(memberGroupId());
            ik.setKeyType(keyType);
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(gen.keyUnderLmkHex);
            ik.setKeyUnderKek(gen.keyUnderKekHex.length() > 64 ? gen.keyUnderKekHex.substring(0,64) : gen.keyUnderKekHex);
            ik.setKcv(gen.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepository.save(ik);
            log.info("[SWAM-SRV] {} genere+persiste (KCV={}, {}hex) -> {}",
                    keyType, gen.kcv, gen.keyUnderKekHex.length(), tagKey);

            String de48 = new SwamDe48()
                    .put(tagKey, gen.keyUnderKekHex)
                    .put(tagKcv, gen.kcv)
                    .build();

            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1814");
            r.set(7, new SimpleDateFormat("yyMMddHHmm").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(12)) r.set(12, m.getString(12));
            if (m.hasField(24)) r.set(24, m.getString(24));
            if (m.hasField(25)) r.set(25, m.getString(25));
            r.set(33, forwardingId());
            if (m.hasField(37)) r.set(37, m.getString(37));
            r.set(39, "800");
            r.set(48, de48);
            poseMacOnResponse(r);
            source.send(r);
            log.info("[SWAM-SRV] Repondu 1814 DE39=800 (key exchange {}) DE48len={}", func, de48.length());
            return true;
        }

        /**
         * Le switch POUSSE la ZPK au membre apres le sign-on (flux HPS reel).
         * 1804 DE24=811, DE48 = P16<len>X<ZPK hex sous ZMK>.
         * Le prefixe 'X' est OBLIGATOIRE et compte dans la longueur (P16033X + 32 hex).
         */
        private void pushZpk(ISOSource source, ISOPackager packager) {
            try {
                SwamKek kek = kekRepository.findByMemberGroupId(memberGroupId()).orElse(null);
                if (kek == null || kek.getKekClear() == null) {
                    log.warn("[SWAM-SRV] KEK absente -> ZPK non poussee (bootstrap d'abord)");
                    return;
                }

                // ZPK double longueur (16 octets), chiffree sous ZMK
                HsmService.KeyResult gen = hsm.generateWorkingKey("PEK", 16, kek.getKekClear());

                SwamIssKey ik = issKeyRepository
                        .findByMemberGroupIdAndKeyTypeAndStatus(
                                memberGroupId(), "PEK", "ACTIVE")
                        .orElseGet(SwamIssKey::new);
                ik.setMemberGroupId(memberGroupId());
                ik.setKeyType("PEK");
                ik.setKeyLength(gen.keyUnderKekHex.length() / 2);
                ik.setKeyUnderLmk(gen.keyUnderLmkHex);
                ik.setKeyUnderKek(gen.keyUnderKekHex.length() > 64 ? gen.keyUnderKekHex.substring(0,64) : gen.keyUnderKekHex);
                ik.setKcv(gen.kcv);
                ik.setStatus("ACTIVE");
                issKeyRepository.save(ik);
                log.info("[SWAM-SRV] ZPK generee+persistee (KCV={})", gen.kcv);

                // DE48 format HPS reel : P16 + longueur(3) + 'X' + cle hex
                String de48 = new SwamDe48()
                        .put(SwamDe48.TAG_ZPK, "X" + gen.keyUnderKekHex)
                        .build();

                String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
                Date now = new Date();

                ISOMsg push = new ISOMsg();
                push.setPackager(packager);
                push.setMTI("1804");
                push.set(7,  new SimpleDateFormat("yyMMddHHmm").format(now));
                push.set(11, stan);
                push.set(12, new SimpleDateFormat("yyMMddHHmmss").format(now));
                push.set(24, "811");
                push.set(25, "0000");
                push.set(33, forwardingId());        // LLVAR : le packager pose la longueur
                push.set(37, stan + "000000");
                push.set(48, de48);
                poseMacOnResponse(push);
                source.send(push);
                log.info("[SWAM-SRV] ZPK poussee -> 1804 DE24=811 STAN={} DE48=[{}] KCV={}",
                        stan, de48, gen.kcv);

            } catch (Exception e) {
                log.error("[SWAM-SRV] pushZpk erreur : {}", e.getMessage(), e);
            }
        }

        /** Traite le 1814 recu du membre en reponse au key push. */
        private boolean handleNetworkResponse(ISOMsg m) {
            String func = m.hasField(24) ? m.getString(24) : "?";
            String de39 = m.hasField(39) ? m.getString(39) : "?";
            String stan = m.hasField(11) ? m.getString(11) : "?";
            log.info("[SWAM-SRV] Recu 1814 DE24={} DE39={} STAN={} (accuse reception key push)", func, de39, stan);
            return true;
        }

        /** Logique d'autorisation (a) : debit reel. */
        private boolean handleAuthorization(ISOSource source, ISOMsg m) throws Exception {
            try {
                SidMessageValidator.validate(m);
            } catch (SidValidationException e) {
                log.warn("[SWAM-SRV] 1100 non conforme SID: {}", e.getMessage());
                return sendFormatError(source, m, "1110");
            }
            String pan = m.hasField(2) ? m.getString(2) : null;
            long amount = m.hasField(4) ? Long.parseLong(m.getString(4)) : 0L;
            String stan = m.hasField(11) ? m.getString(11) : "";
            log.info("[SWAM-SRV] Autorisation 1100 PAN={} montant={}", maskPan(pan), amount);

            String macCheck = verifyIncomingMac(m);
            if (macEnforce && "FAIL".equals(macCheck)) {
                log.warn("[SWAM-SRV] MAC invalide -> rejet DE39={}", macRejectCode);
                ISOMsg rr = new ISOMsg();
                rr.setPackager(m.getPackager());
                rr.setMTI("1110");
                if (m.hasField(2))  rr.set(2,  m.getString(2));
                if (m.hasField(11)) rr.set(11, m.getString(11));
                if (m.hasField(37)) rr.set(37, m.getString(37));
                rr.set(39, macRejectCode);
                poseMacOnResponse(rr);
                source.send(rr);
                return true;
            }

            SwamIssuingAdapter.Decision decision =
                    "EXTERNAL_MEMBER_SIMULATOR".equals(authorizationOwner)
                            ? authorizeExternalMember(m)
                            : issuingAdapter.authorize(m);
            String responseCode = decision.responseCode();
            String status = decision.status();

            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("1110");
            copyFields(m, r, 2,3,4,5,6,7,9,10,11,12,15,16,32,33,37,41,42,49,50,51);
            r.set(27, "6");
            if (decision.authorizationCode() != null) {
                r.set(38, decision.authorizationCode());
            }
            r.set(39, responseCode);
            if (decision.arpcHex() != null) {
                r.set(55, ISOUtil.hex2byte(decision.arpcHex()));
            }
            poseMacOnResponse(r);
            SidMessageValidator.validateResponseTo(m, r);

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
                SidTransactionPersistenceMapper.populate(tx, m, r);
                txRepository.save(tx);
            } catch (Exception e) {
                log.error("[SWAM-SRV] Persistance tx KO : {}", e.getMessage());
            }

            source.send(r);
            log.info("[SWAM-SRV] Repondu 1110 DE39={}", responseCode);
            return true;
        }

        private SwamIssuingAdapter.Decision authorizeExternalMember(ISOMsg message) {
            String pan = message.hasField(2) ? message.getString(2) : null;
            long amount = message.hasField(4)
                    ? Long.parseLong(message.getString(4)) : 0L;
            SwamIssuerCard card = cardRepository.findByPan(pan).orElse(null);
            String responseCode;
            if (card == null) {
                responseCode = "114";
            } else if (!"ACTIVE".equals(card.getStatus())) {
                responseCode = "062";
            } else if (card.getBalance() < amount) {
                responseCode = "116";
            } else {
                card.setBalance(card.getBalance() - amount);
                card.setUpdatedAt(java.time.LocalDateTime.now());
                cardRepository.save(card);
                responseCode = "000";
            }
            log.info("[SWAM-SWITCH] Decision transmise par le membre emetteur simule DE39={}",
                    responseCode);
            String authCode = "000".equals(responseCode)
                    ? authorizationCode(message.hasField(11) ? message.getString(11) : "0")
                    : null;
            return new SwamIssuingAdapter.Decision(
                    responseCode, authCode, null,
                    "000".equals(responseCode) ? "APPROVED" : "DECLINED", false);
        }

        private boolean sendFormatError(ISOSource source, ISOMsg request, String responseMti)
                throws Exception {
            ISOMsg response = new ISOMsg();
            response.setPackager(request.getPackager());
            response.setMTI(responseMti);
            copyFields(request, response, 2,3,4,5,6,7,9,10,11,12,15,16,32,33,37,41,42,49,50,51);
            response.set(27, "6");
            response.set(38, authorizationCode(request.hasField(11) ? request.getString(11) : "0"));
            response.set(39, "904");
            poseMacOnResponse(response);
            source.send(response);
            return true;
        }

        private boolean handleFinancial(ISOSource source, ISOMsg request, String responseMti)
                throws Exception {
            try {
                SidMessageValidator.validate(request);
            } catch (SidValidationException e) {
                log.warn("[SWAM-SRV] {} non conforme SID: {}", request.getMTI(), e.getMessage());
                return sendFormatError(source, request, responseMti);
            }

            String pan = request.getString(2);
            long amount = Long.parseLong(request.getString(4));
            String stan = request.getString(11);
            String transmission = request.getString(7);

            Optional<SwamIssTransaction> duplicate =
                    txRepository.findByStanAndTransmissionDt(stan, transmission);
            if (duplicate.isPresent()) {
                SwamIssTransaction previous = duplicate.get();
                ISOMsg response = financialResponse(
                        request, responseMti, previous.getResponseCode(),
                        previous.getAuthorizationCode());
                source.send(response);
                log.info("[SWAM-SRV] Rejeu {} idempotent STAN={}", request.getMTI(), stan);
                return true;
            }

            String responseCode;
            Optional<SwamIssuerCard> cardOpt = cardRepository.findByPan(pan);
            if (cardOpt.isEmpty()) {
                responseCode = "114";
            } else if (!"ACTIVE".equals(cardOpt.get().getStatus())) {
                responseCode = "062";
            } else if (request.hasField(52) && !verifyPin(request, pan, cardOpt.get().getPin())) {
                responseCode = "117";
            } else if (cardOpt.get().getBalance() < amount) {
                responseCode = "116";
            } else {
                SwamIssuerCard card = cardOpt.get();
                card.setBalance(card.getBalance() - amount);
                card.setUpdatedAt(java.time.LocalDateTime.now());
                cardRepository.save(card);
                responseCode = "000";
            }

            String authCode = request.hasField(38)
                    ? request.getString(38) : authorizationCode(stan);
            ISOMsg response = financialResponse(request, responseMti, responseCode, authCode);
            persistIssuerTransaction(request, response, responseCode);
            source.send(response);
            log.info("[SWAM-SRV] Repondu {} a {} DE39={}",
                    responseMti, request.getMTI(), responseCode);
            return true;
        }

        private ISOMsg financialResponse(
                ISOMsg request, String responseMti, String responseCode, String authCode)
                throws Exception {
            ISOMsg response = new ISOMsg();
            response.setPackager(request.getPackager());
            response.setMTI(responseMti);
            copyFields(request, response,
                    2,3,4,5,6,7,9,10,11,12,15,16,32,33,37,41,42,46,49,50,51,60);
            if ("1210".equals(responseMti)) response.set(27, "6");
            response.set(38, authCode);
            response.set(39, responseCode);
            poseMacOnResponse(response);
            SidMessageValidator.validateResponseTo(request, response);
            return response;
        }

        private boolean handleReversal(ISOSource source, ISOMsg request) throws Exception {
            try {
                SidMessageValidator.validate(request);
            } catch (SidValidationException e) {
                log.warn("[SWAM-SRV] {} non conforme SID: {}", request.getMTI(), e.getMessage());
                return sendFormatError(source, request, "1430");
            }

            String stan = request.getString(11);
            String transmission = request.getString(7);
            Optional<SwamIssTransaction> duplicate =
                    txRepository.findByStanAndTransmissionDt(stan, transmission);
            if (duplicate.isPresent()) {
                ISOMsg response = reversalResponse(
                        request, duplicate.get().getResponseCode(),
                        duplicate.get().getAuthorizationCode());
                source.send(response);
                return true;
            }

            String rrn = request.getString(37);
            Optional<SwamIssTransaction> original =
                    txRepository.findFirstByRrnAndClearingEligibleTrueOrderByCreatedAtDesc(rrn);
            String responseCode = original.isPresent() ? "000" : "025";
            String authCode = request.hasField(38)
                    ? request.getString(38) : authorizationCode(stan);

            if (original.isPresent()) {
                SwamIssTransaction financial = original.get();
                long reversalAmount = Long.parseLong(request.getString(4));
                SwamIssuerCard card = cardRepository.findByPan(financial.getPan()).orElseThrow();
                card.setBalance(card.getBalance() + reversalAmount);
                card.setUpdatedAt(java.time.LocalDateTime.now());
                cardRepository.save(card);
                boolean partial = "402".equals(request.getString(24));
                long currentClearingAmount = financial.getClearingAmount() != null
                        ? financial.getClearingAmount() : financial.getAmount();
                long remaining = Math.max(0L, currentClearingAmount - reversalAmount);
                financial.setClearingAmount(remaining);
                financial.setClearingEligible(partial && remaining > 0L);
                financial.setLifecycleStatus(partial && remaining > 0L
                        ? "PARTIALLY_REVERSED" : "REVERSED");
                financial.setReversedAt(java.time.LocalDateTime.now());
                txRepository.save(financial);
            }

            ISOMsg response = reversalResponse(request, responseCode, authCode);
            persistIssuerTransaction(request, response, responseCode);
            source.send(response);
            return true;
        }

        private ISOMsg reversalResponse(ISOMsg request, String responseCode, String authCode)
                throws Exception {
            ISOMsg response = new ISOMsg();
            response.setPackager(request.getPackager());
            response.setMTI("1430");
            copyFields(request, response,
                    2,3,4,5,6,7,11,12,15,16,32,33,37,41,42,43,49,50,51,53);
            response.set(38, authCode);
            response.set(39, responseCode);
            poseMacOnResponse(response);
            SidMessageValidator.validateResponseTo(request, response);
            return response;
        }

        private void persistIssuerTransaction(
                ISOMsg request, ISOMsg response, String responseCode) throws Exception {
            SwamIssTransaction tx = new SwamIssTransaction();
            tx.setPan(request.getString(2));
            tx.setStan(request.getString(11));
            tx.setTransmissionDt(request.getString(7));
            tx.setMti(request.getMTI());
            tx.setProcessingCode(request.getString(3));
            tx.setAmount(Long.parseLong(request.getString(4)));
            tx.setCurrency(request.getString(49));
            tx.setResponseCode(responseCode);
            tx.setStatus("000".equals(responseCode) ? "APPROVED" : "DECLINED");
            SidTransactionPersistenceMapper.populate(tx, request, response);
            txRepository.save(tx);
        }

        private void copyFields(ISOMsg source, ISOMsg target, int... fields) throws ISOException {
            for (int field : fields) {
                if (source.hasField(field)) {
                    if (source.getComponent(field).getValue() instanceof byte[]) {
                        target.set(field, source.getBytes(field));
                    } else {
                        target.set(field, source.getString(field));
                    }
                }
            }
        }

        private String authorizationCode(String stan) {
            String digits = stan == null ? "" : stan.replaceAll("\\D", "");
            return String.format("%6s", digits).replace(' ', '0')
                    .substring(Math.max(0, String.format("%6s", digits).length() - 6));
        }

        // ====================================================================
        //  MAC SWAM REEL : cle = ZMK (comme TAK), 3DES-CBC-MAC, DE128 tronque
        // ====================================================================

        /** @return "OK" | "FAIL" | "SKIP" */
        private String verifyIncomingMac(ISOMsg m) {
            try {
                if (!m.hasField(128)) {
                    log.info("[SWAM-SRV] DE128 absent -> MAC non verifie (SKIP)");
                    return "SKIP";
                }
                SwamKek kek = kekRepository.findByMemberGroupId(memberGroupId()).orElse(null);
                if (kek == null || kek.getKekClear() == null) {
                    log.warn("[SWAM-SRV] ZMK claire absente -> MAC non verifie (SKIP)");
                    return "SKIP";
                }
                byte[] input = SwamMacBuilder.build(m);
                byte[] rxMac = m.getBytes(128);
                boolean ok = hsm.verifyMacZmk(input, kek.getKekClear(), rxMac);
                log.info("[SWAM-SRV] Verif MAC DE128 ({} octets) -> {}", rxMac.length, ok ? "OK" : "FAIL");
                return ok ? "OK" : "FAIL";
            } catch (Exception e) {
                log.error("[SWAM-SRV] verifyIncomingMac erreur : {}", e.getMessage(), e);
                return "FAIL";
            }
        }

        /** Calcule et pose le DE128 (macLength premiers octets du MAC 3DES). */
        private void poseMacOnResponse(ISOMsg r) {
            try {
                SwamKek kek = kekRepository.findByMemberGroupId(memberGroupId()).orElse(null);
                if (kek == null || kek.getKekClear() == null) {
                    log.warn("[SWAM-SRV] ZMK claire absente -> DE128 non pose");
                    return;
                }
                byte[] input = SwamMacBuilder.build(r);
                byte[] full  = hsm.generateMacZmk(input, kek.getKekClear());
                byte[] mac   = (macLength > 0 && macLength < full.length)
                        ? Arrays.copyOfRange(full, 0, macLength)
                        : full;
                r.set(128, mac);
                log.info("[SWAM-SRV] DE128 pose ({} octets) = {}", mac.length, ISOUtil.hexString(mac));
            } catch (Exception e) {
                log.error("[SWAM-SRV] poseMacOnResponse erreur : {}", e.getMessage(), e);
            }
        }

        private boolean verifyPin(ISOMsg m, String pan, String cardPin) {
            try {
                String de53 = m.hasField(53) ? m.getString(53) : null;
                if (de53 == null || de53.length() < 2 || !"02".equals(de53.substring(0, 2))) {
                    log.warn("[SWAM-SRV] DE53 absent/methode non ZPK -> PIN non verifie (tolerant)");
                    return true;
                }
                SwamIssKey pek = issKeyRepository
                        .findByMemberGroupIdAndKeyTypeAndStatus(
                                memberGroupId(), "PEK", "ACTIVE").orElse(null);
                if (pek == null) {
                    log.warn("[SWAM-SRV] ZPK issuer absente -> PIN non verifie (tolerant)");
                    return true;
                }
                byte[] pinBlock = m.getBytes(52);
                String decrypted = hsm.decryptPinBlock(pinBlock, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
                boolean ok = decrypted.equals(cardPin);
                log.info("[SWAM-SRV] Verif PIN -> {}", ok ? "OK" : "FAIL");
                return ok;
            } catch (Exception e) {
                log.error("[SWAM-SRV] verifyPin erreur : {}", e.getMessage());
                return false;
            }
        }

        private String maskPan(String pan) {
            if (pan == null || pan.length() < 10) return pan;
            return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
        }
    }
}
