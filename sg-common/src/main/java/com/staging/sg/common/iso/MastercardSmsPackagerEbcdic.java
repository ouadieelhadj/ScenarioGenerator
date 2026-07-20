package com.staging.sg.common.iso;

import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOFieldPackager;
import org.jpos.iso.IFB_BINARY;
import org.jpos.iso.IFB_BITMAP;
import org.jpos.iso.IFE_CHAR;
import org.jpos.iso.IFE_LLCHAR;
import org.jpos.iso.IFE_LLLCHAR;
import org.jpos.iso.IFE_LLNUM;
import org.jpos.iso.IFE_NUMERIC;
import org.springframework.stereotype.Component;

/**
 * Mastercard Single Message System (SMS) Packager — EBCDIC.
 *
 * Reference : Mastercard Network Processing — Single Message System Guide
 *             2 June 2026, p.163 (data length representation)
 *
 * ISO 8583:1987 — MTI format : 0xxx
 *
 * ------------------------------------------------------------------
 *  POURQUOI CE PACKAGER
 * ------------------------------------------------------------------
 * Le MIP Mastercard parle EBCDIC, pas ASCII. Verifie sur les traces du
 * simulateur officiel (AcquirerSwitchSimulator, Mastercard Credit 26Q3) :
 *
 *     F0F8F0F0   = "0800" en EBCDIC
 *     30383030   = "0800" en ASCII   <- ce qu'emet MastercardSmsPackager
 *
 * Le chiffre '0' vaut 0xF0 en EBCDIC et 0x30 en ASCII. Tous les champs
 * caractere sont concernes : MTI, DE7, DE11, DE33, DE48, DE63, DE70...
 * y compris les PREFIXES DE LONGUEUR des champs LLVAR et LLLVAR :
 *
 *     DE33 : F0F6 F0F0F2F2F0F2      = "06" + "002202"
 *     DE110: F0F9F6 ...             = "096" + valeur
 *
 * Les champs BINAIRES ne sont pas concernes : bitmaps (IFB_BITMAP),
 * PIN block DE52 et Message Security Code DE96 (IFB_BINARY).
 *
 * ------------------------------------------------------------------
 *  CORRESPONDANCE AVEC MastercardSmsPackager (ASCII)
 * ------------------------------------------------------------------
 * Memes champs, memes longueurs, seuls les types changent :
 *
 *     IFA_NUMERIC  -> IFE_NUMERIC
 *     IFA_LLNUM    -> IFE_LLNUM
 *     IFA_LLCHAR   -> IFE_LLCHAR
 *     IFA_LLLCHAR  -> IFE_LLLCHAR
 *     IFA_LLLNUM   -> IFE_LLLCHAR   (jPOS 2.1.9 n'a pas d'IFE_LLLNUM)
 *     IF_CHAR      -> IFE_CHAR
 *     IFA_BINARY   -> IFB_BINARY    (vrai binaire, pas de l'hex ASCII)
 *     IFB_BITMAP   -> IFB_BITMAP    (inchange)
 *
 * DE64 et DE128 restent a null : le SMS n'utilise PAS de MAC
 * (guide p.775 et p.1096).
 *
 * Structure calquee sur SwamPackager : @Component, tableau construit par
 * une methode d'instance.
 */
@Component
public class MastercardSmsPackagerEbcdic extends ISOBasePackager {

    public MastercardSmsPackagerEbcdic() {
        super();
        setFieldPackager(buildFields());
    }

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fld = new ISOFieldPackager[129];

        // MTI et bitmaps
        fld[0]  = new IFE_NUMERIC(4,  "MESSAGE TYPE INDICATOR");
        fld[1]  = new IFB_BITMAP(16,  "BIT MAP, SECONDARY");

        // Donnees porteur et montants (0200)
        fld[2]  = new IFE_LLNUM(19,   "PRIMARY ACCOUNT NUMBER");
        fld[3]  = new IFE_NUMERIC(6,  "PROCESSING CODE");
        fld[4]  = new IFE_NUMERIC(12, "AMOUNT, TRANSACTION");
        fld[5]  = new IFE_NUMERIC(12, "AMOUNT, SETTLEMENT");
        fld[6]  = new IFE_NUMERIC(12, "AMOUNT, CARDHOLDER BILLING");

        // DE7 : n-10, fixe — Transmission Date and Time (UTC, MMDDhhmmss)
        fld[7]  = new IFE_NUMERIC(10, "TRANSMISSION DATE AND TIME");

        fld[8]  = new IFE_NUMERIC(8,  "AMOUNT, CARDHOLDER BILLING FEE");
        fld[9]  = new IFE_NUMERIC(8,  "CONVERSION RATE, SETTLEMENT");
        fld[10] = new IFE_NUMERIC(8,  "CONVERSION RATE, CARDHOLDER BILLING");

        // DE11 : n-6, fixe — System Trace Audit Number
        fld[11] = new IFE_NUMERIC(6,  "SYSTEM TRACE AUDIT NUMBER");

        fld[12] = new IFE_NUMERIC(6,  "TIME, LOCAL TRANSACTION");
        fld[13] = new IFE_NUMERIC(4,  "DATE, LOCAL TRANSACTION");
        fld[14] = new IFE_NUMERIC(4,  "DATE, EXPIRATION");
        fld[15] = new IFE_NUMERIC(4,  "DATE, SETTLEMENT");
        fld[16] = new IFE_NUMERIC(4,  "DATE, CONVERSION");
        fld[17] = new IFE_NUMERIC(4,  "DATE, CAPTURE");
        fld[18] = new IFE_NUMERIC(4,  "MERCHANT TYPE");
        fld[19] = new IFE_NUMERIC(3,  "ACQUIRING INSTITUTION COUNTRY CODE");
        fld[20] = new IFE_NUMERIC(3,  "PAN EXTENDED COUNTRY CODE");
        fld[21] = new IFE_NUMERIC(3,  "FORWARDING INSTITUTION COUNTRY CODE");
        fld[22] = new IFE_NUMERIC(3,  "POINT OF SERVICE DATA CODE");
        fld[23] = new IFE_NUMERIC(3,  "CARD SEQUENCE NUMBER");
        fld[24] = new IFE_NUMERIC(3,  "NETWORK INTERNATIONAL IDENTIFIER");
        fld[25] = new IFE_NUMERIC(2,  "POINT OF SERVICE CONDITION CODE");
        fld[26] = new IFE_NUMERIC(2,  "POINT OF SERVICE PIN CAPTURE CODE");
        fld[27] = new IFE_NUMERIC(1,  "AUTHORIZATION ID RESPONSE LENGTH");
        fld[28] = new IFE_NUMERIC(9,  "AMOUNT, TRANSACTION FEE");
        fld[29] = new IFE_NUMERIC(9,  "AMOUNT, SETTLEMENT FEE");
        fld[30] = new IFE_NUMERIC(9,  "AMOUNT, TRANSACTION PROCESSING FEE");
        fld[31] = new IFE_NUMERIC(9,  "AMOUNT, SETTLEMENT PROCESSING FEE");

        // DE32 : n..9, LLVAR — exactement 9 chiffres (guide p.383)
        fld[32] = new IFE_LLNUM(11,   "ACQUIRING INSTITUTION ID CODE");

        // DE33 : n..10, LLVAR — la trace montre 6 chiffres (ICA)
        fld[33] = new IFE_LLNUM(10,   "FORWARDING INSTITUTION ID CODE");

        fld[34] = new IFE_LLNUM(28,   "PAN, EXTENDED");
        fld[35] = new IFE_LLCHAR(37,  "TRACK 2 DATA");
        fld[36] = new IFE_LLLCHAR(104,"TRACK 3 DATA");
        fld[37] = new IFE_CHAR(12,    "RETRIEVAL REFERENCE NUMBER");
        fld[38] = new IFE_CHAR(6,     "AUTHORIZATION ID RESPONSE");

        // DE39 : an-2, fixe — Response Code
        fld[39] = new IFE_CHAR(2,     "RESPONSE CODE");

        fld[40] = new IFE_CHAR(3,     "SERVICE RESTRICTION CODE");
        fld[41] = new IFE_CHAR(8,     "CARD ACCEPTOR TERMINAL ID");
        fld[42] = new IFE_CHAR(15,    "CARD ACCEPTOR ID CODE");
        fld[43] = new IFE_LLCHAR(40,  "CARD ACCEPTOR NAME/LOCATION");

        // DE44 : an..25, LLVAR — present si DE39=30
        fld[44] = new IFE_LLCHAR(25,  "ADDITIONAL RESPONSE DATA");

        fld[45] = new IFE_LLCHAR(76,  "TRACK 1 DATA");
        fld[46] = new IFE_LLLCHAR(999,"AMOUNTS, FEES");
        fld[47] = new IFE_LLLCHAR(999,"ADDITIONAL DATA: NATIONAL USE");

        // DE48 : ans..999, LLLVAR — porte le subelement 11 (echange de cles)
        fld[48] = new IFE_LLLCHAR(999,"ADDITIONAL DATA: PRIVATE USE");

        fld[49] = new IFE_CHAR(3,     "CURRENCY CODE, TRANSACTION");
        fld[50] = new IFE_CHAR(3,     "CURRENCY CODE, SETTLEMENT");
        fld[51] = new IFE_CHAR(3,     "CURRENCY CODE, CARDHOLDER BILLING");

        // DE52 : binaire pur — PIN block
        fld[52] = new IFB_BINARY(8,   "PIN DATA");

        fld[53] = new IFE_NUMERIC(16, "SECURITY RELATED CONTROL INFO");
        fld[54] = new IFE_LLLCHAR(120,"ADDITIONAL AMOUNTS");
        fld[55] = new IFE_LLLCHAR(999,"ICC DATA / EMV");
        fld[56] = new IFE_LLLCHAR(999,"RESERVED ISO");
        fld[57] = new IFE_LLLCHAR(999,"RESERVED NATIONAL");
        fld[58] = new IFE_LLLCHAR(999,"RESERVED NATIONAL");
        fld[59] = new IFE_LLLCHAR(999,"RESERVED NATIONAL");
        fld[60] = new IFE_LLLCHAR(999,"RESERVED PRIVATE");
        fld[61] = new IFE_LLLCHAR(999,"RESERVED PRIVATE");
        fld[62] = new IFE_LLLCHAR(999,"RESERVED PRIVATE");

        // DE63 : ans..999, LLLVAR — Network Data (MCC + Banknet reference)
        fld[63] = new IFE_LLLCHAR(999,"NETWORK DATA");

        // DE64 : NON UTILISE dans le SMS (guide p.775)
        fld[64] = null;

        fld[65] = null; // Bit Map Extended
        fld[66] = null; // Settlement Code
        fld[67] = null; // Extended Payment Code
        fld[68] = null; // Receiving Institution Country Code
        fld[69] = null; // Settlement Institution Country Code

        // DE70 : n-3, fixe — Network Management Information Code
        fld[70] = new IFE_NUMERIC(3,  "NETWORK MANAGEMENT INFORMATION CODE");

        fld[71] = null; // Message Number
        fld[72] = null; // Message Number Last
        fld[73] = null; // Date, Action
        fld[74] = null; // Credits, Number
        fld[75] = null; // Credits, Reversal Number
        fld[76] = null; // Debits, Number
        fld[77] = null; // Debits, Reversal Number
        fld[78] = null; // Transfer Number
        fld[79] = null; // Transfer, Reversal Number
        fld[80] = null; // Inquiries, Number
        fld[81] = null; // Authorizations, Number

        fld[82] = new IFE_NUMERIC(12, "CREDITS, PROCESSING FEE AMOUNT");
        fld[83] = new IFE_NUMERIC(12, "CREDITS, TRANSACTION FEE AMOUNT");
        fld[84] = new IFE_NUMERIC(12, "DEBITS, PROCESSING FEE AMOUNT");
        fld[85] = new IFE_NUMERIC(12, "DEBITS, TRANSACTION FEE AMOUNT");
        fld[86] = new IFE_NUMERIC(16, "CREDITS, AMOUNT");
        fld[87] = new IFE_NUMERIC(16, "CREDITS, REVERSAL AMOUNT");
        fld[88] = new IFE_NUMERIC(16, "DEBITS, AMOUNT");
        fld[89] = new IFE_NUMERIC(16, "DEBITS, REVERSAL AMOUNT");
        fld[90] = new IFE_NUMERIC(42, "ORIGINAL DATA ELEMENTS");
        fld[91] = new IFE_CHAR(1,     "FILE UPDATE CODE");
        fld[92] = new IFE_NUMERIC(2,  "FILE SECURITY CODE");
        fld[93] = new IFE_LLNUM(6,    "RESPONSE INDICATOR");
        fld[94] = new IFE_LLCHAR(7,   "SERVICE INDICATOR");
        fld[95] = new IFE_NUMERIC(42, "REPLACEMENT AMOUNTS");

        // DE96 : b-8 binaire — Message Security Code (sign-on)
        fld[96] = new IFB_BINARY(8,   "MESSAGE SECURITY CODE");

        fld[97]  = new IFE_NUMERIC(17, "AMOUNT, NET SETTLEMENT");
        fld[98]  = new IFE_CHAR(25,    "PAYEE");
        fld[99]  = new IFE_LLNUM(11,   "SETTLEMENT INSTITUTION ID CODE");
        fld[100] = new IFE_LLNUM(11,   "RECEIVING INSTITUTION ID CODE");
        fld[101] = new IFE_LLCHAR(17,  "FILE NAME");
        fld[102] = new IFE_LLCHAR(28,  "ACCOUNT ID 1");
        fld[103] = new IFE_LLCHAR(28,  "ACCOUNT ID 2");
        fld[104] = new IFE_LLLCHAR(100,"TRANSACTION DESCRIPTION");

        // DE105-127 : reserved / national use.
        // DE110 (Encryption Data) est dans cette plage : il porte le keyblock
        // TR-31 du mecanisme 163. Format observe sur la trace : subelements
        // ASCII ID(2)+len(3)+valeur, donc IFE_LLLCHAR convient.
        for (int i = 105; i <= 127; i++) {
            fld[i] = new IFE_LLLCHAR(999, "RESERVED DE " + i);
        }

        // DE128 : NON UTILISE dans le SMS (guide p.1096)
        fld[128] = null;

        return fld;
    }
}
