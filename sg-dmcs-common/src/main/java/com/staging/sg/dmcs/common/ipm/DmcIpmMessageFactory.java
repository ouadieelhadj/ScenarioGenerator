package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit le squelette normatif d'un fichier DMC IPM.
 *
 * <p>Le fichier contient un File Header/1644-697, des First
 * Presentment/1240-200 et un File Trailer/1644-695. Le DE71 commence a
 * 00000001 et est strictement sequentiel. Le trailer contient le File ID,
 * le checksum des DE4 et le nombre total de messages.</p>
 */
public final class DmcIpmMessageFactory {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final ISOPackager packager;

    public DmcIpmMessageFactory(ISOPackager packager) {
        this.packager = packager;
    }

    public BuiltFile build(FileParameters parameters, List<PresentmentData> presentments)
            throws ISOException {
        validateFileParameters(parameters);
        List<PresentmentData> transactions =
                presentments == null ? List.of() : List.copyOf(presentments);
        String fileId = buildFileId(parameters);
        List<ISOMsg> messages = new ArrayList<>(transactions.size() + 2);
        int sequence = 1;
        messages.add(header(parameters, fileId, sequence++));

        long amountChecksum = 0;
        for (PresentmentData data : transactions) {
            validatePresentment(data);
            messages.add(firstPresentment(data, sequence++));
            amountChecksum = Math.addExact(amountChecksum, data.amount());
        }
        messages.add(trailer(parameters, fileId, sequence, amountChecksum, transactions.size() + 2));
        return new BuiltFile(fileId, amountChecksum, List.copyOf(messages));
    }

    /**
     * Construit un fichier IPM complet contenant un seul type de cycle de
     * litige. Le header, le trailer, DE71 et les totaux sont produits par le
     * même chemin que les First Presentments.
     */
    public BuiltFile buildDisputes(
            FileParameters parameters,
            List<DmcDisputeMessageFactory.DisputeData> disputes)
            throws ISOException {
        validateFileParameters(parameters);
        List<DmcDisputeMessageFactory.DisputeData> transactions =
                disputes == null ? List.of() : List.copyOf(disputes);
        String fileId = buildFileId(parameters);
        List<ISOMsg> messages = new ArrayList<>(transactions.size() + 2);
        DmcDisputeMessageFactory disputeFactory =
                new DmcDisputeMessageFactory(packager);
        int sequence = 1;
        messages.add(header(parameters, fileId, sequence++));

        long amountChecksum = 0;
        for (DmcDisputeMessageFactory.DisputeData data : transactions) {
            ISOMsg message;
            if ("450".equals(data.functionCode())
                    || "453".equals(data.functionCode())) {
                message = disputeFactory.firstChargeback(data, sequence++);
            } else if ("205".equals(data.functionCode())
                    || "282".equals(data.functionCode())) {
                message = disputeFactory.secondPresentment(data, sequence++);
            } else {
                throw new IllegalArgumentException(
                        "DE24 non supporté dans un fichier de litige: "
                                + data.functionCode());
            }
            messages.add(message);
            amountChecksum = Math.addExact(amountChecksum, data.amount());
        }
        messages.add(trailer(
                parameters, fileId, sequence, amountChecksum,
                transactions.size() + 2));
        return new BuiltFile(fileId, amountChecksum, List.copyOf(messages));
    }

    private ISOMsg header(FileParameters parameters, String fileId, int sequence)
            throws ISOException {
        ISOMsg message = message("1644");
        message.set(24, "697");
        message.set(71, sequence(sequence));
        setIfPresent(message, 93, parameters.destinationId());
        setIfPresent(message, 94, parameters.originId());
        message.set(48, DmcPdsCodec.concat(
                DmcPdsCodec.encode(105, fileId),
                DmcPdsCodec.encode(122, parameters.processingMode())));
        return message;
    }

    private ISOMsg firstPresentment(PresentmentData data, int sequence)
            throws ISOException {
        ISOMsg message = message("1240");
        message.set(2, data.pan());
        message.set(3, data.processingCode());
        message.set(4, amount(data.amount()));
        message.set(12, data.transactionDatetime());
        setIfPresent(message, 14, data.expiry());
        message.set(22, data.posDataCode());
        message.set(24, "200");
        message.set(26, data.mcc());
        message.set(31, data.acquirerReference());
        setIfPresent(message, 32, data.acquiringInstitutionId());
        setIfPresent(message, 33, data.forwardingInstitutionId());
        setIfPresent(message, 37, data.rrn());
        setIfPresent(message, 38, data.authorizationCode());
        setIfPresent(message, 41, data.terminalId());
        setIfPresent(message, 42, data.acceptorId());
        setIfPresent(message, 43, data.acceptorNameLocation());
        message.set(49, data.currency());
        message.set(71, sequence(sequence));
        setIfPresent(message, 93, data.destinationId());
        setIfPresent(message, 94, data.originId());
        setIfPresent(message, 48, data.pdsData());
        return message;
    }

    private ISOMsg trailer(
            FileParameters parameters,
            String fileId,
            int sequence,
            long amountChecksum,
            int messageCount) throws ISOException {
        ISOMsg message = message("1644");
        message.set(24, "695");
        message.set(71, sequence(sequence));
        setIfPresent(message, 93, parameters.destinationId());
        setIfPresent(message, 94, parameters.originId());
        message.set(48, DmcPdsCodec.concat(
                DmcPdsCodec.encode(105, fileId),
                DmcPdsCodec.encode(301, "%016d".formatted(amountChecksum)),
                DmcPdsCodec.encode(306, "%08d".formatted(messageCount))));
        return message;
    }

    private ISOMsg message(String mti) throws ISOException {
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(mti);
        return message;
    }

    private static String buildFileId(FileParameters parameters) {
        return parameters.fileType()
                + parameters.businessDate().format(FILE_DATE)
                + leftPadNumeric(parameters.processorId(), 11, "processorId")
                + "%05d".formatted(parameters.fileSequence());
    }

    private static void validateFileParameters(FileParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Parametres fichier obligatoires");
        }
        requireDigits(parameters.fileType(), 3, "PDS 0105 fileType");
        if (parameters.businessDate() == null) {
            throw new IllegalArgumentException("businessDate obligatoire");
        }
        requireDigits(parameters.processorId(), 1, 11, "PDS 0105 processorId");
        if (parameters.fileSequence() < 1 || parameters.fileSequence() > 99_999) {
            throw new IllegalArgumentException("PDS 0105 fileSequence hors plage");
        }
        if (!"T".equals(parameters.processingMode())
                && !"P".equals(parameters.processingMode())) {
            throw new IllegalArgumentException("PDS 0122 doit valoir T ou P");
        }
        optionalDigits(parameters.destinationId(), 1, 11, "DE93");
        optionalDigits(parameters.originId(), 1, 11, "DE94");
    }

    private static void validatePresentment(PresentmentData data) {
        if (data == null) {
            throw new IllegalArgumentException("Presentment null");
        }
        requireDigits(data.pan(), 12, 19, "DE2");
        requireDigits(data.processingCode(), 6, "DE3");
        if (data.amount() < 0 || data.amount() > 999_999_999_999L) {
            throw new IllegalArgumentException("DE4 hors plage");
        }
        requireDigits(data.transactionDatetime(), 12, "DE12");
        if (data.expiry() != null) requireDigits(data.expiry(), 4, "DE14");
        requireLength(data.posDataCode(), 12, "DE22");
        requireDigits(data.mcc(), 4, "DE26");
        // DE31 est obligatoire en First Presentment. Sa regle ARN detaillee
        // est volontairement reportee, mais aucune valeur fictive n'est creee.
        requireDigits(data.acquirerReference(), 23, "DE31");
        requireLength(data.currency(), 3, "DE49");
        optionalDigits(data.acquiringInstitutionId(), 1, 11, "DE32");
        optionalDigits(data.forwardingInstitutionId(), 1, 11, "DE33");
        optionalDigits(data.destinationId(), 1, 11, "DE93");
        optionalDigits(data.originId(), 1, 11, "DE94");
    }

    private static void setIfPresent(ISOMsg message, int field, String value) {
        if (value != null && !value.isBlank()) {
            message.set(field, value);
        }
    }

    private static String amount(long value) {
        return "%012d".formatted(value);
    }

    private static String sequence(int value) {
        return "%08d".formatted(value);
    }

    private static String leftPadNumeric(String value, int length, String label) {
        requireDigits(value, 1, length, label);
        return "0".repeat(length - value.length()) + value;
    }

    private static void requireLength(String value, int length, String label) {
        if (value == null || value.length() != length) {
            throw new IllegalArgumentException(label + " doit contenir " + length + " positions");
        }
    }

    private static void requireDigits(String value, int length, String label) {
        requireDigits(value, length, length, label);
    }

    private static void requireDigits(String value, int min, int max, String label) {
        if (value == null || value.length() < min || value.length() > max
                || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    label + " doit etre numerique sur " + min + ".." + max + " positions");
        }
    }

    private static void optionalDigits(String value, int min, int max, String label) {
        if (value != null && !value.isBlank()) {
            requireDigits(value, min, max, label);
        }
    }

    public record FileParameters(
            String fileType,
            LocalDate businessDate,
            String processorId,
            int fileSequence,
            String processingMode,
            String destinationId,
            String originId) {
    }

    public record PresentmentData(
            String pan,
            String processingCode,
            long amount,
            String transactionDatetime,
            String expiry,
            String posDataCode,
            String mcc,
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
            String pdsData) {
    }

    public record BuiltFile(String fileId, long amountChecksum, List<ISOMsg> messages) {
    }
}
