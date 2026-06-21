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
        fields[11] = new IFE_NUMERIC (6,  "SYSTEM TRACE AUDIT NUMBER");
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
        fields[32] = new IFE_LLNUM   (11, "ACQUIRING INSTITUTION ID");
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
        // DE63 : network data (Banknet ref) — LLLVAR EBCDIC
        fields[63] = new IFE_LLLCHAR (50, "NETWORK DATA");
        // DE70 : network management code (001 sign-on, 002 sign-off, 270 echo)
        fields[70] = new IFE_NUMERIC (3,  "NETWORK MANAGEMENT CODE");

        return fields;
    }
}
