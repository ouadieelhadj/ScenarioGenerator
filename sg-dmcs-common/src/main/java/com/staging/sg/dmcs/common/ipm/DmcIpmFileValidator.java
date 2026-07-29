package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.util.List;
import java.util.Map;

/**
 * Controles d'integrite de l'enveloppe logique IPM avant toute integration.
 */
public final class DmcIpmFileValidator {

    private DmcIpmFileValidator() {
    }

    public static ValidationResult validate(List<ISOMsg> messages) throws ISOException {
        if (messages == null || messages.size() < 2) {
            throw new IllegalArgumentException("Fichier IPM incomplet: header et trailer requis");
        }
        ISOMsg header = messages.get(0);
        ISOMsg trailer = messages.get(messages.size() - 1);
        requireMessage(header, "1644", "697", "File Header");
        requireMessage(trailer, "1644", "695", "File Trailer");

        for (int i = 0; i < messages.size(); i++) {
            String expected = "%08d".formatted(i + 1);
            if (!expected.equals(messages.get(i).getString(71))) {
                throw new IllegalArgumentException(
                        "DE71 non sequentiel au message " + (i + 1)
                                + ": attendu=" + expected
                                + " recu=" + messages.get(i).getString(71));
            }
        }

        Map<Integer, String> headerPds = DmcPdsCodec.decode(header.getString(48));
        Map<Integer, String> trailerPds = DmcPdsCodec.decode(trailer.getString(48));
        String fileId = requiredPds(headerPds, 105, "header");
        if (!fileId.equals(requiredPds(trailerPds, 105, "trailer"))) {
            throw new IllegalArgumentException("PDS 0105 different entre header et trailer");
        }
        String processingMode = requiredPds(headerPds, 122, "header");
        if (!"T".equals(processingMode) && !"P".equals(processingMode)) {
            throw new IllegalArgumentException("PDS 0122 invalide: " + processingMode);
        }

        long amountChecksum = 0;
        for (ISOMsg message : messages) {
            if (message.hasField(4)) {
                amountChecksum = Math.addExact(
                        amountChecksum, Long.parseLong(message.getString(4)));
            }
        }

        String trailerAmount = requiredPds(trailerPds, 301, "trailer");
        requireDigits(trailerAmount, 16, "PDS 0301");
        if (!isAllZeros(trailerAmount)
                && amountChecksum != Long.parseLong(trailerAmount)) {
            throw new IllegalArgumentException(
                    "PDS 0301 invalide: calcule=" + amountChecksum
                            + " recu=" + trailerAmount);
        }

        String trailerCount = requiredPds(trailerPds, 306, "trailer");
        requireDigits(trailerCount, 8, "PDS 0306");
        if (!isAllZeros(trailerCount)
                && messages.size() != Integer.parseInt(trailerCount)) {
            throw new IllegalArgumentException(
                    "PDS 0306 invalide: calcule=" + messages.size()
                            + " recu=" + trailerCount);
        }
        return new ValidationResult(fileId, processingMode, messages.size(), amountChecksum);
    }

    private static void requireMessage(
            ISOMsg message, String mti, String functionCode, String label)
            throws ISOException {
        if (!mti.equals(message.getMTI())
                || !functionCode.equals(message.getString(24))) {
            throw new IllegalArgumentException(label + " attendu "
                    + mti + "/" + functionCode);
        }
    }

    private static String requiredPds(Map<Integer, String> pds, int tag, String location) {
        String value = pds.get(tag);
        if (value == null) {
            throw new IllegalArgumentException(
                    "PDS %04d obligatoire dans le %s".formatted(tag, location));
        }
        return value;
    }

    private static void requireDigits(String value, int length, String label) {
        if (value.length() != length || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    label + " doit etre numerique sur " + length + " positions");
        }
    }

    private static boolean isAllZeros(String value) {
        return value.chars().allMatch(c -> c == '0');
    }

    public record ValidationResult(
            String fileId, String processingMode, int messageCount, long amountChecksum) {
    }
}
