package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.IssuingAuthorization;
import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.port.PaymentIdentifierResolutionPort;
import com.staging.sg.card.issuing.repository.IssuingAuthorizationRepository;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import com.staging.sg.common.issuing.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreClearingValidationServiceTest {
    @Test
    void matchesWithoutAnyFinancialMutation() {
        var authorizations=mock(IssuingAuthorizationRepository.class);
        var resolver=mock(PaymentIdentifierResolutionPort.class);
        var identifiers=mock(PaymentIdentifierRepository.class);
        var identifier=PaymentIdentifier.activePan(
                "ISSUER-1",UUID.randomUUID(),"vault-ref","123456******7890");
        var authorization=IssuingAuthorization.decided(
                "ISSUER-1","SERVER_POS","txn-1","corr-auth","idem-auth","fp",
                identifier.id(),IssuingOperation.AUTHORIZATION,null,1000,"504",
                IssuingDecisionStatus.APPROVED,"APPROVED","123456",1000,false);
        when(authorizations.findByIssuerIdAndTransactionId("ISSUER-1","txn-1"))
                .thenReturn(Optional.of(authorization));
        when(resolver.resolve(any(),any(),any())).thenReturn(
                new PaymentIdentifierResolutionPort.ResolvedPaymentIdentifier("vault-ref"));
        when(identifiers.findByIssuerIdAndIdentifierTypeAndVaultReferenceAndStatus(
                any(),any(),any(),any())).thenReturn(Optional.of(identifier));
        var service=new PreClearingValidationService(
                authorizations,resolver,identifiers);

        var response=service.validate(new PreClearingValidationRequest(
                "1.0","ISSUER-1","PRE_CLEARING","clear-1","corr-1","idem-1",
                PaymentIdentifierType.PAN,"protected-input","txn-1","123456",
                1000,"504","2026-07-31T10:00:00",Map.of(),Map.of()));

        assertEquals(PreClearingVerdict.MATCHED,response.verdict());
        assertFalse(response.financialMutationPerformed());
    }
}
