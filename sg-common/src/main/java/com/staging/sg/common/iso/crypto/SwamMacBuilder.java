package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Construit la donnee a MACer pour SWAM (HPS PowerCARD), conforme au vrai membre Way4.
 *
 * REGLE REELLE (verifiee octet par octet sur les logs Way4 "Prepared MAC Data") :
 *
 *   [ MTI (4o ASCII) ] [ bitmap primaire+secondaire (16o binaire) ] [ DEs sauf DE128 ]
 *
 *   POINT CRITIQUE : le BIT 128 reste ALLUME dans le bitmap (et donc le bit 1,
 *   qui signale la presence du bitmap secondaire), mais la VALEUR du champ DE128
 *   est ABSENTE du corps.
 *
 *   Exemple Way4 (key push 1804/811) :
 *     bitmap = 82300180880100000000000000000001
 *              ^^ bit1 ON (bitmap secondaire)        ^^ bit128 ON
 *     corps  = 2607141111...P16033X9C0EE219...   (pas de MAC a la fin)
 *
 * PIEGE EVITE : un simple msg.unset(128) eteint AUSSI le bit 128 du bitmap,
 * jPOS n'emet alors plus le bitmap secondaire -> bitmap de 8 octets au lieu de 16,
 * et premier octet 0x02 au lieu de 0x82. On perdait 8 octets et le MAC etait faux.
 *
 * SOLUTION : on packe le message AVEC un DE128 factice (4 octets a zero), ce qui
 * garantit le bon bitmap, puis on retire les 8 derniers octets ASCII (la valeur
 * hex du DE128 factice, IFA_BINARY(4) = 8 caracteres sur le fil).
 *
 * Le header PowerCARD (11 octets) reste exclu d'office : il est gere par
 * SwamLengthChannel et n'entre pas dans le pack jPOS.
 */
public final class SwamMacBuilder {

    private static final Logger log = LoggerFactory.getLogger(SwamMacBuilder.class);

    /** Taille du DE128 sur le fil : IFA_BINARY(4) => 8 caracteres ASCII hex. */
    private static final int DE128_WIRE_LEN = 8;

    private SwamMacBuilder() {}

    /**
     * @param msg le message ISO (SwamPackager deja positionne)
     * @return les octets a MACer : MTI + bitmap (bit 128 ON) + DEs, sans la valeur du MAC
     */
    public static byte[] build(ISOMsg msg) throws Exception {
        // 1. Copie defensive : on ne touche jamais au message d'origine.
        ISOMsg copy = (ISOMsg) msg.clone();
        copy.setPackager(msg.getPackager());

        // 2. Forcer un DE128 factice a ZERO : le bit 128 reste allume dans le bitmap,
        //    donc le bitmap secondaire est emis (16 octets, premier octet 0x8x).
        copy.set(128, new byte[]{0, 0, 0, 0});

        // 3. Packer : MTI + bitmap(16) + DEs + DE128 factice (8 car. ASCII "00000000")
        byte[] packed = copy.pack();

        if (packed.length <= DE128_WIRE_LEN) {
            log.warn("[SWAM-MAC] pack trop court ({} octets) -> input MAC vide", packed.length);
            return new byte[0];
        }

        // 4. Retirer les 8 derniers octets (la valeur du DE128 factice).
        //    Le BITMAP, lui, garde le bit 128 allume. C'est exactement ce que MACe Way4.
        byte[] input = new byte[packed.length - DE128_WIRE_LEN];
        System.arraycopy(packed, 0, input, 0, input.length);

        log.info("[SWAM-MAC] MAC input : packedLen={} inputLen={} inputHex={}",
                packed.length, input.length, ISOUtil.hexString(input));
        return input;
    }
}
