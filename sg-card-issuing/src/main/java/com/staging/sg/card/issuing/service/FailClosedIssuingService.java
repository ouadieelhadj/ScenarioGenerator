package com.staging.sg.card.issuing.service;

import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import com.staging.sg.common.issuing.IssuingDecisionStatus;
import com.staging.sg.common.issuing.PreClearingValidationRequest;
import com.staging.sg.common.issuing.PreClearingValidationResponse;
import com.staging.sg.common.issuing.PreClearingVerdict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FailClosedIssuingService
        implements IssuerAuthorizationUseCase, PreClearingValidationUseCase {

    @Override
    public IssuingAuthorizationResponse authorize(
            IssuingAuthorizationRequest request) {
        IssuingContractValidator.validate(request);
        return new IssuingAuthorizationResponse(
                "1.0", request.issuerId(), request.transactionId(),
                request.correlationId(), IssuingDecisionStatus.UNKNOWN,
                "ISSUER_DEPENDENCIES_NOT_READY", null, 0,
                request.currency(), null, true,
                Map.of("decisionOwner", "sg-card-issuing",
                        "processing", "FAIL_CLOSED"));
    }

    @Override
    public PreClearingValidationResponse validate(
            PreClearingValidationRequest request) {
        IssuingContractValidator.validate(request);
        return new PreClearingValidationResponse(
                "1.0", request.issuerId(), request.clearingRecordId(),
                request.correlationId(), PreClearingVerdict.REVIEW_REQUIRED,
                request.authorizationTransactionId(),
                List.of("ISSUER_DATA_NOT_READY"), false,
                Map.of("processing", "FAIL_CLOSED"));
    }
}
