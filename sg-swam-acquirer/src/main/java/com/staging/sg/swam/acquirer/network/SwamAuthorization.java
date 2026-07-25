package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.service.SwamInterfaceService;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/** Construit les messages SWAM (autorisation 1100, gestion reseau 1804). */
@Component
public class SwamAuthorization {

    private final SwamInterfaceService interfaceService;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    public SwamAuthorization(SwamInterfaceService interfaceService) {
        this.interfaceService = interfaceService;
    }

    private String nextStan() {
        int v = stanSeq.getAndIncrement() % 1000000;
        return String.format("%06d", v);
    }
    /** DE7 : format reel Way4/HPS = aaMMjjHHmm (10 car). */
    private String now10() { return new SimpleDateFormat("yyMMddHHmm").format(new Date()); }
    private String nowLocal12() { return new SimpleDateFormat("yyMMddHHmmss").format(new Date()); }

    /**
     * 1804 gestion reseau : func = 801 (sign-on) / 803 (echo) / 802 (sign-off).
     *
     * Structure alignee sur le VRAI membre Way4 (logs interop du 14/07/2026) :
     *   DE7, DE11, DE12, DE24, DE25, DE33, DE37  (+ DE128 pose par SwamMac)
     * Tous ces champs sont MANDATORY cote HPS et entrent dans le calcul du MAC.
     */
    public ISOMsg buildNetwork(String func, org.jpos.iso.ISOPackager pkg) throws Exception {
        String stan = nextStan();
        ISOMsg m = new ISOMsg();
        m.setPackager(pkg);
        m.setMTI("1804");
        m.set(7,  now10());              // aaMMjjHHmm
        m.set(11, stan);
        m.set(12, nowLocal12());         // aaMMjjHHmmss
        m.set(24, func);
        m.set(25, "1000");               // message reason code (comme Way4)
        m.set(33, requiredDe33());
        m.set(37, stan + "000000");      // retrieval reference number (12)
        return m;
    }

    /** 1100 autorisation simple. */
    public ISOMsg buildAuth1100(String pan, String amount, String stan, org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(pkg);
        m.setMTI("1100");
        m.set(2,  pan);
        m.set(3,  "000000");           // achat
        m.set(4,  amount);             // n12
        m.set(7,  now10());
        m.set(11, stan);
        m.set(12, nowLocal12());
        m.set(22, "051         ");     // POS data code an12
        m.set(24, "100");             // function code
        m.set(32, requiredDe32());
        m.set(37, String.format("%012d", Long.parseLong(stan)));
        m.set(41, "TERM0001");
        m.set(42, "MERCHANT000001 ");
        m.set(49, "504");             // MAD
        return m;
    }

    public String nextStan_() { return nextStan(); }

    private String requiredDe32() {
        String value = interfaceService.get().getAcquirerCodeDe32();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] acquirer_code_de32 obligatoire");
        }
        return value;
    }

    private String requiredDe33() {
        String value = interfaceService.get().getIssuerCodeDe33();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] issuer_code_de33 obligatoire");
        }
        return value;
    }
}
