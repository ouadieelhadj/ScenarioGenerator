package com.staging.sg.swam.acquirer.network;

import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/** Construit les messages SWAM (autorisation 1100, gestion reseau 1804). */
@Component
public class SwamAuthorization {

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    private String nextStan() {
        int v = stanSeq.getAndIncrement() % 1000000;
        return String.format("%06d", v);
    }
    private String now10() { return new SimpleDateFormat("MMddHHmmss").format(new Date()); }
    private String nowLocal12() { return new SimpleDateFormat("yyMMddHHmmss").format(new Date()); }

    /** 1804 gestion reseau : func = 801 (sign-on) / 803 (echo) / 802 (sign-off). */
    public ISOMsg buildNetwork(String func, org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(pkg);
        m.setMTI("1804");
        m.set(7, now10());
        m.set(11, nextStan());
        m.set(24, func);
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
        m.set(32, "12345");           // acquiring institution id
        m.set(37, String.format("%012d", Long.parseLong(stan)));
        m.set(41, "TERM0001");
        m.set(42, "MERCHANT000001 ");
        m.set(49, "504");             // MAD
        return m;
    }

    public String nextStan_() { return nextStan(); }
}
