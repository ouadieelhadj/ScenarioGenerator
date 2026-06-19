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
        // DE7 : MMDDhhmmss (transmission date & time)
        fields[7]  = new IFE_NUMERIC (10, "TRANSMISSION DATE AND TIME");
        // DE11 : STAN
        fields[11] = new IFE_NUMERIC (6,  "SYSTEM TRACE AUDIT NUMBER");
        // DE39 : response code (présent sur 0810)
        fields[39] = new IFE_CHAR    (2,  "RESPONSE CODE");
        // DE70 : network management code (001 sign-on, 002 sign-off, 270 echo)
        fields[70] = new IFE_NUMERIC (3,  "NETWORK MANAGEMENT CODE");

        return fields;
    }
}
