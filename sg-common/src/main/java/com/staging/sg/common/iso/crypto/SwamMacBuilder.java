package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Construit le buffer de la commande Thales M6 observé en recette SWAM.
 *
 * <p>Le buffer contient uniquement les valeurs des DE présents, dans leur
 * ordre numérique. Le MTI, les bitmaps, le header PowerCARD et DE128 sont
 * exclus. Les préfixes ASCII LLVAR/LLLVAR sont conservés pour les messages
 * autres que le sign-on 1804/801, conformément aux vecteurs M6 validés.</p>
 */
public final class SwamMacBuilder {
    private static final Logger log =
            LoggerFactory.getLogger(SwamMacBuilder.class);

    private SwamMacBuilder() {
    }

    public static byte[] build(ISOMsg message) throws Exception {
        ISOMsg canonical = canonicalWireMessage(message);
        boolean withPrefixes = !isSignOn(canonical);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int field = 2; field <= 127; field++) {
            if (!canonical.hasField(field)) {
                continue;
            }
            String value = canonical.getString(field);
            if (value == null) {
                continue;
            }
            byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
            if (withPrefixes) {
                if (isLllvar(field)) {
                    output.write("%03d".formatted(bytes.length)
                            .getBytes(StandardCharsets.US_ASCII));
                } else if (isLlvar(field)) {
                    output.write("%02d".formatted(bytes.length)
                            .getBytes(StandardCharsets.US_ASCII));
                }
            }
            output.write(bytes);
        }
        byte[] input = output.toByteArray();
        log.info("[SWAM-MAC] M6 input : prefixes={} inputLen={} inputHex={}",
                withPrefixes, input.length, ISOUtil.hexString(input));
        return input;
    }

    /**
     * The sender calculates the MAC before jPOS packs the message, while the
     * receiver calculates it after unpacking. A wire round-trip canonicalizes
     * every fixed field (space padding for IF_CHAR, zero padding for numeric
     * fields) before both sides build the M6 input.
     */
    private static ISOMsg canonicalWireMessage(ISOMsg message) throws Exception {
        ISOMsg canonical = new ISOMsg();
        canonical.setPackager(message.getPackager());
        canonical.unpack(message.pack());
        return canonical;
    }

    private static boolean isSignOn(ISOMsg message) throws Exception {
        return "1804".equals(message.getMTI())
                && "801".equals(message.getString(24));
    }

    private static boolean isLlvar(int field) {
        return switch (field) {
            case 2, 32, 33, 35, 43, 45, 53, 56, 93, 94, 100, 101,
                    102, 103 -> true;
            default -> false;
        };
    }

    private static boolean isLllvar(int field) {
        return switch (field) {
            case 46, 48, 54, 55, 60, 61, 62, 123, 127 -> true;
            default -> false;
        };
    }
}
