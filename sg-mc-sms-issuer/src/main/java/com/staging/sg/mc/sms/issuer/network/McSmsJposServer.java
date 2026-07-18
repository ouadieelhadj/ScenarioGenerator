package com.staging.sg.mc.sms.issuer.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.MastercardSmsPackager;
import com.staging.sg.common.iso.McSmsLengthChannel;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serveur jPOS cote MASTERCARD SIMULE (sg-mc-sms-issuer).
 * Liaison PERMANENTE : ISOServer garde les sessions ouvertes.
 *
 * Modele : SwamJposServer.
 *
 * Messages geres :
 *   0800 DE70=061 (sign-on)   -> 0810 DE39=00
 *   0800 DE70=062 (sign-off)  -> 0810 DE39=00
 *   0800 DE70=270 (echo test) -> 0810 DE39=00
 *   0800 DE70=161 (PEK exch)  -> 0810 DE39=00
 *   0810                      -> accuse d'un push, rien a faire
 *   0200                      -> autorisation (a implementer)
 *
 * Layout 0810 (Table 77) : DE7 (M), DE11 (ME), DE33 (ME), DE39 (M),
 *                          DE44 (C si DE39=30), DE63 (ME), DE70 (ME).
 * Les champs ME (Match Echo) doivent reprendre EXACTEMENT la valeur de la requete.
 *
 * Codes DE39 Mastercard (an-2) : 00 = approuve, 30 = format error, 96 = system error.
 */
@Component
public class McSmsJposServer {

    private static final Logger log = LoggerFactory.getLogger(McSmsJposServer.class);

    private static final String NETWORK_CODE     = "MASTERCARD_SMS";
    private static final int    DEFAULT_ISO_PORT = 8098;

    /** Codes DE70. */
    private static final String DE70_SIGNON       = "061";
    private static final String DE70_SIGNOFF      = "062";
    private static final String DE70_PEK_EXCHANGE = "161";
    private static final String DE70_ECHO         = "270";
    private static final String DE70_CUTOVER      = "301";

    /** Codes DE39 (an-2). */
    private static final String RC_OK           = "00";
    private static final String RC_FORMAT_ERROR = "30";
    private static final String RC_SYSTEM_ERROR = "96";

    private final NetworkRepository networkRepository;

    private ISOServer isoServer;
    private Thread serverThread;

    /** Compteur STAN pour les messages spontanes emis par le MIP simule. */
    private final AtomicInteger stanCounter = new AtomicInteger(900000);

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
            MastercardSmsPackager packager = new MastercardSmsPackager();
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

                log.warn("[MC-SRV] MTI non gere : {}", mti);
                return false;
            } catch (Exception e) {
                log.error("[MC-SRV] Erreur : {}", e.getMessage(), e);
                return false;
            }
        }

        /** 0800 -> 0810. */
        private boolean handleNetwork(ISOSource source, ISOMsg m) throws Exception {
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            String label = switch (de70) {
                case DE70_SIGNON       -> "SIGN-ON";
                case DE70_SIGNOFF      -> "SIGN-OFF";
                case DE70_ECHO         -> "ECHO TEST";
                case DE70_PEK_EXCHANGE -> "PEK EXCHANGE";
                case DE70_CUTOVER      -> "CUTOVER";
                default                -> "FONCTION " + de70;
            };
            log.info("[MC-SRV] Gestion reseau {} (DE70={})", label, de70);

            // Controle minimal du layout Table 74 : DE7, DE11, DE33, DE70 obligatoires
            String rc = RC_OK;
            if (!m.hasField(7) || !m.hasField(11) || !m.hasField(33) || !m.hasField(70)) {
                log.warn("[MC-SRV] Champs obligatoires manquants -> DE39={}", RC_FORMAT_ERROR);
                rc = RC_FORMAT_ERROR;
            }

            ISOMsg r = buildResponse(m, rc);
            source.send(r);
            log.info("[MC-SRV] Repondu 0810 DE39={} ({})", rc, label);
            return true;
        }

        /**
         * Construit le 0810. Les champs ME reprennent la valeur exacte de la requete.
         * Table 77 : DE7 (M), DE11 (ME), DE33 (ME), DE39 (M), DE63 (ME), DE70 (ME).
         */
        private ISOMsg buildResponse(ISOMsg req, String de39) throws Exception {
            ISOMsg r = new ISOMsg();
            r.setPackager(req.getPackager());
            r.setMTI("0810");
            r.set(7, req.hasField(7) ? req.getString(7) : utcDateTime());
            if (req.hasField(11)) r.set(11, req.getString(11));
            if (req.hasField(33)) r.set(33, req.getString(33));
            r.set(39, de39);
            if (req.hasField(63)) r.set(63, req.getString(63));
            if (req.hasField(70)) r.set(70, req.getString(70));
            return r;
        }

        /** 0810 recu : c'est l'accuse d'un message qu'on a pousse. */
        private boolean handleNetworkResponse(ISOMsg m) {
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            String de39 = m.hasField(39) ? m.getString(39) : "?";
            String stan = m.hasField(11) ? m.getString(11) : "?";
            log.info("[MC-SRV] Recu 0810 DE70={} DE39={} STAN={} (accuse)", de70, de39, stan);
            return true;
        }

        /** 0200 -> 0210. A implementer (autorisation). */
        private boolean handleAuthorization(ISOSource source, ISOMsg m) throws Exception {
            log.warn("[MC-SRV] 0200 recu — autorisation pas encore implementee");
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
            r.set(39, RC_SYSTEM_ERROR);
            source.send(r);
            return true;
        }

        private String utcDateTime() {
            return ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        }
    }
}
