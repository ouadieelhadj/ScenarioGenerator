package com.staging.sg.common.iso;

import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOFieldPackager;
import org.jpos.iso.IF_CHAR;
import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IFA_BINARY;
import org.jpos.iso.IFA_LLNUM;
import org.jpos.iso.IFA_LLCHAR;
import org.jpos.iso.IFA_LLLCHAR;
import org.jpos.iso.IFA_LLLNUM;
import org.jpos.iso.IFB_BITMAP;
import org.springframework.stereotype.Component;

/**
 * Mastercard Single Message System (SMS) Packager.
 *
 * Reference : Mastercard Network Processing — Single Message System Guide
 *             2 June 2026 (1909 pages)
 *
 * ISO 8583:1987 — MTI format : 0xxx
 *
 * IMPORTANT : Le lien Mastercard utilise EBCDIC (p.163 du guide).
 *             Ce packager utilise ASCII (IFA_*) pour l'environnement de
 *             developpement/test. Pour un lien MIP reel en EBCDIC, utiliser
 *             MastercardSmsPackagerEbcdic (a creer avec IFE_*).
 *
 * Champs implementes pour le socle reseau (0800/0810) :
 *   DE1  : Bit Map Secondary        (b-8,    fixe)
 *   DE7  : Transmission Date/Time   (n-10,   fixe)    p.345
 *   DE11 : System Trace Audit Number(n-6,    fixe)    p.352
 *   DE33 : Forwarding Institution ID(n..10,  LLVAR)   p.385
 *   DE39 : Response Code            (an-2,   fixe)    p.399
 *   DE44 : Additional Response Data (an..25, LLVAR)   p.416
 *   DE48 : Additional Data          (ans..999,LLLVAR) p.445
 *   DE63 : Network Data             (ans..999,LLLVAR) p.768
 *   DE70 : Network Mgmt Info Code   (n-3,    fixe)    p.776
 *   DE96 : Message Security Code    (b-8,    fixe)    p.792
 *
 * Layouts utilises :
 *   0800 : DE1,7,11,33,(48),(63),70,(96)   p.293
 *   0810 : DE1,7,11,33,39,(44),(48),(63),70 p.296
 */
@Component
public class MastercardSmsPackager extends ISOBasePackager {

    private ISOFieldPackager[] buildFields() {
        ISOFieldPackager[] fld = new ISOFieldPackager[129];

        // DE0  : pas de champ (MTI gere par le packager)
        fld[0]  = new IFA_NUMERIC(4, "Message Type Indicator");

        // DE1  : Bit Map Secondary (b-8 = 8 octets binaires)
        fld[1]  = new IFB_BITMAP(16, "Bit Map, Secondary");

        // DE2-6 : non utilises dans le socle 0800/0810 (a completer pour 0200)
        fld[2]  = new IFA_LLNUM(19, "Primary Account Number");
        fld[3]  = new IFA_NUMERIC(6, "Processing Code");
        fld[4]  = new IFA_NUMERIC(12, "Amount, Transaction");
        fld[5]  = new IFA_NUMERIC(12, "Amount, Settlement");
        fld[6]  = new IFA_NUMERIC(12, "Amount, Cardholder Billing");

        // DE7  : n-10, Fixe — Transmission Date and Time (UTC, MMDDhhmmss)
        fld[7]  = new IFA_NUMERIC(10, "Transmission Date and Time");

        fld[8]  = new IFA_NUMERIC(8,  "Amount, Cardholder Billing Fee");
        fld[9]  = new IFA_NUMERIC(8,  "Conversion Rate, Settlement");
        fld[10] = new IFA_NUMERIC(8,  "Conversion Rate, Cardholder Billing");

        // DE11 : n-6, Fixe — System Trace Audit Number (STAN)
        fld[11] = new IFA_NUMERIC(6,  "System Trace Audit Number");

        fld[12] = new IFA_NUMERIC(6,  "Time, Local Transaction");
        fld[13] = new IFA_NUMERIC(4,  "Date, Local Transaction");
        fld[14] = new IFA_NUMERIC(4,  "Date, Expiration");
        fld[15] = new IFA_NUMERIC(4,  "Date, Settlement");
        fld[16] = new IFA_NUMERIC(4,  "Date, Conversion");
        fld[17] = new IFA_NUMERIC(4,  "Date, Capture");
        fld[18] = new IFA_NUMERIC(4,  "Merchant Type");
        fld[19] = new IFA_NUMERIC(3,  "Acquiring Institution Country Code");
        fld[20] = new IFA_NUMERIC(3,  "Primary Account Number, Extended");
        fld[21] = new IFA_NUMERIC(3,  "Forwarding Institution Country Code");
        fld[22] = new IFA_NUMERIC(3,  "Point of Service Data Code");
        fld[23] = new IFA_NUMERIC(3,  "Card Sequence Number");
        fld[24] = new IFA_NUMERIC(3,  "Network International Identifier");
        fld[25] = new IFA_NUMERIC(2,  "Point of Service Condition Code");
        fld[26] = new IFA_NUMERIC(2,  "Point of Service PIN Capture Code");
        fld[27] = new IFA_NUMERIC(1,  "Authorization ID Response Length");
        fld[28] = new IFA_NUMERIC(9,  "Amount, Transaction Fee");
        fld[29] = new IFA_NUMERIC(9,  "Amount, Settlement Fee");
        fld[30] = new IFA_NUMERIC(9,  "Amount, Transaction Processing Fee");
        fld[31] = new IFA_NUMERIC(9,  "Amount, Settlement Processing Fee");
        fld[32] = new IFA_LLNUM(11,   "Acquiring Institution ID Code");

        // DE33 : n..10, LLVAR (2 positions de longueur)
        fld[33] = new IFA_LLNUM(10,   "Forwarding Institution ID Code");

        fld[34] = new IFA_LLNUM(28,   "Primary Account Number, Extended");
        fld[35] = new IFA_LLCHAR(37,  "Track 2 Data");
        fld[36] = new IFA_LLLNUM(104, "Track 3 Data");
        fld[37] = new IF_CHAR(12,     "Retrieval Reference Number");
        fld[38] = new IF_CHAR(6,      "Authorization ID Response");

        // DE39 : an-2, Fixe — Response Code
        fld[39] = new IF_CHAR(2,      "Response Code");

        fld[40] = new IF_CHAR(3,      "Service Restriction Code");
        fld[41] = new IF_CHAR(8,      "Card Acceptor Terminal ID");
        fld[42] = new IF_CHAR(15,     "Card Acceptor ID Code");
        fld[43] = new IFA_LLCHAR(40,  "Card Acceptor Name/Location");

        // DE44 : an..25, LLVAR — Additional Response Data
        fld[44] = new IFA_LLCHAR(25,  "Additional Response Data");

        fld[45] = new IFA_LLCHAR(76,  "Track 1 Data");
        fld[46] = new IFA_LLLCHAR(999,"Amounts, Fees");
        fld[47] = new IFA_LLLCHAR(999,"Additional Data: National Use");

        // DE48 : ans..999, LLLVAR — Additional Data: Private Use
        fld[48] = new IFA_LLLCHAR(999,"Additional Data: Private Use");

        fld[49] = new IF_CHAR(3,      "Currency Code, Transaction");
        fld[50] = new IF_CHAR(3,      "Currency Code, Settlement");
        fld[51] = new IF_CHAR(3,      "Currency Code, Cardholder Billing");
        fld[52] = new IFA_BINARY(8,   "PIN Data");
        fld[53] = new IFA_NUMERIC(16, "Security Related Control Info");
        fld[54] = new IFA_LLLCHAR(120,"Additional Amounts");
        fld[55] = new IFA_LLLCHAR(999,"ICC Data / EMV");
        fld[56] = new IFA_LLLCHAR(999,"Reserved ISO");
        fld[57] = new IFA_LLLCHAR(999,"Reserved National");
        fld[58] = new IFA_LLLCHAR(999,"Reserved National");
        fld[59] = new IFA_LLLCHAR(999,"Reserved National");
        fld[60] = new IFA_LLLCHAR(999,"Reserved Private");
        fld[61] = new IFA_LLLCHAR(999,"Reserved Private");
        fld[62] = new IFA_LLLCHAR(999,"Reserved Private");

        // DE63 : ans..999, LLLVAR — Network Data
        fld[63] = new IFA_LLLCHAR(999,"Network Data");

        // DE64 : NON UTILISE dans le SMS (p.775)
        fld[64] = null;

        fld[65] = null; // Bit Map Extended - non utilise SMS
        fld[66] = null; // Settlement Code - non utilise SMS
        fld[67] = null; // Extended Payment Code - non utilise SMS
        fld[68] = null; // Receiving Institution Country Code - non utilise SMS
        fld[69] = null; // Settlement Institution Country Code - non utilise SMS

        // DE70 : n-3, Fixe — Network Management Information Code
        fld[70] = new IFA_NUMERIC(3,  "Network Management Information Code");

        fld[71] = null; // Message Number - non utilise SMS
        fld[72] = null; // Message Number Last - non utilise SMS
        fld[73] = null; // Date, Action - non utilise SMS
        fld[74] = null; // Credits, Number - non utilise SMS
        fld[75] = null; // Credits, Reversal Number - non utilise SMS
        fld[76] = null; // Debits, Number - non utilise SMS
        fld[77] = null; // Debits, Reversal Number - non utilise SMS
        fld[78] = null; // Transfer Number - non utilise SMS
        fld[79] = null; // Transfer, Reversal Number - non utilise SMS
        fld[80] = null; // Inquiries, Number - non utilise SMS
        fld[81] = null; // Authorizations, Number - non utilise SMS

        fld[82] = new IFA_NUMERIC(12, "Credits, Processing Fee Amount");
        fld[83] = new IFA_NUMERIC(12, "Credits, Transaction Fee Amount");
        fld[84] = new IFA_NUMERIC(12, "Debits, Processing Fee Amount");
        fld[85] = new IFA_NUMERIC(12, "Debits, Transaction Fee Amount");
        fld[86] = new IFA_NUMERIC(16, "Credits, Amount");
        fld[87] = new IFA_NUMERIC(16, "Credits, Reversal Amount");
        fld[88] = new IFA_NUMERIC(16, "Debits, Amount");
        fld[89] = new IFA_NUMERIC(16, "Debits, Reversal Amount");
        fld[90] = new IFA_NUMERIC(42, "Original Data Elements");
        fld[91] = new IF_CHAR(1,      "File Update Code");
        fld[92] = new IFA_NUMERIC(2,  "File Security Code");
        fld[93] = new IFA_LLNUM(6,    "Response Indicator");
        fld[94] = new IFA_LLCHAR(7,   "Service Indicator");
        fld[95] = new IFA_NUMERIC(42, "Replacement Amounts");

        // DE96 : b-8 (binary, 8 octets = 64 bits) — Message Security Code
        // Utilise dans le 0800 sign-on pour authentifier l'acces
        fld[96] = new IFA_BINARY(8,   "Message Security Code");

        fld[97]  = new IFA_NUMERIC(17, "Amount, Net Settlement");
        fld[98]  = new IF_CHAR(25,     "Payee");
        fld[99]  = new IFA_LLNUM(11,   "Settlement Institution ID Code");
        fld[100] = new IFA_LLNUM(11,   "Receiving Institution ID Code");
        fld[101] = new IFA_LLCHAR(17,  "File Name");
        fld[102] = new IFA_LLCHAR(28,  "Account ID 1");
        fld[103] = new IFA_LLCHAR(28,  "Account ID 2");
        fld[104] = new IFA_LLLCHAR(100,"Transaction Description");

        // DE105-DE127 : reserved / national use
        for (int i = 105; i <= 127; i++) {
            fld[i] = new IFA_LLLCHAR(999, "Reserved DE " + i);
        }

        // DE128 : NON UTILISE dans le SMS (p.1096)
        fld[128] = null;

        return fld;
    }

    public MastercardSmsPackager() {
        super();
        setFieldPackager(buildFields());
    }
}
