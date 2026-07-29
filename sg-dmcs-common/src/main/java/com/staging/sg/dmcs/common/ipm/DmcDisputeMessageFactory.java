package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import java.util.Map;

/**
 * Construit les messages sortants du premier cycle de litige DMC.
 *
 * <p>Aucune référence n'est synthétisée. DE31, DE95 et les PDS associés aux
 * montants originaux doivent provenir du cycle réel ou être fournis par le
 * système propriétaire habilité.</p>
 */
public final class DmcDisputeMessageFactory {
    private final ISOPackager packager;

    public DmcDisputeMessageFactory(ISOPackager packager) {
        this.packager = packager;
    }

    public ISOMsg firstChargeback(DisputeData data, int messageNumber)
            throws ISOException {
        requireFunction(data, "1442", "450", "453");
        validate(data, messageNumber);
        return build("1442", data, messageNumber);
    }

    public ISOMsg secondPresentment(DisputeData data, int messageNumber)
            throws ISOException {
        requireFunction(data, "1240", "205", "282");
        validate(data, messageNumber);
        return build("1240", data, messageNumber);
    }

    private ISOMsg build(String mti, DisputeData data, int messageNumber)
            throws ISOException {
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(mti);
        message.set(2, data.pan());
        message.set(3, data.processingCode());
        message.set(4, amount(data.amount()));
        message.set(12, data.transactionDatetime());
        setIfPresent(message, 14, data.expiry());
        message.set(22, data.posDataCode());
        message.set(24, data.functionCode());
        message.set(25, data.messageReasonCode());
        message.set(26, data.mcc());
        message.set(30, data.originalAmounts());
        message.set(31, data.acquirerReference());
        setIfPresent(message, 32, data.acquiringInstitutionId());
        message.set(33, data.forwardingInstitutionId());
        setIfPresent(message, 37, data.rrn());
        setIfPresent(message, 38, data.authorizationCode());
        setIfPresent(message, 41, data.terminalId());
        setIfPresent(message, 42, data.acceptorId());
        message.set(43, data.acceptorNameLocation());
        message.set(48, data.pdsData());
        message.set(49, data.currency());
        message.set(71, "%08d".formatted(messageNumber));
        setIfPresent(message, 93, data.destinationId());
        message.set(94, data.originId());
        message.set(95, data.issuerReference());
        return message;
    }

    private static void validate(DisputeData data, int messageNumber) {
        if (data == null) {
            throw new IllegalArgumentException("Données de litige obligatoires");
        }
        if (messageNumber < 1 || messageNumber > 99_999_999) {
            throw new IllegalArgumentException("DE71 hors plage");
        }
        requireDigits(data.pan(), 12, 19, "DE2");
        requireDigits(data.processingCode(), 6, 6, "DE3");
        if (data.amount() < 0 || data.amount() > 999_999_999_999L) {
            throw new IllegalArgumentException("DE4 hors plage");
        }
        requireDigits(data.transactionDatetime(), 12, 12, "DE12");
        optionalDigits(data.expiry(), 4, 4, "DE14");
        requireLength(data.posDataCode(), 12, "DE22");
        requireDigits(data.messageReasonCode(), 4, 4, "DE25");
        requireDigits(data.mcc(), 4, 4, "DE26");
        requireDigits(data.originalAmounts(), 24, 24, "DE30");
        requireDigits(data.acquirerReference(), 23, 23, "DE31 réel");
        optionalDigits(data.acquiringInstitutionId(), 1, 11, "DE32");
        requireDigits(data.forwardingInstitutionId(), 1, 11, "DE33");
        requireLength(data.acceptorNameLocation(), 1, 99, "DE43");
        requireLength(data.currency(), 3, "DE49");
        optionalDigits(data.destinationId(), 1, 11, "DE93");
        requireDigits(data.originId(), 1, 11, "DE94");
        requireLength(data.issuerReference(), 42, "DE95 réel");
        requirePdsForOriginalAmounts(data.pdsData());

        long originalTransactionAmount =
                Long.parseLong(data.originalAmounts().substring(0, 12));
        if (data.amount() > originalTransactionAmount) {
            throw new IllegalArgumentException(
                    "DE4 litige ne peut pas dépasser DE30 sous-champ 1");
        }
        boolean full = "450".equals(data.functionCode())
                || "205".equals(data.functionCode());
        boolean partial = "453".equals(data.functionCode())
                || "282".equals(data.functionCode());
        if (full && data.amount() != originalTransactionAmount) {
            throw new IllegalArgumentException(
                    "Le cycle Full exige DE4 égal au montant original");
        }
        if (partial && data.amount() >= originalTransactionAmount) {
            throw new IllegalArgumentException(
                    "Le cycle Partial exige DE4 inférieur au montant original");
        }
    }

    private static void requirePdsForOriginalAmounts(String pdsData) {
        if (pdsData == null || pdsData.isBlank()) {
            throw new IllegalArgumentException("DE48/PDS obligatoire");
        }
        Map<Integer, String> pds = DmcPdsCodec.decode(pdsData);
        if (!pds.containsKey(148) || !pds.containsKey(149)) {
            throw new IllegalArgumentException(
                    "PDS 0148 et 0149 obligatoires avec DE30");
        }
    }

    private static void requireFunction(
            DisputeData data, String mti, String... allowed) {
        if (data == null) {
            throw new IllegalArgumentException("Données " + mti + " obligatoires");
        }
        for (String value : allowed) {
            if (value.equals(data.functionCode())) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "DE24 invalide pour " + mti + ": " + data.functionCode());
    }

    private static void setIfPresent(ISOMsg message, int field, String value) {
        if (value != null && !value.isBlank()) {
            message.set(field, value);
        }
    }

    private static String amount(long value) {
        return "%012d".formatted(value);
    }

    private static void requireDigits(
            String value, int min, int max, String label) {
        if (value == null || value.length() < min || value.length() > max
                || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    label + " doit être numérique sur " + min + ".." + max
                            + " positions");
        }
    }

    private static void optionalDigits(
            String value, int min, int max, String label) {
        if (value != null && !value.isBlank()) {
            requireDigits(value, min, max, label);
        }
    }

    private static void requireLength(String value, int length, String label) {
        requireLength(value, length, length, label);
    }

    private static void requireLength(
            String value, int min, int max, String label) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new IllegalArgumentException(
                    label + " doit contenir " + min + ".." + max + " positions");
        }
    }

    public record DisputeData(
            String functionCode,
            String pan,
            String processingCode,
            long amount,
            String transactionDatetime,
            String expiry,
            String posDataCode,
            String messageReasonCode,
            String mcc,
            String originalAmounts,
            String acquirerReference,
            String acquiringInstitutionId,
            String forwardingInstitutionId,
            String rrn,
            String authorizationCode,
            String terminalId,
            String acceptorId,
            String acceptorNameLocation,
            String currency,
            String destinationId,
            String originId,
            String issuerReference,
            String pdsData) {
    }
}
