package com.staging.sg.iso;

import org.jpos.iso.*;
import org.springframework.stereotype.Component;

/**
 * Mastercard ISO 8583 Packager.
 * Based exactly on jPOS iso87ascii.xml standard packager.
 */
@Component
public class McPackager extends ISOBasePackager {

    public McPackager() {
        super();
        setFieldPackager(buildFields());
    }

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fields = new ISOFieldPackager[128];

        fields[0]  = new IFA_NUMERIC  (4,    "MESSAGE TYPE INDICATOR");
        fields[1]  = new IFA_BITMAP   (16,   "BIT MAP");
        fields[2]  = new IFA_LLNUM    (19,   "PAN - PRIMARY ACCOUNT NUMBER");
        fields[3]  = new IFA_NUMERIC  (6,    "PROCESSING CODE");
        fields[4]  = new IFA_NUMERIC  (12,   "AMOUNT TRANSACTION");
        fields[5]  = new IFA_NUMERIC  (12,   "AMOUNT SETTLEMENT");
        fields[6]  = new IFA_NUMERIC  (12,   "AMOUNT CARDHOLDER BILLING");
        fields[7]  = new IFA_NUMERIC  (10,   "TRANSMISSION DATE AND TIME");
        fields[8]  = new IFA_NUMERIC  (8,    "AMOUNT CARDHOLDER BILLING FEE");
        fields[9]  = new IFA_NUMERIC  (8,    "CONVERSION RATE SETTLEMENT");
        fields[10] = new IFA_NUMERIC  (8,    "CONVERSION RATE CARDHOLDER BILLING");
        fields[11] = new IFA_NUMERIC  (6,    "SYSTEM TRACE AUDIT NUMBER");
        fields[12] = new IFA_NUMERIC  (6,    "TIME LOCAL TRANSACTION");
        fields[13] = new IFA_NUMERIC  (4,    "DATE LOCAL TRANSACTION");
        fields[14] = new IFA_NUMERIC  (4,    "DATE EXPIRATION");
        fields[15] = new IFA_NUMERIC  (4,    "DATE SETTLEMENT");
        fields[16] = new IFA_NUMERIC  (4,    "DATE CONVERSION");
        fields[17] = new IFA_NUMERIC  (4,    "DATE CAPTURE");
        fields[18] = new IFA_NUMERIC  (4,    "MERCHANT TYPE");
        fields[19] = new IFA_NUMERIC  (3,    "ACQUIRING INSTITUTION COUNTRY CODE");
        fields[20] = new IFA_NUMERIC  (3,    "PAN EXTENDED COUNTRY CODE");
        fields[21] = new IFA_NUMERIC  (3,    "FORWARDING INSTITUTION COUNTRY CODE");
        fields[22] = new IFA_NUMERIC  (3,    "POINT OF SERVICE ENTRY MODE");
        fields[23] = new IFA_NUMERIC  (3,    "CARD SEQUENCE NUMBER");
        fields[24] = new IFA_NUMERIC  (3,    "NETWORK INTERNATIONAL IDENTIFIER");
        fields[25] = new IFA_NUMERIC  (2,    "POINT OF SERVICE CONDITION CODE");
        fields[26] = new IFA_NUMERIC  (2,    "POINT OF SERVICE PIN CAPTURE CODE");
        fields[27] = new IFA_NUMERIC  (1,    "AUTHORIZATION IDENTIFICATION RESPONSE LENGTH");
        fields[28] = new IFA_NUMERIC  (9,    "AMOUNT TRANSACTION FEE");
        fields[29] = new IFA_NUMERIC  (9,    "AMOUNT SETTLEMENT FEE");
        fields[30] = new IFA_NUMERIC  (9,    "AMOUNT TRANSACTION PROCESSING FEE");
        fields[31] = new IFA_NUMERIC  (9,    "AMOUNT SETTLEMENT PROCESSING FEE");
        fields[32] = new IFA_LLNUM    (11,   "ACQUIRING INSTITUTION ID CODE");
        fields[33] = new IFA_LLNUM    (11,   "FORWARDING INSTITUTION ID CODE");
        fields[34] = new IFA_LLNUM    (28,   "PAN EXTENDED");
        fields[35] = new IFA_LLNUM    (37,   "TRACK 2 DATA");
        fields[36] = new IFA_LLLCHAR  (104,  "TRACK 3 DATA");
        fields[37] = new IF_CHAR      (12,   "RETRIEVAL REFERENCE NUMBER");
        fields[38] = new IF_CHAR      (6,    "AUTHORIZATION IDENTIFICATION RESPONSE");
        fields[39] = new IF_CHAR      (2,    "RESPONSE CODE");
        fields[40] = new IF_CHAR      (3,    "SERVICE RESTRICTION CODE");
        fields[41] = new IF_CHAR      (8,    "CARD ACCEPTOR TERMINAL ID");
        fields[42] = new IF_CHAR      (15,   "CARD ACCEPTOR IDENTIFICATION CODE");
        fields[43] = new IF_CHAR      (40,   "CARD ACCEPTOR NAME LOCATION");
        fields[44] = new IFA_LLCHAR   (25,   "ADDITIONAL RESPONSE DATA");
        fields[45] = new IFA_LLCHAR   (76,   "TRACK 1 DATA");
        fields[46] = new IFA_LLLCHAR  (999,  "ADDITIONAL DATA ISO");
        fields[47] = new IFA_LLLCHAR  (999,  "ADDITIONAL DATA NATIONAL");
        fields[48] = new IFA_LLLCHAR  (999,  "ADDITIONAL DATA PRIVATE");
        fields[49] = new IF_CHAR      (3,    "CURRENCY CODE TRANSACTION");
        fields[50] = new IF_CHAR      (3,    "CURRENCY CODE SETTLEMENT");
        fields[51] = new IF_CHAR      (3,    "CURRENCY CODE CARDHOLDER BILLING");
        fields[52] = new IFA_BINARY   (8,    "PIN DATA");
        fields[53] = new IFA_LLLCHAR  (48,   "SECURITY RELATED CONTROL INFO");
        fields[54] = new IFA_LLLCHAR  (120,  "ADDITIONAL AMOUNTS");
        fields[55] = new IFA_LLLBINARY(255,  "ICC DATA EMV");
        fields[56] = new IFA_LLLCHAR  (999,  "RESERVED ISO");
        fields[57] = new IFA_LLLCHAR  (999,  "RESERVED NATIONAL 1");
        fields[58] = new IFA_LLLCHAR  (999,  "RESERVED NATIONAL 2");
        fields[59] = new IFA_LLLCHAR  (999,  "RESERVED NATIONAL 3");
        fields[60] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE 1");
        fields[61] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE 2");
        fields[62] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE 3");
        fields[63] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE 4");
        fields[64] = new IFA_BINARY   (8,    "MESSAGE AUTHENTICATION CODE");
        fields[70] = new IFA_NUMERIC  (3,    "NETWORK MANAGEMENT CODE");
        fields[90] = new IFA_NUMERIC  (42,   "ORIGINAL DATA ELEMENTS");
        fields[95] = new IF_CHAR      (42,   "REPLACEMENT AMOUNTS");
        fields[96] = new IFA_LLLCHAR  (999,  "MESSAGE SECURITY CODE");
        fields[100]= new IFA_LLNUM    (11,   "RECEIVING INSTITUTION ID CODE");
        fields[102]= new IFA_LLCHAR   (28,   "ACCOUNT IDENTIFICATION 1");
        fields[103]= new IFA_LLCHAR   (28,   "ACCOUNT IDENTIFICATION 2");
        fields[127]= new IFA_BITMAP   (8,    "MAC 2");

        return fields;
    }
}
