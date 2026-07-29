package com.staging.sg.common.service;

import com.staging.sg.common.entity.AbstractDmcClearingTransaction;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

public final class DmcIncomingMessageMapper {
    private DmcIncomingMessageMapper() {
    }

    public static boolean isSupportedLifecycle(ISOMsg message) throws ISOException {
        return lifecycleStage(message) != null;
    }

    public static <T extends AbstractDmcClearingTransaction> T populate(
            T target, ISOMsg message, LocalDate businessDate,
            Long sourceFileId, int sourceMessageNumber) throws ISOException {
        String stage = lifecycleStage(message);
        if (stage == null) {
            throw new IllegalArgumentException("Message DMC non transactionnel: "
                    + message.getMTI() + "/" + value(message, 24));
        }
        target.setBusinessDate(businessDate);
        target.setSourceType("INCOMING_IPM");
        target.setDirection("IN");
        target.setSourceFileId(sourceFileId);
        target.setSourceMessageNumber(sourceMessageNumber);
        target.setCorrelationKey(correlationKey(message));
        target.setLifecycleStage(stage);
        target.setStatus("RECEIVED");
        target.setMatchStatus("UNMATCHED");
        target.setMti(message.getMTI());
        target.setFunctionCode(value(message, 24));
        target.setPan(required(value(message, 2), "DE2"));
        target.setMaskedPan(maskPan(value(message, 2)));
        target.setProcessingCode(value(message, 3));
        target.setAmount(parseLong(value(message, 4)));
        target.setReconciliationAmount(parseLong(value(message, 5)));
        target.setReconciliationRate(value(message, 9));
        target.setTransactionDatetime(value(message, 12));
        target.setExpiry(value(message, 14));
        target.setPosDataCode(value(message, 22));
        target.setMessageReasonCode(value(message, 25));
        target.setMcc(value(message, 26));
        target.setOriginalAmounts(value(message, 30));
        target.setAcquirerReference(value(message, 31));
        target.setAcquiringInstitutionId(value(message, 32));
        target.setForwardingInstitutionId(value(message, 33));
        target.setRrn(value(message, 37));
        target.setAuthorizationCode(value(message, 38));
        target.setTerminalId(value(message, 41));
        target.setAcceptorId(value(message, 42));
        target.setAcceptorNameLocation(value(message, 43));
        target.setCurrency(value(message, 49));
        target.setReconciliationCurrency(value(message, 50));
        target.setMessageNumber(value(message, 71));
        target.setDestinationId(value(message, 93));
        target.setOriginId(value(message, 94));
        target.setIssuerReference(value(message, 95));
        target.setPdsData(value(message, 48));
        return target;
    }

    private static String lifecycleStage(ISOMsg message) throws ISOException {
        String key = message.getMTI() + "/" + value(message, 24);
        return switch (key) {
            case "1240/200" -> "FIRST_PRESENTMENT";
            case "1240/205", "1240/282" -> "SECOND_PRESENTMENT";
            case "1442/450", "1442/453" -> "CHARGEBACK";
            default -> null;
        };
    }

    private static String correlationKey(ISOMsg message) throws ISOException {
        String de31 = value(message, 31);
        if (de31 != null) {
            return "DE31:" + de31;
        }
        String source = String.join("|",
                safe(value(message, 2)), safe(value(message, 37)),
                safe(value(message, 4)), safe(value(message, 49)),
                safe(value(message, 38)));
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String value(ISOMsg message, int field) {
        if (!message.hasField(field)) return null;
        String value = message.getString(field);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long parseLong(String value) {
        return value == null ? null : Long.parseLong(value);
    }

    private static String required(String value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " obligatoire");
        return value;
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10)
                + pan.substring(pan.length() - 4);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
