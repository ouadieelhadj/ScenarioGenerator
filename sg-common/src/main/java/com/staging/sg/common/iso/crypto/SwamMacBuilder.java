package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Construit la donnee a MACer pour SWAM (HPS PowerCARD), conforme au vrai membre Way4.
 *
 * Regle (validee section 20.7 du SESSION_RESUME, exemple HSM Thales) :
 *   La donnee MACee = le message PACKE, PRIVE de :
 *      - le MTI              (4 octets ASCII)
 *      - le bitmap primaire  (16 octets binaires, IFB_BITMAP)
 *      - le champ DE128 lui-meme (le MAC ne se MAC pas lui-meme)
 *   Ce sont donc tous les DEs concatenes AVEC leurs prefixes de longueur
 *   (ex : DE33 LLVAR -> "06" + valeur ; DE48 LLLVAR -> "039" + valeur), en ASCII.
 *
 * IMPORTANT : on packe une COPIE du message d'ou DE128 a ete retire, puis on
 * enleve les 20 premiers octets (4 MTI + 16 bitmap). On NE reconstruit PAS la
 * concatenation "a la main" : on laisse le packager produire exactement les
 * memes octets que ceux transmis sur le fil (prefixes de longueur compris).
 *
 * Le header PowerCARD (11 octets 'ISO' + entete) N'EST PAS dans le pack jPOS :
 * il est gere par SwamLengthChannel, donc il est deja EXCLU d'office. OK.
 */
public final class SwamMacBuilder {

    private static final Logger log = LoggerFactory.getLogger(SwamMacBuilder.class);

    /** Longueur du MTI packe en ASCII. */
    private static final int MTI_LEN = 4;
    /** Longueur du bitmap primaire binaire (IFB_BITMAP(16)). */
    private static final int BITMAP_LEN = 16;

    private SwamMacBuilder() {}

    /**
     * @param msg le message ISO (packager SwamPackager deja positionne)
     * @return les octets a MACer (message packe sans MTI, sans bitmap, sans DE128)
     * @throws Exception si le clonage ou le packing echoue
     */
    public static byte[] build(ISOMsg msg) throws Exception {
        // 1. Copie defensive : on ne modifie jamais le message d'origine.
        ISOMsg copy = (ISOMsg) msg.clone();
        copy.setPackager(msg.getPackager());

        // 2. Retirer DE128 (le MAC lui-meme) s'il est present.
        if (copy.hasField(128)) {
            copy.unset(128);
        }

        // 3. Packer : produit MTI(4) + bitmap(16) + DEs (avec prefixes de longueur).
        byte[] packed = copy.pack();

        int skip = MTI_LEN + BITMAP_LEN; // 20
        if (packed.length <= skip) {
            log.warn("[SWAM-MAC] pack trop court ({} octets) -> input MAC vide", packed.length);
            return new byte[0];
        }

        byte[] input = new byte[packed.length - skip];
        System.arraycopy(packed, skip, input, 0, input.length);

        log.info("[SWAM-MAC] MAC input : packedLen={} skip={} inputLen={} inputHex={}",
                packed.length, skip, input.length, ISOUtil.hexString(input));
        return input;
    }
}
