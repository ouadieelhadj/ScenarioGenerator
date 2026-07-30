package com.staging.sg.common.iso;

import com.staging.sg.common.iso.WayPosOperationCatalog.Effect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.staging.sg.common.iso.WayPosOperationCatalog.Effect.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

class WayPosOperationCatalogTest {
    @Test
    void resolvesBasicAndExtendedEffects() {
        assertEquals(HOLD, WayPosOperationCatalog.resolve("0100", "000000").effect());
        assertEquals(DEBIT, WayPosOperationCatalog.resolve("0200", "000000").effect());
        assertEquals(CREDIT, WayPosOperationCatalog.resolve("0200", "200000").effect());
        assertEquals(REVERSAL, WayPosOperationCatalog.resolve("0420", "000000").effect());
        assertEquals(KEY_CHANGE, WayPosOperationCatalog.resolve("0800", "960000").effect());
        assertTrue(WayPosOperationCatalog.resolve("0200", "480000").extended());
        assertEquals(DEBIT,
                WayPosOperationCatalog.resolve("0220", "000000", "200").effect());
        assertEquals(CAPTURE,
                WayPosOperationCatalog.resolve("0220", "590000", "201").effect());
        assertEquals(CAPTURE,
                WayPosOperationCatalog.resolve("0220", "020000", "202").effect());
        var afd = WayPosOperationCatalog.resolve("0220", "000000", "102");
        assertEquals(CAPTURE, afd.effect());
        assertEquals("AFD_COMPLETION", afd.name());
        assertTrue(afd.extended());
        assertEquals("BATCH_UPLOAD_ADVICE",
                WayPosOperationCatalog.resolve("0320", "000000").name());
        assertTrue(!WayPosOperationCatalog.resolve(
                "0320", "000000").extended());
    }

    @ParameterizedTest
    @MethodSource("documentedOperations")
    void resolvesDocumentedBasicAndExtendedOperations(
            String mti, String processingCode, String networkId,
            String expectedName, Effect expectedEffect, boolean extended) {
        var operation = WayPosOperationCatalog.resolve(
                mti, processingCode, networkId);

        assertEquals(expectedName, operation.name());
        assertEquals(expectedEffect, operation.effect());
        assertEquals(extended, operation.extended());
    }

    private static Stream<Arguments> documentedOperations() {
        return Stream.of(
                Arguments.of("0100", "000000", null,
                        "AUTHORIZATION", HOLD, false),
                Arguments.of("0100", "160000", null,
                        "LOYALTY_PROGRAM_REQUEST", INQUIRY, true),
                Arguments.of("0100", "300000", null,
                        "BALANCE_INQUIRY", INQUIRY, false),
                Arguments.of("0100", "320000", null,
                        "MINI_STATEMENT", INQUIRY, false),
                Arguments.of("0100", "390000", null,
                        "CARD_VERIFICATION", INQUIRY, false),
                Arguments.of("0100", "500000", null,
                        "UTILITY_PAYMENT_AUTHORIZATION", HOLD, true),
                Arguments.of("0100", "510000", null,
                        "PREAUTHORIZATION_CASH", HOLD, false),
                Arguments.of("0100", "590000", null,
                        "BILL_PAYMENT_AUTHORIZATION", HOLD, true),
                Arguments.of("0100", "910000", null,
                        "CARD_CONTROL_REQUEST", CONTROL, true),
                Arguments.of("0100", "920000", null,
                        "PIN_MANAGEMENT", CONTROL, true),
                Arguments.of("0200", "000000", null,
                        "PURCHASE_OR_PAYMENT", DEBIT, false),
                Arguments.of("0200", "090000", null,
                        "PURCHASE_WITH_CASHBACK", DEBIT, false),
                Arguments.of("0200", "020000", null,
                        "TIP_COMPLETION", DEBIT, false),
                Arguments.of("0200", "200000", null,
                        "REFUND", CREDIT, false),
                Arguments.of("0200", "210000", null,
                        "CREDIT", CREDIT, true),
                Arguments.of("0200", "230000", null,
                        "MIR_EC_PURCHASE_RETURN", CREDIT, false),
                Arguments.of("0200", "240000", null,
                        "CREDIT_VOUCHER", CREDIT, true),
                Arguments.of("0200", "250000", null,
                        "PURCHASE_RETURN", CREDIT, false),
                Arguments.of("0200", "290000", null,
                        "CASH_TO_CARD", CREDIT, false),
                Arguments.of("0200", "480000", null,
                        "P2P_CARD_TO_CARD", DEBIT, true),
                Arguments.of("0200", "500000", null,
                        "UTILITY_PAYMENT", DEBIT, true),
                Arguments.of("0200", "520000", null,
                        "CASH_BY_CODE", DEBIT, false),
                Arguments.of("0200", "590000", null,
                        "BILL_PAYMENT_ADVICE", ADVICE, true),
                Arguments.of("0220", "000000", "102",
                        "AFD_COMPLETION", CAPTURE, true),
                Arguments.of("0220", "020000", "202",
                        "TIP_PURCHASE_COMPLETION", CAPTURE, false),
                Arguments.of("0220", "000000", "202",
                        "AUTHORIZATION_CONFIRMATION", CAPTURE, false),
                Arguments.of("0400", "000000", "400",
                        "UNIVERSAL_REVERSAL", REVERSAL, false),
                Arguments.of("0500", "920000", null,
                        "RECONCILIATION", RECONCILIATION, false),
                Arguments.of("0302", "", null,
                        "FILE_UPDATE", FILE, true),
                Arguments.of("0320", "000000", null,
                        "BATCH_UPLOAD_ADVICE", FILE, false),
                Arguments.of("0800", "930000", null,
                        "POS_INITIALIZATION", INITIALIZATION, false),
                Arguments.of("0800", "960000", null,
                        "KEY_CHANGE", KEY_CHANGE, true),
                Arguments.of("9700", "380000", null,
                        "INFORMATION_INQUIRY", INQUIRY, false));
    }
}
