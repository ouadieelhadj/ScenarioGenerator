package com.staging.sg.common.issuing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuingContractsTest {
    @Test
    void authorizationRequestToStringRedactsPaymentAndSecurityData() {
        var request = new IssuingAuthorizationRequest(
                "1.0", "ISSUER-1", "WAY_POS", "tx-1", "corr-1", "idem-1",
                IssuingOperation.AUTHORIZATION, null, PaymentIdentifierType.PAN,
                "5321962145453348", 1_000, "504", "2026-07-31T10:00:00",
                "TERM0001", "MERCHANT000001", "5411", "504",
                true, false, "0011223344556677", "00000",
                "9F26081122334455667788", Map.of());

        String rendered = request.toString();
        assertFalse(rendered.contains("5321962145453348"));
        assertFalse(rendered.contains("0011223344556677"));
        assertFalse(rendered.contains("9F26081122334455667788"));
    }

    @Test
    void preClearingContractForbidsFinancialMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> new PreClearingValidationResponse(
                        "1.0", "ISSUER-1", "clr-1", "corr-1",
                        PreClearingVerdict.MATCHED, "tx-1",
                        List.of(), true, Map.of()));
    }
}
