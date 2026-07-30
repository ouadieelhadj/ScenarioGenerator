package com.staging.sg.mc.sms.issuer.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.MastercardSmsPackagerEbcdic;
import com.staging.sg.common.iso.McSmsLengthChannel;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Serveur jPOS cote MASTERCARD SIMULE (sg-mc-sms-issuer).
 * Liaison PERMANENTE : ISOServer garde les sessions ouvertes.
 *
 * Messages geres :
 *   0800 DE70=061 sign-on    -> 0810 DE39=00
 *   0800 DE70=062 sign-off   -> 0810 DE39=00
 *   0800 DE70=270 echo test  -> 0810 DE39=00
 *   0800 DE70=162 sollicitation de cle -> 0810 DE39=00, puis livraison
 *                                          asynchrone (voir McSmsIssKeyExchange)
 *   0810 DE70=161 accuse du membre apres livraison
 *   0200 autorisation (a implementer)
 *
 * Layout 0810 (Table 77) : les champs ME (DE7, DE11, DE33, DE63, DE70)
 * reprennent EXACTEMENT la valeur de la requete.
 *
 * Codes DE39 (an-2) : 00 approuve, 30 erreur de format, 96 erreur systeme.
 */
@Component
public class McSmsJposServer {

    private static final Logger log = LoggerFactory.getLogger(McSmsJposServer.class);

    private static final String NETWORK_CODE     = "MASTERCARD_SMS";
    private static final int    DEFAULT_ISO_PORT = 8098;

    // Codes DE70
    private static final String DE70_SIGNON           = "061";
    private static final String DE70_SIGNOFF          = "062";
    private static final String DE70_KEY_EXCHANGE     = "161";
    private static final String DE70_KEY_SOLICITATION = "162";
    private static final String DE70_KEY_SOLIC_TR31   = "163";
    private static final String DE70_KEY_SUCCESS      = "164";
    private static final String DE70_KEY_FAILURE      = "165";
    private static final String DE70_ECHO             = "270";

    // Codes DE39
    private static final String RC_OK           = "00";
    private static final String RC_FORMAT_ERROR = "30";
    private static final String RC_SYSTEM_ERROR = "96";

    private final NetworkRepository networkRepository;

    @Autowired private McSmsIssKeyExchange keyExchange;
    @Autowired private McSmsAuthorizationProcessor authorizationProcessor;

    private ISOServer isoServer;
    private Thread serverThread;

    public McSmsJposServer(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    private int resolvePort() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerIsoPort() != null) {
                int p = n.get().getIssuerIsoPort();
                log.info("[MC-SRV] Port ISO lu depuis networks : {}", p);
                return p;
            }
            log.warn("[MC-SRV] Port ISO absent en base, fallback {}", DEFAULT_ISO_PORT);
        } catch (Exception e) {
            log.warn("[MC-SRV] Lecture port base KO ({}), fallback {}", e.getMessage(), DEFAULT_ISO_PORT);
        }
        return DEFAULT_ISO_PORT;
    }

    @PostConstruct
    public void start() {
        int port = resolvePort();
        try {
            MastercardSmsPackagerEbcdic packager = new MastercardSmsPackagerEbcdic();
            McSmsLengthChannel channel = new McSmsLengthChannel();
            channel.setPackager(packager);
            isoServer = new ISOServer(port, channel, null);
            isoServer.addISORequestListener(new McSmsListener());
            serverThread = new Thread(isoServer, "mc-sms-jpos-server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("[MC-SRV] ISOServer demarre sur :{} (McSmsLengthChannel, framing 2o big-endian)", port);
        } catch (Exception e) {
            log.error("[MC-SRV] Echec demarrage : {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (isoServer != null) isoServer.shutdown();
        if (serverThread != null) serverThread.interrupt();
        log.info("[MC-SRV] ISOServer arrete");
    }

    // ========================================================================
    //  LISTENER
    // ========================================================================

    private class McSmsListener implements ISORequestListener {

        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti = m.getMTI();
                log.info("[MC-SRV] Recu MTI={} STAN={}", mti,
                        m.hasField(11) ? m.getString(11) : "?");

                if ("0800".equals(mti)) return handleNetwork(source, m);
                if ("0810".equals(mti)) return handleNetworkResponse(m);
                if ("0200".equals(mti)) return handleAuthorization(source, m);
                if ("0400".equals(mti) || "0420".equals(mti)) return handleReversal(source, m);

                log.warn("[MC-SRV] MTI non gere : {}", mti);
                return false;
            } catch (Exception e) {
                log.error("[MC-SRV] Erreur : {}", e.getMessage(), e);
                return false;
            }
        }

        /** 0800 -> 0810, plus livraison de cle si sollicitation. */
        private boolean handleNetwork(ISOSource source, ISOMsg m) throws Exception {
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            String label = switch (de70) {
                case DE70_SIGNON           -> "SIGN-ON";
                case DE70_SIGNOFF          -> "SIGN-OFF";
                case DE70_ECHO             -> "ECHO TEST";
                case DE70_KEY_EXCHANGE     -> "KEY EXCHANGE";
                case DE70_KEY_SOLICITATION -> "SOLLICITATION DE CLE";
                case DE70_KEY_SOLIC_TR31   -> "SOLLICITATION DE CLE (TR-31)";
                case DE70_KEY_SUCCESS      -> "CONFIRMATION DE SUCCES";
                case DE70_KEY_FAILURE      -> "AVIS D'ECHEC";
                default                    -> "FONCTION " + de70;
            };
            log.info("[MC-SRV] Gestion reseau {} (DE70={})", label, de70);

            // Controle du layout Table 74 : DE7, DE11, DE33, DE70 obligatoires
            String rc = RC_OK;
            if (!m.hasField(7) || !m.hasField(11) || !m.hasField(33) || !m.hasField(70)) {
                log.warn("[MC-SRV] Champs obligatoires manquants -> DE39={}", RC_FORMAT_ERROR);
                rc = RC_FORMAT_ERROR;
            }

            // TR-31 non implemente : on refuse explicitement
            if (DE70_KEY_SOLIC_TR31.equals(de70)) {
                log.warn("[MC-SRV] TR-31 (DE70=163) non implemente -> DE39={}", RC_SYSTEM_ERROR);
                rc = RC_SYSTEM_ERROR;
            }

            ISOMsg r = buildResponse(m, rc);
            source.send(r);
            log.info("[MC-SRV] Repondu 0810 DE39={} ({})", rc, label);

            // Sollicitation acceptee : livrer la cle de facon asynchrone
            if (RC_OK.equals(rc) && DE70_KEY_SOLICITATION.equals(de70)) {
                log.info("[MC-SRV] Declenchement de la livraison de cle");
                keyExchange.deliverKeyAsync(source, m);
            }

            // Confirmation / echec envoyes par le membre : simple trace
            if (DE70_KEY_SUCCESS.equals(de70)) {
                log.info("[MC-SRV] Le membre confirme l'import de la cle");
            } else if (DE70_KEY_FAILURE.equals(de70)) {
                log.warn("[MC-SRV] Le membre signale un echec d'import");
            }

            return true;
        }

        /**
         * Construit le 0810. Champs ME repris a l'identique.
         * Table 77 : DE7 (M), DE11 (ME), DE33 (ME), DE39 (M), DE63 (ME), DE70 (ME).
         */
        private ISOMsg buildResponse(ISOMsg req, String de39) throws Exception {
            ISOMsg r = new ISOMsg();
            r.setPackager(req.getPackager());
            r.setMTI("0810");
            if (req.hasField(2))  r.set(2,  req.getString(2));
            r.set(7, req.hasField(7) ? req.getString(7) : utcDateTime());
            if (req.hasField(11)) r.set(11, req.getString(11));
            if (req.hasField(33)) r.set(33, req.getString(33));
            r.set(39, de39);
            if (req.hasField(63)) r.set(63, req.getString(63));
            if (req.hasField(70)) r.set(70, req.getString(70));
            return r;
        }

        /** 0810 recu : accuse d'un message que le simulateur a pousse. */
        private boolean handleNetworkResponse(ISOMsg m) {
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            String de39 = m.hasField(39) ? m.getString(39) : "?";
            String stan = m.hasField(11) ? m.getString(11) : "?";

            if (DE70_KEY_EXCHANGE.equals(de70)) {
                if (RC_OK.equals(de39)) {
                    log.info("[MC-SRV] Le membre a accepte la cle (0810 DE39=00 STAN={})", stan);
                } else {
                    log.warn("[MC-SRV] Le membre a rejete la cle (0810 DE39={} STAN={}) — "
                           + "le 0820 est envoye malgre tout, comme le fait le simulateur "
                           + "Mastercard officiel", de39, stan);
                }
            } else {
                log.info("[MC-SRV] Recu 0810 DE70={} DE39={} STAN={} (accuse)", de70, de39, stan);
            }
            return true;
        }

        /** 0200 -> 0210. A implementer. */
        private boolean handleAuthorization(ISOSource source, ISOMsg m) throws Exception {
            McSmsAuthorizationProcessor.Decision decision = authorizationProcessor.process(m);
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("0210");
            if (m.hasField(2))  r.set(2,  m.getString(2));
            if (m.hasField(3))  r.set(3,  m.getString(3));
            if (m.hasField(4))  r.set(4,  m.getString(4));
            r.set(7, utcDateTime());
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(33)) r.set(33, m.getString(33));
            if (m.hasField(37)) r.set(37, m.getString(37));
            if (m.hasField(41)) r.set(41, m.getString(41));
            if (m.hasField(49)) r.set(49, m.getString(49));
            r.set(39, decision.responseCode());
            if (decision.authorizationCode() != null) r.set(38, decision.authorizationCode());
            source.send(r);
            return true;
        }

        private boolean handleReversal(ISOSource source, ISOMsg m) throws Exception {
            McSmsAuthorizationProcessor.Decision decision = authorizationProcessor.reverse(m);
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("0420".equals(m.getMTI()) ? "0430" : "0410");
            if (m.hasField(2)) r.set(2, m.getString(2));
            if (m.hasField(3)) r.set(3, m.getString(3));
            if (m.hasField(4)) r.set(4, m.getString(4));
            if (m.hasField(7)) r.set(7, m.getString(7));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(37)) r.set(37, m.getString(37));
            r.set(39, decision.responseCode());
            source.send(r);
            return true;
        }

        private String utcDateTime() {
            return ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        }
    }
}
