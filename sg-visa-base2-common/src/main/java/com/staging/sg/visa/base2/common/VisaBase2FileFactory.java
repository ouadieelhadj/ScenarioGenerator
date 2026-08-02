package com.staging.sg.visa.base2.common;

import java.time.LocalDate;
import java.util.List;

public final class VisaBase2FileFactory {

    public List<VisaBase2Record> purchaseCtf(VisaBase2PresentmentData data,
            String centerInformationBlock, LocalDate processingDate,
            int outgoingFileId, long batchNumber) {
        validate(data, centerInformationBlock, outgoingFileId, batchNumber);
        VisaBase2Record header = VisaBase2Record.create("90", 0, 0)
                .setAlpha(3, 8, centerInformationBlock)
                .setNumeric(9, 13, yyDdd(processingDate))
                .setAlpha(30, 33, "TEST")
                .setNumeric(77, 79, Integer.toString(outgoingFileId));

        String panMain = data.pan().substring(0, Math.min(16, data.pan().length()));
        String panExtension = data.pan().length() > 16 ? data.pan().substring(16) : "";
        VisaBase2Record tcr0 = VisaBase2Record.create("05", 0, 0)
                .setNumeric(5, 20, panMain)
                .setNumeric(21, 23, panExtension)
                .setNumeric(24, 46, data.arn())
                .setAlpha(47, 54, data.acquirerBusinessId())
                .setNumeric(55, 58, data.purchaseDateMmdd())
                .setNumeric(59, 70, Long.toString(data.destinationAmountMinor()))
                .setNumeric(71, 73, data.destinationCurrency())
                .setNumeric(74, 85, Long.toString(data.sourceAmountMinor()))
                .setNumeric(86, 88, data.sourceCurrency())
                .setAlpha(89, 113, data.merchantName())
                .setAlpha(114, 126, data.merchantCity())
                .setAlpha(127, 129, data.merchantCountry())
                .setNumeric(130, 133, data.mcc())
                .setAlpha(134, 138, data.merchantZip())
                .setAlpha(139, 141, data.merchantState())
                .setNumeric(147, 147, "1")
                .setNumeric(148, 149, "00")
                .setNumeric(150, 150, "9")
                .setAlpha(151, 151, data.aci())
                .setAlpha(152, 157, data.authorizationCode())
                .setAlpha(158, 158, "0")
                .setAlpha(162, 163, data.posEntryMode())
                .setNumeric(164, 167, yDdd(processingDate));

        VisaBase2Record tcr5 = VisaBase2Record.create("05", 0, 5)
                .setNumeric(5, 19, data.transactionIdentifier())
                .setNumeric(20, 31, Long.toString(data.authorizedAmountMinor()))
                .setAlpha(32, 34, data.authorizationCurrency())
                .setAlpha(35, 36, data.authorizationResponseCode())
                .setAlpha(37, 40, data.validationCode());

        VisaBase2Record batch = VisaBase2Record.create("91", 0, 0)
                .setNumeric(5, 10, centerInformationBlock)
                .setNumeric(11, 15, yyDdd(processingDate))
                .setNumeric(16, 30, Long.toString(data.destinationAmountMinor()))
                .setNumeric(31, 42, "1")
                .setNumeric(43, 48, Long.toString(batchNumber))
                .setNumeric(49, 60, "3")
                .setAlpha(67, 74, "SGVISA01")
                .setNumeric(75, 83, "2")
                .setNumeric(102, 116, Long.toString(data.sourceAmountMinor()));

        VisaBase2Record trailer = VisaBase2Record.create("92", 0, 0)
                .setNumeric(5, 10, centerInformationBlock)
                .setNumeric(11, 15, yyDdd(processingDate))
                .setNumeric(16, 30, Long.toString(data.destinationAmountMinor()))
                .setNumeric(31, 42, "1")
                .setNumeric(43, 48, "1")
                .setNumeric(49, 60, "4")
                .setNumeric(75, 83, "3")
                .setNumeric(102, 116, Long.toString(data.sourceAmountMinor()));
        return List.of(header, tcr0, tcr5, batch, trailer);
    }

    private static void validate(VisaBase2PresentmentData d, String cib, int fileId, long batch) {
        if (d == null || d.pan() == null || !d.pan().matches("\\d{12,19}")
                || d.arn() == null || !d.arn().matches("\\d{23}")
                || d.transactionIdentifier() == null || !d.transactionIdentifier().matches("\\d{15}")
                || d.validationCode() == null || !d.validationCode().matches("[A-Z0-9]{4}")
                || d.authorizationCode() == null || !d.authorizationCode().matches("[A-Z0-9]{6}")
                || cib == null || !cib.matches("\\d{6}") || fileId < 0 || fileId > 999
                || batch < 1 || batch > 999999) {
            throw new IllegalArgumentException("Incomplete or invalid Base II presentment data");
        }
    }

    private static String yyDdd(LocalDate date) {
        return "%02d%03d".formatted(Math.floorMod(date.getYear(), 100), date.getDayOfYear());
    }

    private static String yDdd(LocalDate date) {
        return "%d%03d".formatted(Math.floorMod(date.getYear(), 10), date.getDayOfYear());
    }
}
