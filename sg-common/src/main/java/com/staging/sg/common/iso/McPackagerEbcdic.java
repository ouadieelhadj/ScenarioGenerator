package com.staging.sg.common.iso;

import org.jpos.iso.*;
import org.springframework.stereotype.Component;

/**
 * Mastercard DMAS ISO 8583 Packager — EBCDIC + bitmap binaire.
 *
 * Version MINIMALE (D-sign-on) : seuls les champs nécessaires au
 * message réseau 0800/0810 de sign-on sont définis (MTI, bitmap,
 * DE7, DE11, DE39, DE70). Les autres champs (DE2..DE64, PIN, MAC...)
 * seront ajoutés dans la version complète (D3).
 *
 * Conforme DMAS : EBCDIC DISPLAY (IFE_*), numériques right-justified
 * leading zeros, bitmap b-64 binaire (IFB_BITMAP).
 *
 * NE REMPLACE PAS McPackager (ASCII) — coexiste à côté.
 */
@Component
public class McPackagerEbcdic extends ISOBasePackager {

    public McPackagerEbcdic() {
        super();
        setFieldPackager(buildFields());
    }

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fields = new ISOFieldPackager[129];

        fields[0]  = new IFE_NUMERIC (4,  "MESSAGE TYPE INDICATOR");
        fields[1]  = new IFB_BITMAP  (16, "BIT MAP");
        // DE2 : PAN / Member Group ID — LLVAR numerique EBCDIC
        fields[2]  = new IFE_LLNUM   (19, "PRIMARY ACCOUNT NUMBER");
        // DE7 : MMDDhhmmss (transmission date & time)
        fields[7]  = new IFE_NUMERIC (10, "TRANSMISSION DATE AND TIME");
        // DE11 : STAN
        fields[11] = new IFE_NUMERIC (7,  "SYSTEM TRACE AUDIT NUMBER");
        // === DE de transaction 0100/0110 ===
        // DE3 : processing code — n-6
        fields[3]  = new IFE_NUMERIC (6,  "PROCESSING CODE");
        // DE4 : amount transaction — n-12
        fields[4]  = new IFE_NUMERIC (12, "AMOUNT TRANSACTION");
        // DE12 : time local transaction — n-6 (HHmmss)
        fields[12] = new IFE_NUMERIC (6,  "TIME LOCAL TRANSACTION");
        // DE13 : date local transaction — n-4 (MMDD)
        fields[13] = new IFE_NUMERIC (4,  "DATE LOCAL TRANSACTION");
        // DE14 : date expiration — n-4 (YYMM)
        fields[14] = new IFE_NUMERIC (4,  "DATE EXPIRATION");
        // DE18 : merchant type (MCC) — n-4
        fields[18] = new IFE_NUMERIC (4,  "MERCHANT TYPE");
        // DE22 : POS entry mode — n-3
        fields[22] = new IFE_NUMERIC (3,  "POS ENTRY MODE");
        // DE32 : acquiring institution id — LLVAR n-11
        fields[32] = new IFE_NUMERIC (6,  "ACQUIRING INSTITUTION ID");
        // DE33 : forwarding institution id (002202 = reseau MC) — n-6
        fields[33] = new IFE_NUMERIC (6,  "FORWARDING INSTITUTION ID");
        // DE37 : retrieval reference number — ans-12
        fields[37] = new IFE_CHAR    (12, "RETRIEVAL REFERENCE NUMBER");
        // DE39 : response code (présent sur 0810)
        fields[39] = new IFE_CHAR    (2,  "RESPONSE CODE");
        // DE41 : acceptor terminal id — ans-8
        fields[41] = new IFE_CHAR    (8,  "ACCEPTOR TERMINAL ID");
        // DE42 : acceptor id code — ans-15
        fields[42] = new IFE_CHAR    (15, "ACCEPTOR ID CODE");
        // DE48 : private data (Key Exchange Block en DMAS) — LLLVAR EBCDIC
        fields[48] = new IFE_LLLCHAR (999, "ADDITIONAL DATA PRIVATE");
        // DE49 : currency code transaction — n-3
        fields[49] = new IFE_NUMERIC (3,  "CURRENCY CODE TRANSACTION");
        // DE52 : PIN data — b-8 (binaire, PAS EBCDIC)
        fields[52] = new IFB_BINARY  (8,  "PIN DATA");
        // DE61 : POS data — LLLVAR EBCDIC (subfields, dont sf7 POS Transaction Status)
        fields[61] = new IFE_LLLCHAR (26, "POS DATA");
        // DE60 : advice reason code (0420 reversal advice) — LLLVAR ans...060
        fields[60] = new IFE_LLLCHAR (60, "ADVICE REASON CODE");
        // DE63 : network data (Banknet ref) — LLLVAR EBCDIC
        fields[63] = new IFE_LLLCHAR (50, "NETWORK DATA");
        // DE70 : network management code (001 sign-on, 002 sign-off, 270 echo)
        fields[70] = new IFE_NUMERIC (3,  "NETWORK MANAGEMENT CODE");
        // DE90 : original data elements (reversal) — n-42 : [MTI 4][STAN 6][DE7 10][DE32 11][DE33 11]
        fields[90] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS");

        fields[94] = new IFE_CHAR    (7,  "SERVICE INDICATOR");
        fields[96] = new IFE_NUMERIC (6,  "MESSAGE SECURITY CODE");
        fields[5]  = new IFE_NUMERIC (12, "AMOUNT, RECONCILIATION");
        fields[6]  = new IFE_NUMERIC (12, "AMOUNT, CARDHOLDER BILLING");
        fields[8]  = new IFE_NUMERIC (8,  "AMOUNT, CARDHOLDER BILLING FEE");
        fields[9]  = new IFE_NUMERIC (8,  "CONVERSION RATE, RECONCILIATION");
        fields[10] = new IFE_NUMERIC (8,  "CONVERSION RATE, CARDHOLDER BILLING");
        fields[15] = new IFE_NUMERIC (4,  "DATE, SETTLEMENT");
        fields[16] = new IFE_NUMERIC (4,  "DATE, CONVERSION");
        fields[17] = new IFE_NUMERIC (4,  "DATE, CAPTURE");
        fields[19] = new IFE_NUMERIC (3,  "ACQUIRING INSTITUTION COUNTRY CODE");
        fields[20] = new IFE_NUMERIC (3,  "PAN EXTENDED COUNTRY CODE");
        fields[21] = new IFE_NUMERIC (3,  "FORWARDING INSTITUTION COUNTRY CODE");
        fields[23] = new IFE_NUMERIC (3,  "CARD SEQUENCE NUMBER");
        fields[24] = new IFE_NUMERIC (2,  "FUNCTION CODE");
        fields[25] = new IFE_NUMERIC (2,  "MESSAGE REASON CODE");
        fields[26] = new IFE_NUMERIC (2,  "CARD ACCEPTOR BUSINESS CODE");
        fields[27] = new IFE_CHAR    (9,  "APPROVAL CODE LENGTH");
        fields[28] = new IFE_CHAR    (9,  "DATE, RE-RECONCILIATION");
        fields[29] = new IFE_NUMERIC (8,  "AMOUNT, RECONCILIATION FEE");
        fields[30] = new IFE_NUMERIC (8,  "AMOUNT, TRANSACTION FEE");
        fields[31] = new IFE_NUMERIC (6,  "VALUES PRESERVED FOR ANSI X9.2");
        fields[34] = new IFE_LLCHAR  (37, "PAN, EXTENDED");
        fields[35] = new IFE_LLCHAR  (37, "TRACK 2 DATA");
        fields[36] = new IFE_CHAR    (12, "TRACK 3 DATA");
        fields[38] = new IFE_CHAR    (6,  "APPROVAL CODE");
        fields[40] = new IFE_CHAR    (8,  "SERVICE RESTRICTION CODE");
        fields[43] = new IFE_CHAR    (40, "CARD ACCEPTOR NAME/LOCATION");
        fields[44] = new IFE_LLCHAR  (25, "ADDITIONAL RESPONSE DATA");
        fields[45] = new IFE_LLCHAR  (76, "TRACK 1 DATA");
        fields[46] = new IFE_LLLCHAR (999, "AMOUNTS, FEES");
        fields[47] = new IFE_LLLCHAR (999, "ADDITIONAL DATA, NATIONAL");
        fields[50] = new IFE_NUMERIC (3,  "CURRENCY CODE, RECONCILIATION");
        fields[51] = new IFE_NUMERIC (3,  "CURRENCY CODE, CARDHOLDER BILLING");
        fields[53] = new IFE_NUMERIC (16, "SECURITY-RELATED CONTROL INFO");
        fields[54] = new IFE_LLLCHAR (240, "AMOUNTS, ADDITIONAL");
        fields[55] = new IFB_LLLBINARY (255, "INTEGRATED CIRCUIT CARD DATA");
        fields[56] = new IFE_LLLCHAR (37, "ORIGINAL DATA ELEMENTS, MESSAGE TYPE");
        fields[62] = new IFE_LLLCHAR (999, "ADDITIONAL DATA-2");
        fields[64] = new IFB_BINARY  (8,  "MAC, PRIMARY");
        fields[65] = new IFB_BINARY  (8,  "BIT MAP, EXTENDED");
        fields[66] = new IFB_BINARY  (8,  "SETTLEMENT CODE");
        fields[67] = new IFE_NUMERIC (2,  "EXTENDED PAYMENT CODE");
        fields[68] = new IFE_NUMERIC (3,  "RECEIVING INSTITUTION COUNTRY CODE");
        fields[69] = new IFE_NUMERIC (3,  "SETTLEMENT INSTITUTION COUNTRY CODE");
        fields[85] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS-2");
        fields[86] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS-3");
        fields[87] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS-4");
        fields[88] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS-5");
        fields[89] = new IFE_NUMERIC (42, "ORIGINAL DATA ELEMENTS-6");
        fields[91] = new IFE_NUMERIC (11, "FILE UPDATE CODE");
        fields[92] = new IFE_CHAR    (7,  "FILE SECURITY CODE");
        fields[93] = new IFE_CHAR    (7,  "RESPONSE INDICATOR");
        fields[95] = new IFE_NUMERIC (42, "REPLACEMENT AMOUNTS");
        fields[97] = new IFE_LLNUM   (11, "AMOUNT, NET SETTLEMENT");
        fields[98] = new IFE_LLNUM   (11, "PAYEE");
        fields[99] = new IFE_LLNUM   (11, "SETTLEMENT INSTITUTION ID CODE");
        fields[100]= new IFE_LLNUM   (11, "RECEIVING INSTITUTION ID CODE");
        fields[101]= new IFE_LLCHAR  (17, "FILE NAME");
        fields[102]= new IFE_LLCHAR  (28, "ACCOUNT IDENTIFICATION 1");
        fields[103]= new IFE_LLCHAR  (28, "ACCOUNT IDENTIFICATION 2");
        fields[104]= new IFE_LLLCHAR (999, "TRANSACTION DESCRIPTION");
        fields[105]= new IFE_LLLCHAR (999, "RESERVED FOR ISO USE 1");
        fields[106]= new IFE_LLLCHAR (999, "RESERVED FOR ISO USE 2");
        fields[107]= new IFE_LLLCHAR (999, "RESERVED FOR ISO USE 3");
        fields[108]= new IFE_LLLCHAR (999, "RESERVED FOR NATIONAL USE 1");
        fields[109]= new IFE_CHAR    (1,  "RESERVED FOR NATIONAL USE 2");
        fields[110]= new IFE_LLLCHAR (999, "ENCRYPTION DATA");
        fields[111]= new IFE_CHAR    (1,  "RESERVED FOR NATIONAL USE 4 (HU)");
        fields[112]= new IFE_CHAR    (1,  "RESERVED FOR NATIONAL USE 5 (HU)");
        fields[113]= new IFE_LLCHAR  (20, "RESERVED FOR PRIVATE USE 1");
        fields[114]= new IFE_LLCHAR  (20, "RESERVED FOR PRIVATE USE 2");
        fields[115]= new IFE_LLCHAR  (20, "RESERVED FOR PRIVATE USE 3");
        fields[116]= new IFE_LLLCHAR (999, "RESERVED FOR PRIVATE USE 4");
        fields[117]= new IFE_LLLCHAR (999, "RESERVED FOR PRIVATE USE 5");
        fields[118]= new IFE_LLLCHAR (999, "RESERVED FOR PRIVATE USE 6");
        fields[119]= new IFE_LLLCHAR (999, "RESERVED FOR PRIVATE USE 7");
        fields[120]= new IFE_LLLCHAR (999, "RECORD DATA");
        fields[121]= new IFE_LLLCHAR (6,  "AUTHORIZING AGENT INSTITUTION ID");
        fields[122]= new IFE_LLLCHAR (999, "ADDITIONAL VERIFICATION DATA");
        fields[123]= new IFE_LLLCHAR (512, "RESERVED FOR PRIVATE USE 8");
        fields[124]= new IFE_LLLCHAR (299, "INFORMATION TEXT");
        fields[125]= new IFB_BINARY  (8,  "NETWORK MANAGEMENT INFORMATION");
        fields[126]= new IFE_LLLCHAR (100, "ISSUER TRACE ID");
        fields[127]= new IFE_LLLCHAR (100, "RESERVED FOR PRIVATE USE 9");
        fields[128]= new IFB_BINARY  (8,  "MAC, SECONDARY");
        return fields;
    }
}
