package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.common.iso.McPackagerEbcdic;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import com.staging.sg.common.iso.DmasLengthChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Serveur jPOS cote ACQUEREUR (= reseau Mastercard).
 * Ecoute sur un port dedie (8600) avec NACChannel + McPackagerEbcdic.
 * Recoit le 0800 sign-on de l'issuer (client) et repond 0810.
 * En PARALLELE de l'existant : ne touche pas au transport socket actuel.
 */
@Component
public class DmasJposServer {

    private static final Logger log = LoggerFactory.getLogger(DmasJposServer.class);

    @Value("${dmas.jpos.server-port:8600}")
    private int serverPort;

    private ISOServer isoServer;
    private Thread    serverThread;

    @PostConstruct
    public void start() {
        try {
            McPackagerEbcdic packager = new McPackagerEbcdic();
            DmasLengthChannel channel = new DmasLengthChannel();
            channel.setPackager(packager);

            isoServer = new ISOServer(serverPort, channel, null);
            isoServer.addISORequestListener(new SignOnListener());

            serverThread = new Thread(isoServer, "dmas-jpos-server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("[JPOS-SRV] ISOServer demarre sur :{} (DmasLengthChannel/EBCDIC)", serverPort);
        } catch (Exception e) {
            log.error("[JPOS-SRV] Echec demarrage : {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (isoServer != null) isoServer.shutdown();
        if (serverThread != null) serverThread.interrupt();
        log.info("[JPOS-SRV] ISOServer arrete");
    }

    /** Repond aux 0800 (sign-on / echo / key exchange) par un 0810. */
    private static class SignOnListener implements ISORequestListener {
        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti  = m.getMTI();
                String de70 = m.hasField(70) ? m.getString(70) : "?";
                log.info("[JPOS-SRV] Recu MTI={} DE70={} STAN={}",
                        mti, de70, m.hasField(11) ? m.getString(11) : "?");

                if (!"0800".equals(mti)) {
                    log.warn("[JPOS-SRV] MTI non gere par ce listener : {}", mti);
                    return false;
                }

                // Construire la reponse 0810 : echo DE2/7/11/33/70 + DE39=00 + DE63
                ISOMsg r = new ISOMsg();
                r.setPackager(m.getPackager());
                r.setMTI("0810");
                if (m.hasField(2))  r.set(2,  m.getString(2));
                r.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
                if (m.hasField(11)) r.set(11, m.getString(11));
                if (m.hasField(33)) r.set(33, m.getString(33));
                r.set(39, "00");
                r.set(63, "MCC000NPQ");
                if (m.hasField(70)) r.set(70, m.getString(70));

                source.send(r);
                log.info("[JPOS-SRV] Repondu 0810 DE39=00 (echo DE70={})", de70);
                return true;
            } catch (Exception e) {
                log.error("[JPOS-SRV] Erreur traitement : {}", e.getMessage(), e);
                return false;
            }
        }
    }
}
