package com.staging.sg.common.service;

import com.staging.sg.common.entity.AbstractMcDmasAuthorizationTransaction;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Copie une autorisation ISO 8583 DMAS dans un journal exploitable par DMC.
 *
 * <p>Le PIN block (DE52) n'est volontairement jamais journalise. Les donnees
 * ICC (DE55) sont conservees en hexadecimal pour ne pas alterer les octets
 * binaires.</p>
 */
public final class McDmasAuthorizationJournalMapper {

    private static final Set<String> CLEARING_TRANSACTION_TYPES =
            Set.of("00", "01", "09", "17", "18", "20", "21", "22", "28", "40");

    private McDmasAuthorizationJournalMapper() {
    }

    public static <T extends AbstractMcDmasAuthorizationTransaction> T populate(
            T target,
            ISOMsg request,
            ISOMsg response,
            String bankCode,
            String interfaceId,
            LocalDateTime requestAt,
            LocalDateTime responseAt) throws ISOException {

        target.setInterfaceId(trimToNull(interfaceId));
        target.setBankCode(required(bankCode, "bankCode"));
        target.setMtiRequest(required(request.getMTI(), "MTI requete"));
        target.setMtiResponse(response == null ? null : response.getMTI());
        target.setPan(required(value(request, 2), "DE2 PAN"));
        target.setMaskedPan(maskPan(value(request, 2)));
        target.setProcessingCode(value(request, 3));
        target.setAmount(parseAmount(value(request, 4)));
        target.setTransmissionDatetime(required(value(request, 7), "DE7"));
        target.setStan(required(value(request, 11), "DE11"));
        target.setLocalTime(value(request, 12));
        target.setLocalDate(value(request, 13));
        target.setExpiry(value(request, 14));
        target.setMcc(value(request, 18));
        target.setPosEntryMode(value(request, 22));
        target.setCardSequence(value(request, 23));
        target.setAcquiringInstitutionId(value(request, 32));
        target.setForwardingInstitutionId(value(request, 33));
        target.setRrn(value(request, 37));
        target.setAuthorizationCode(response == null ? null : value(response, 38));
        target.setResponseCode(response == null ? null : value(response, 39));
        target.setTerminalId(value(request, 41));
        target.setAcceptorId(value(request, 42));
        target.setAcceptorNameLocation(value(request, 43));
        target.setAdditionalData(value(request, 48));
        target.setCurrency(value(request, 49));
        target.setIccDataHex(hexValue(request, 55));
        target.setPosData(value(request, 61));

        boolean approved = response != null && "00".equals(value(response, 39));
        target.setApproved(approved);
        target.setClearingEligible(approved && isFinancial(request) && !isPreauthorization(request));
        target.setRequestAt(requestAt == null ? LocalDateTime.now() : requestAt);
        target.setResponseAt(responseAt);
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(LocalDateTime.now());
        }
        target.setUpdatedAt(LocalDateTime.now());
        return target;
    }

    public static boolean isFinancial(ISOMsg request) throws ISOException {
        String mti = request.getMTI();
        if (!"0100".equals(mti) && !"0120".equals(mti)
                && !"0200".equals(mti) && !"0220".equals(mti)) {
            return false;
        }
        String processingCode = value(request, 3);
        return processingCode != null
                && processingCode.length() >= 2
                && CLEARING_TRANSACTION_TYPES.contains(processingCode.substring(0, 2));
    }

    private static boolean isPreauthorization(ISOMsg request) {
        String mti;
        try {
            mti = request.getMTI();
        } catch (ISOException e) {
            throw new IllegalArgumentException("MTI DMAS illisible", e);
        }
        String posData = value(request, 61);
        return ("0100".equals(mti) || "0200".equals(mti))
                && posData != null
                && posData.length() >= 7
                && posData.charAt(6) == '4';
    }

    /**
     * Parse le DE90 DMAS sans accepter de valeur tronquée.
     *
     * <p>Structure n-42 : MTI(4), STAN(6), DE7(10), DE32(11), DE33(11).</p>
     */
    public static OriginalDataElements parseOriginalDataElements(String de90) {
        String value = trimToNull(de90);
        if (value == null || !value.matches("\\d{42}")) {
            throw new IllegalArgumentException("DE90 DMAS doit contenir exactement 42 chiffres");
        }
        return new OriginalDataElements(
                value.substring(0, 4),
                value.substring(4, 10),
                value.substring(10, 20),
                value.substring(20, 31),
                value.substring(31, 42));
    }

    public record OriginalDataElements(
            String mti,
            String stan,
            String transmissionDatetime,
            String acquiringInstitutionId,
            String forwardingInstitutionId) {
    }

    private static String hexValue(ISOMsg message, int field) {
        if (message == null || !message.hasField(field)) {
            return null;
        }
        try {
            byte[] bytes = message.getBytes(field);
            return bytes == null ? null : ISOUtil.hexString(bytes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String value(ISOMsg message, int field) {
        if (message == null || !message.hasField(field)) {
            return null;
        }
        return trimToNull(message.getString(field));
    }

    private static Long parseAmount(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Montant ISO invalide: " + value, e);
        }
    }

    private static String required(String value, String label) {
        String result = trimToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(label + " obligatoire pour le journal DMAS");
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return pan;
        }
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10)
                + pan.substring(pan.length() - 4);
    }
}
