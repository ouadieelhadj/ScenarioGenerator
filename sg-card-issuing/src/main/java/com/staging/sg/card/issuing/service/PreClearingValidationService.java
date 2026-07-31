package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.PaymentIdentifierStatus;
import com.staging.sg.card.issuing.port.PaymentIdentifierResolutionPort;
import com.staging.sg.card.issuing.port.PaymentIdentifierNotFoundException;
import com.staging.sg.card.issuing.repository.IssuingAuthorizationRepository;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import com.staging.sg.common.issuing.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PreClearingValidationService
        implements PreClearingValidationUseCase {
    private final IssuingAuthorizationRepository authorizations;
    private final PaymentIdentifierResolutionPort resolver;
    private final PaymentIdentifierRepository identifiers;

    public PreClearingValidationService(
            IssuingAuthorizationRepository authorizations,
            PaymentIdentifierResolutionPort resolver,
            PaymentIdentifierRepository identifiers) {
        this.authorizations=authorizations;
        this.resolver=resolver;
        this.identifiers=identifiers;
    }

    @Override
    public PreClearingValidationResponse validate(
            PreClearingValidationRequest request) {
        IssuingContractValidator.validate(request);
        if(request.authorizationTransactionId()==null
                || request.authorizationTransactionId().isBlank())
            return response(request,PreClearingVerdict.NOT_FOUND,
                    List.of("AUTHORIZATION_TRANSACTION_ID_MISSING"));
        var authorization=authorizations.findByIssuerIdAndTransactionId(
                request.issuerId(),request.authorizationTransactionId());
        if(authorization.isEmpty())
            return response(request,PreClearingVerdict.NOT_FOUND,
                    List.of("AUTHORIZATION_NOT_FOUND"));

        var mismatches=new ArrayList<String>();
        try {
            var resolved=resolver.resolve(request.issuerId(),
                    request.paymentIdentifierType(),request.paymentIdentifier());
            var identifier=identifiers
                    .findByIssuerIdAndVaultReferenceAndStatus(
                            request.issuerId(),
                            resolved.vaultReference(),PaymentIdentifierStatus.ACTIVE);
            if(identifier.isEmpty() || authorization.get().paymentIdentifierId()==null
                    || !identifier.get().id().equals(
                            authorization.get().paymentIdentifierId()))
                mismatches.add("PAYMENT_IDENTIFIER");
        } catch(PaymentIdentifierNotFoundException notFound) {
            mismatches.add("PAYMENT_IDENTIFIER");
        } catch(RuntimeException unavailable) {
            return response(request,PreClearingVerdict.REVIEW_REQUIRED,
                    List.of("IDENTIFIER_RESOLUTION_UNAVAILABLE"));
        }
        if(authorization.get().status()!=IssuingDecisionStatus.APPROVED
                && authorization.get().status()!=IssuingDecisionStatus.PARTIALLY_APPROVED)
            mismatches.add("AUTHORIZATION_STATUS");
        if(authorization.get().approvedAmountMinor()!=request.amountMinor())
            mismatches.add("AMOUNT");
        if(!authorization.get().currency().equals(request.currency()))
            mismatches.add("CURRENCY");
        if(request.authorizationCode()!=null
                && !request.authorizationCode().equals(
                        authorization.get().authorizationCode()))
            mismatches.add("AUTHORIZATION_CODE");
        return response(request,mismatches.isEmpty()
                ? PreClearingVerdict.MATCHED : PreClearingVerdict.MISMATCH,mismatches);
    }

    private static PreClearingValidationResponse response(
            PreClearingValidationRequest request,PreClearingVerdict verdict,
            List<String> mismatches){
        return new PreClearingValidationResponse(
                "1.0",request.issuerId(),request.clearingRecordId(),
                request.correlationId(),verdict,
                request.authorizationTransactionId(),mismatches,false,
                Map.of("processing","READ_ONLY"));
    }
}
