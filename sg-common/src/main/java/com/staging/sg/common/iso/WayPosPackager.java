package com.staging.sg.common.iso;

import org.jpos.iso.BcdPrefixer;
import org.jpos.iso.IFB_BITMAP;
import org.jpos.iso.IFB_LLLBINARY;
import org.jpos.iso.IFB_LLLCHAR;
import org.jpos.iso.IFB_LLCHAR;
import org.jpos.iso.IFB_LLNUM;
import org.jpos.iso.IFB_NUMERIC;
import org.jpos.iso.IF_CHAR;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOBinaryFieldPackager;
import org.jpos.iso.ISOFieldPackager;
import org.jpos.iso.LiteralBinaryInterpreter;

/**
 * OpenWay POS Basic/Extended packager.
 *
 * Numeric data and variable-length prefixes are packed BCD. Character
 * fields are literal bytes and the bitmap is binary. Only the primary
 * bitmap (DE2..DE64) is supported by the supplied specifications.
 */
public final class WayPosPackager extends ISOBasePackager {

    public WayPosPackager() {
        setFieldPackager(buildFields());
    }

    private static ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] f = new ISOFieldPackager[65];
        f[0] = n(4, "MESSAGE TYPE INDICATOR");
        f[1] = new IFB_BITMAP(8, "PRIMARY BITMAP");
        f[2] = new IFB_LLNUM(19, "PAN", false);
        f[3] = n(6, "PROCESSING CODE");
        f[4] = n(12, "AMOUNT TRANSACTION");
        f[5] = n(12, "AMOUNT SETTLEMENT");
        f[6] = n(12, "AMOUNT CARDHOLDER BILLING");
        f[7] = n(10, "TRANSMISSION DATE TIME");
        f[8] = n(8, "AMOUNT CARDHOLDER BILLING FEE");
        f[9] = n(8, "CONVERSION RATE SETTLEMENT");
        f[10] = n(8, "CONVERSION RATE CARDHOLDER BILLING");
        f[11] = n(6, "STAN");
        f[12] = n(6, "LOCAL TRANSACTION TIME");
        f[13] = n(4, "LOCAL TRANSACTION DATE");
        f[14] = n(4, "EXPIRATION DATE");
        f[15] = n(4, "SETTLEMENT DATE");
        f[16] = n(4, "CONVERSION DATE");
        f[17] = n(4, "CAPTURE DATE");
        f[18] = n(4, "MERCHANT TYPE");
        f[19] = n(3, "ACQUIRING COUNTRY CODE");
        f[20] = n(3, "PAN EXTENDED COUNTRY CODE");
        f[21] = n(3, "FORWARDING COUNTRY CODE");
        f[22] = n(3, "POS ENTRY MODE");
        f[23] = n(3, "CARD SEQUENCE NUMBER");
        f[24] = n(3, "NETWORK INTERNATIONAL ID");
        f[25] = n(2, "POS CONDITION CODE");
        f[26] = n(2, "POS PIN CAPTURE CODE");
        f[27] = n(1, "AUTH ID RESPONSE LENGTH");
        f[28] = n(9, "AMOUNT TRANSACTION FEE");
        f[29] = n(9, "AMOUNT SETTLEMENT FEE");
        f[30] = n(24, "AMOUNTS ORIGINAL");
        f[31] = new IFB_LLLCHAR(999, "SECURITY ADDITIONAL DATA");
        f[32] = new IFB_LLNUM(11, "ACQUIRING INSTITUTION ID", true);
        f[33] = new IFB_LLNUM(11, "FORWARDING INSTITUTION ID", true);
        f[34] = new IFB_LLCHAR(28, "PAN EXTENDED");
        f[35] = new IFB_LLCHAR(37, "TRACK 2 DATA");
        f[36] = new IFB_LLLCHAR(104, "TRACK 3 DATA");
        f[37] = new IF_CHAR(12, "RETRIEVAL REFERENCE NUMBER");
        f[38] = new IF_CHAR(6, "AUTHORIZATION ID RESPONSE");
        f[39] = new IF_CHAR(2, "RESPONSE CODE");
        f[40] = n(3, "SERVICE CODE");
        f[41] = new IF_CHAR(8, "TERMINAL ID");
        f[42] = new IF_CHAR(15, "CARD ACCEPTOR ID");
        f[43] = new IF_CHAR(40, "CARD ACCEPTOR NAME LOCATION");
        f[44] = new IFB_LLCHAR(99, "ADDITIONAL RESPONSE DATA");
        f[45] = new IFB_LLCHAR(75, "TRACK 1 DATA");
        f[46] = new IFB_LLLCHAR(206, "AMOUNTS FEES");
        f[47] = new IFB_LLLBINARY(999, "ADDITIONAL DATA");
        f[48] = new IFB_LLLBINARY(999, "ADDITIONAL DATA PRIVATE");
        f[49] = n(3, "CURRENCY TRANSACTION");
        f[50] = n(3, "CURRENCY SETTLEMENT");
        f[51] = n(3, "CURRENCY CARDHOLDER BILLING");
        f[52] = new ISOBinaryFieldPackager(
                8, "PIN BLOCK", LiteralBinaryInterpreter.INSTANCE, org.jpos.iso.NullPrefixer.INSTANCE);
        f[54] = new IFB_LLLCHAR(120, "ADDITIONAL AMOUNTS");
        f[55] = new IFB_LLLBINARY(255, "ICC DATA");
        f[56] = new IFB_LLNUM(35, "ORIGINAL DATA ELEMENTS", true);
        f[59] = new IFB_LLLBINARY(999, "ADDITIONAL DATA PRIVATE");
        f[60] = new IFB_LLLCHAR(999, "OPERATION SPECIFIC DATA");
        f[61] = new ISOBinaryFieldPackager(
                15000, "LONG ADDITIONAL DATA",
                LiteralBinaryInterpreter.INSTANCE, BcdPrefixer.LLLLL);
        f[62] = new IFB_LLLCHAR(999, "SECURE REFERENCE");
        f[63] = new IFB_LLLCHAR(999, "ADDITIONAL DATA PRIVATE");
        f[64] = new ISOBinaryFieldPackager(
                4, "MAC", LiteralBinaryInterpreter.INSTANCE, org.jpos.iso.NullPrefixer.INSTANCE);
        return f;
    }

    private static IFB_NUMERIC n(int length, String description) {
        return new IFB_NUMERIC(length, description, true);
    }
}
