package com.staging.sg.card.issuing.service;

import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingDecisionStatus;
import com.staging.sg.common.issuing.IssuingOperation;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.common.issuing.PreClearingValidationRequest;
import com.staging.sg.common.issuing.PreClearingVerdict;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailClosedIssuingServiceTest {
    private final FailClosedIssuingService service = new FailClosedIssuingService();

    @Test
    void neverApprovesBeforeMandatoryDependenciesAreConnected() {
        var response = service.authorize(new IssuingAuthorizationRequest(
                "1.0", "ISSUER-1", "WAY_POS", "tx-1", "corr-1", "idem-1",
                IssuingOperation.AUTHORIZATION, null, PaymentIdentifierType.PAN,
                "5321962145453348", 1_000, "504", null,
                "TERM0001", "MERCHANT000001", "5411", "504",
                true, false, null, null, null, Map.of()));

        assertEquals(IssuingDecisionStatus.UNKNOWN, response.status());
        assertEquals("ISSUER_DEPENDENCIES_NOT_READY", response.internalResponseCode());
        assertTrue(response.retryable());
    }

    @Test
    void preClearingValidationIsReadOnlyAndRequiresReview() {
        var response = service.validate(new PreClearingValidationRequest(
                "1.0", "ISSUER-1", "SWAM_LIS", "clr-1", "corr-1", "idem-1",
                PaymentIdentifierType.PAN, "5321962145453348",
                "tx-1", "123456", 1_000, "504",
                "2026-07-31T10:00:00", Map.of(), Map.of()));

        assertEquals(PreClearingVerdict.REVIEW_REQUIRED, response.verdict());
        assertFalse(response.financialMutationPerformed());
    }
}
