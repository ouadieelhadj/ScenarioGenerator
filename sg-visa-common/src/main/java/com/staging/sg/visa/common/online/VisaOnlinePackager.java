package com.staging.sg.visa.common.online;

import org.jpos.iso.*;

/**
 * VisaNet Authorization-Only sandbox packager.
 * Numeric fields use packed BCD, character fields use EBCDIC and bitmaps are
 * binary. It deliberately models only the fields covered by the first E2E.
 */
public final class VisaOnlinePackager extends ISOBasePackager {
    public VisaOnlinePackager() {
        setFieldPackager(fields());
    }

    private static ISOFieldPackager[] fields() {
        ISOFieldPackager[] f = new ISOFieldPackager[129];
        f[0] = new IFB_NUMERIC(4, "MTI", true);
        f[1] = new IFB_BITMAP(16, "BITMAP");
        f[2] = new IFB_LLNUM(19, "PAN", true);
        f[3] = new IFB_NUMERIC(6, "PROCESSING CODE", true);
        f[4] = new IFB_NUMERIC(12, "AMOUNT", true);
        f[7] = new IFB_NUMERIC(10, "TRANSMISSION DATE TIME", true);
        f[11] = new IFB_NUMERIC(6, "STAN", true);
        f[12] = new IFB_NUMERIC(6, "LOCAL TIME", true);
        f[13] = new IFB_NUMERIC(4, "LOCAL DATE", true);
        f[14] = new IFB_NUMERIC(4, "EXPIRY", true);
        f[18] = new IFB_NUMERIC(4, "MCC", true);
        f[19] = new IFB_NUMERIC(3, "ACQUIRER COUNTRY", true);
        f[22] = new IFB_NUMERIC(3, "POS ENTRY MODE", true);
        f[25] = new IFB_NUMERIC(2, "POS CONDITION", true);
        f[32] = new IFB_LLNUM(11, "ACQUIRER ID", true);
        f[37] = new IFE_CHAR(12, "RRN");
        f[38] = new IFE_CHAR(6, "AUTHORIZATION CODE");
        f[39] = new IFE_CHAR(2, "RESPONSE CODE");
        f[41] = new IFE_CHAR(8, "TERMINAL ID");
        f[42] = new IFE_CHAR(15, "MERCHANT ID");
        f[43] = new IFE_CHAR(40, "MERCHANT NAME LOCATION");
        f[49] = new IFB_NUMERIC(3, "CURRENCY", true);
        f[60] = new IFE_LLLCHAR(999, "ADDITIONAL POS DATA");
        f[62] = new IFE_LLLCHAR(999, "VISA PRIVATE DATA");
        f[70] = new IFB_NUMERIC(3, "NETWORK MANAGEMENT CODE", true);
        f[90] = new IFB_NUMERIC(42, "ORIGINAL DATA ELEMENTS", true);
        f[126] = new IFE_LLLCHAR(999, "VISA PRIVATE DATA 126");
        return f;
    }
}
