package com.staging.sg.common.iso;

import org.jpos.iso.*;
import org.springframework.stereotype.Component;

/**
 * SWAM (HPS Switch / PowerCARD HSID) ISO 8583:1993 Packager.
 *
 * Dialecte : TOUT-ASCII (IFA_*), longueurs LLVAR/LLLVAR en ASCII,
 * bitmaps ASCII-hex (IFA_BITMAP), DE52 (PIN) et DE128 (MAC) binaires.
 *
 * Base sur le gabarit McPackager (ASCII) avec les specificites HPS SID v3.20 :
 *  - DE24 code fonction (n3) present (gestion reseau 1804 : 801/803/802)
 *  - DE43 nom/adresse accepteur = LLVAR ans..40 (VARIABLE chez HPS, pas fixe)
 *  - regle LLVAR = longueur du prefixe derivee du max (2 si <=99, 3 si 100-999)
 *    -> DE48 ..999 = LLL ; DE53 ..99 = LL ; DE54 ..120 = LLL ; DE55 ..255 = LLL
 *
 * Le framing (longueur 4o ASCII + header PowerCARD) est gere par
 * SwamLengthChannel, PAS par ce packager.
 *
 * Perimetre 1er increment : autorisation 1100/1110 + gestion reseau 1804/1814.
 * EMV/tokens/DCC/3DS/consolidation/chargeback : champs presents mais non exerces.
 */
@Component
public class SwamPackager extends ISOBasePackager {

    public SwamPackager() {
        super();
        setFieldPackager(buildFields());
    }

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fields = new ISOFieldPackager[129];

        fields[0]  = new IFA_NUMERIC  (4,    "MESSAGE TYPE INDICATOR");
        fields[1]  = new IFA_BITMAP   (16,   "BIT MAP");
        fields[2]  = new IFA_LLNUM    (19,   "PAN - PRIMARY ACCOUNT NUMBER");
        fields[3]  = new IFA_NUMERIC  (6,    "PROCESSING CODE");
        fields[4]  = new IFA_NUMERIC  (12,   "AMOUNT TRANSACTION");
        fields[5]  = new IFA_NUMERIC  (12,   "AMOUNT SETTLEMENT");
        fields[6]  = new IFA_NUMERIC  (12,   "AMOUNT CARDHOLDER BILLING");
        fields[7]  = new IFA_NUMERIC  (10,   "TRANSMISSION DATE AND TIME");
        fields[9]  = new IFA_NUMERIC  (8,    "CONVERSION RATE SETTLEMENT");
        fields[10] = new IFA_NUMERIC  (8,    "CONVERSION RATE CARDHOLDER BILLING");
        fields[11] = new IFA_NUMERIC  (6,    "SYSTEM TRACE AUDIT NUMBER");
        fields[12] = new IFA_NUMERIC  (12,   "DATE AND TIME LOCAL TRANSACTION");
        fields[14] = new IFA_NUMERIC  (4,    "DATE EXPIRATION");
        fields[15] = new IFA_NUMERIC  (6,    "DATE SETTLEMENT");
        fields[16] = new IFA_NUMERIC  (4,    "DATE CONVERSION");
        fields[18] = new IFA_NUMERIC  (4,    "MERCHANT TYPE");
        fields[19] = new IFA_NUMERIC  (3,    "ACQUIRING INSTITUTION COUNTRY CODE");
        fields[21] = new IFA_NUMERIC  (3,    "FORWARDING INSTITUTION COUNTRY CODE");
        fields[22] = new IF_CHAR      (12,   "POINT OF SERVICE DATA CODE");
        fields[23] = new IFA_NUMERIC  (3,    "CARD SEQUENCE NUMBER");
        fields[24] = new IFA_NUMERIC  (3,    "FUNCTION CODE");
        fields[25] = new IFA_NUMERIC  (4,    "MESSAGE REASON CODE");
        fields[27] = new IFA_NUMERIC  (1,    "AUTH ID RESPONSE LENGTH");
        fields[30] = new IFA_NUMERIC  (24,   "AMOUNTS ORIGINAL");
        fields[32] = new IFA_LLNUM    (11,   "ACQUIRING INSTITUTION ID CODE");
        fields[33] = new IFA_LLNUM    (11,   "FORWARDING INSTITUTION ID CODE");
        fields[35] = new IFA_LLNUM    (37,   "TRACK 2 DATA");
        fields[37] = new IF_CHAR      (12,   "RETRIEVAL REFERENCE NUMBER");
        fields[38] = new IF_CHAR      (6,    "AUTHORIZATION IDENTIFICATION");
        fields[39] = new IFA_NUMERIC  (3,    "ACTION CODE");
        fields[41] = new IF_CHAR      (8,    "CARD ACCEPTOR TERMINAL ID");
        fields[42] = new IF_CHAR      (15,   "CARD ACCEPTOR IDENTIFICATION CODE");
        fields[43] = new IFA_LLCHAR   (40,   "CARD ACCEPTOR NAME LOCATION");
        fields[45] = new IFA_LLCHAR   (76,   "TRACK 1 DATA");
        fields[46] = new IFA_LLLCHAR  (204,  "AMOUNTS FEES");
        fields[48] = new IFA_LLLCHAR  (999,  "ADDITIONAL DATA PRIVATE");
        fields[49] = new IFA_NUMERIC  (3,    "CURRENCY CODE TRANSACTION");
        fields[50] = new IFA_NUMERIC  (3,    "CURRENCY CODE SETTLEMENT");
        fields[51] = new IFA_NUMERIC  (3,    "CURRENCY CODE CARDHOLDER BILLING");
        fields[52] = new IFA_BINARY   (8,    "PIN DATA");
        fields[53] = new IFA_LLNUM    (99,   "SECURITY RELATED CONTROL INFO");
        fields[54] = new IFA_LLLCHAR  (120,  "ADDITIONAL AMOUNTS");
        fields[55] = new IFA_LLLBINARY(255,  "ICC DATA EMV");
        fields[56] = new IFA_LLNUM    (35,   "ORIGINAL DATA ELEMENTS");
        fields[60] = new IFA_LLLCHAR  (999,  "RESERVED NATIONAL");
        fields[61] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE POS DATA");
        fields[62] = new IFA_LLLCHAR  (999,  "RESERVED PRIVATE CPS");
        fields[73] = new IF_CHAR      (6,    "DATE ACTION");
        fields[93] = new IFA_LLNUM    (11,   "TRANSACTION DESTINATION INSTITUTION");
        fields[94] = new IFA_LLNUM    (11,   "TRANSACTION ORIGINATOR INSTITUTION");
        fields[100]= new IFA_LLNUM    (11,   "RECEIVING INSTITUTION ID CODE");
        fields[101]= new IFA_LLCHAR   (17,   "FILE NAME");
        fields[102]= new IFA_LLCHAR   (28,   "ACCOUNT IDENTIFICATION 1");
        fields[103]= new IFA_LLCHAR   (28,   "ACCOUNT IDENTIFICATION 2");
        fields[123]= new IFA_LLLCHAR  (999,  "RESERVED (VbV/3DS)");
        fields[127]= new IFA_LLLCHAR  (999,  "RESERVED PRIVATE (TOKENS)");
        fields[128]= new IFA_BINARY   (8,    "MESSAGE AUTHENTICATION CODE");

        return fields;
    }
}
