package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.service.SwamInterfaceService;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.staging.sg.common.entity.SwamInterface;

/** Construit les messages SWAM SID transactionnels et de gestion reseau. */
@Component
public class SwamAuthorization {

    private final Supplier<SwamInterface> interfaceConfig;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    @Value("${swam.sid.country-code:504}") private String countryCode;
    @Value("${swam.sid.card-expiry:2712}") private String defaultExpiry;
    @Value("${swam.sid.mcc:5411}") private String defaultMcc;
    @Value("${swam.sid.terminal-id:TERM0001}") private String terminalId;
    @Value("${swam.sid.merchant-id:MERCHANT000001}") private String merchantId;
    @Value("${swam.sid.merchant-name-location:MONEYCORE CASABLANCA MA}") private String merchantNameLocation;

    @Autowired
    public SwamAuthorization(SwamInterfaceService interfaceService) {
        this(interfaceService::get);
    }

    SwamAuthorization(Supplier<SwamInterface> interfaceConfig) {
        this.interfaceConfig = interfaceConfig;
    }

    private String nextStan() {
        int v = stanSeq.getAndIncrement() % 1000000;
        return String.format("%06d", v);
    }
    /** DE7 : format reel Way4/HPS = aaMMjjHHmm (10 car). */
    private String now10() { return new SimpleDateFormat("yyMMddHHmm").format(new Date()); }
    private String nowLocal12() { return new SimpleDateFormat("yyMMddHHmmss").format(new Date()); }
    private String today6() { return new SimpleDateFormat("yyMMdd").format(new Date()); }
    private String monthDay4() { return new SimpleDateFormat("MMdd").format(new Date()); }

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

    /** 1100 demande d'autorisation conforme au tableau SID V3.20 section 3.1. */
    public ISOMsg buildAuth1100(String pan, String amount, String stan, org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(pkg);
        m.setMTI("1100");
        m.set(2,  pan);
        m.set(3,  "000000");           // achat
        m.set(4,  amount);             // n12
        m.set(6,  amount);             // facturation porteur, meme devise
        m.set(7,  now10());
        m.set(10, "61000000");         // 1.000000, exposant 6
        m.set(11, stan);
        m.set(12, nowLocal12());
        m.set(14, defaultExpiry);
        m.set(15, today6());
        m.set(16, monthDay4());
        m.set(18, defaultMcc);
        m.set(19, countryCode);
        m.set(21, countryCode);
        m.set(22, "P10101511004");     // POS data code an12
        m.set(24, "100");             // function code
        m.set(32, requiredDe32());
        m.set(33, requiredDe33());
        m.set(37, String.format("%012d", Long.parseLong(stan)));
        m.set(41, fixed(terminalId, 8));
        m.set(42, fixed(merchantId, 15));
        m.set(43, merchantNameLocation);
        m.set(49, "504");             // MAD
        m.set(51, "504");
        m.set(53, "0099000000");      // pas de PIN; indices de cles reserves
        m.set(61, "061012" + m.getString(22));
        m.set(124, requiredSenderIdentification());
        return m;
    }

    /** 1200 transaction financiere single-message, section SID 3.3. */
    public ISOMsg buildFinancial1200(String pan, String amount, String stan,
                                     org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = buildAuth1100(pan, amount, stan, pkg);
        m.setMTI("1200");
        m.set(5, amount);
        m.set(9, "61000000");
        m.set(24, "200");
        m.set(50, "504");
        return m;
    }

    /** 1220 avis financier relatif a une autorisation precedente. */
    public ISOMsg buildFinancialAdvice1220(
            String pan, String amount, String stan, String authorizationCode,
            String originalDataElements, org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = buildFinancial1200(pan, amount, stan, pkg);
        m.setMTI("1220");
        m.set(24, "201");
        m.set(25, "2000");
        m.set(38, authorizationCode);
        m.set(39, "000");
        m.set(56, originalDataElements);
        return m;
    }

    /** 1420 redressement total ou partiel d'une transaction precedente. */
    public ISOMsg buildReversal1420(
            String pan, String amount, String stan, String rrn,
            String authorizationCode, String originalDataElements,
            boolean partial, String originalAmounts,
            org.jpos.iso.ISOPackager pkg) throws Exception {
        ISOMsg m = buildAuth1100(pan, amount, stan, pkg);
        m.setMTI("1420");
        m.set(5, amount);
        m.set(9, "61000000");
        m.set(24, partial ? "402" : "400");
        m.set(25, "4000");
        m.set(37, rrn);
        if (authorizationCode != null && !authorizationCode.isBlank()) m.set(38, authorizationCode);
        m.set(39, "000");
        m.set(50, "504");
        m.set(56, originalDataElements);
        if (partial) {
            if (originalAmounts == null || !originalAmounts.matches("\\d{24}")) {
                throw new IllegalArgumentException(
                        "DE30 originalAmounts n24 est obligatoire pour un redressement partiel");
            }
            m.set(30, originalAmounts);
        }
        return m;
    }

    public String nextStan_() { return nextStan(); }

    private String requiredDe32() {
        String value = interfaceConfig.get().getAcquirerCodeDe32();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] acquirer_code_de32 obligatoire");
        }
        return value;
    }

    private String requiredDe33() {
        String value = interfaceConfig.get().getIssuerCodeDe33();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] issuer_code_de33 obligatoire");
        }
        return value;
    }

    private String requiredSenderIdentification() {
        String value = interfaceConfig.get().getMemberGroupId();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] member_group_id obligatoire pour DE124");
        }
        return value;
    }

    private static String fixed(String value, int length) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > length) return normalized.substring(0, length);
        return String.format("%-" + length + "s", normalized);
    }
}
