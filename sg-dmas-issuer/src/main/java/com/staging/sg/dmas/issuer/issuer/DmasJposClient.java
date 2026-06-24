package com.staging.sg.dmas.issuer.issuer;

import com.staging.sg.common.iso.McPackagerEbcdic;
import org.jpos.iso.ISOMsg;
import com.staging.sg.common.iso.DmasLengthChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client jPOS cote ISSUER (= customer/membre).
 * Ouvre un NACChannel vers l'acquereur (reseau) et ENVOIE le 0800 sign-on conforme.
 * Etape A : sign-on uniquement, en parallele de l'existant.
 */
@Component
public class DmasJposClient {

    private static final Logger log = LoggerFactory.getLogger(DmasJposClient.class);

    @Value("${dmas.jpos.acquirer-host:localhost}")
    private String acquirerHost;

    @Value("${dmas.jpos.acquirer-port:8600}")
    private int acquirerPort;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    @Value("${dmas.jpos.group-signon-id:40260}")
    private String groupSignonId;

    @Value("${dmas.jpos.forwarding-id:011901}")
    private String forwardingId;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    /** Construit et envoie un 0800 sign-on conforme, retourne le resultat. */
    public Map<String,Object> signOn() throws Exception {
        return sendNetwork("061", "SIGN-ON");   // DE070=061 group sign-on
    }

    public Map<String,Object> echoTest() throws Exception {
        return sendNetwork("270", "ECHO-TEST"); // DE070=270 echo test
    }

    private Map<String,Object> sendNetwork(String de070, String label) throws Exception {
        McPackagerEbcdic packager = new McPackagerEbcdic();
        DmasLengthChannel channel = new DmasLengthChannel();
        channel.setPackager(packager);
        channel.setHost(acquirerHost, acquirerPort);

        String stan = String.format("%06d", stanSeq.getAndIncrement());
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("0800");
        m.set(2,  groupSignonId);    // DE002 = Group Sign-on ID numerique (pas un PAN)
        m.set(7,  dt);               // DE007
        m.set(11, stan);             // DE011
        m.set(33, forwardingId);     // DE033 forwarding institution (6 chiffres)
        m.set(70, de070);         // DE070
        m.set(94, "0I0    ");     // DE094 service indicator (7 car, padde comme la trace)
        m.set(96, "000000");      // DE096 message security code (n-6 conforme spec)

        log.info("[JPOS-CLI] {} -> connexion {}:{}", label, acquirerHost, acquirerPort);
        channel.connect();
        log.info("[JPOS-CLI] {} -> envoi 0800 DE70={} STAN={} memberGroup={}",
                label, de070, stan, memberGroup);
        channel.send(m);

        ISOMsg resp = channel.receive();
        channel.disconnect();

        String rc = resp.hasField(39) ? resp.getString(39) : "??";
        boolean ok = "00".equals(rc);
        log.info("[JPOS-CLI] {} <- 0810 DE39={} ok={}", label, rc, ok);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label", label);
        r.put("mti_sent", "0800");
        r.put("de070", de070);
        r.put("stan", stan);
        r.put("mti_received", resp.getMTI());
        r.put("de039", rc);
        r.put("success", ok);
        return r;
    }
}
