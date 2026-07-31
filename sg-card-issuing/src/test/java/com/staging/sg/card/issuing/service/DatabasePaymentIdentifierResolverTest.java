package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.domain.PaymentIdentifierStatus;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabasePaymentIdentifierResolverTest {
    @Test
    void panAndTokenResolveBijectivelyToSameIdentifier() {
        PaymentIdentifierRepository repository =
                mock(PaymentIdentifierRepository.class);
        PaymentIdentifier identifier = PaymentIdentifier.activePan(
                "ISSUER-1", UUID.randomUUID(), "pan_tok_opaque",
                "5321960000003348", "532196******3348");
        when(repository.findByIssuerIdAndPanClearAndStatus(
                "ISSUER-1", "5321960000003348",
                PaymentIdentifierStatus.ACTIVE))
                .thenReturn(Optional.of(identifier));
        when(repository.findByIssuerIdAndVaultReferenceAndStatus(
                "ISSUER-1", "pan_tok_opaque",
                PaymentIdentifierStatus.ACTIVE))
                .thenReturn(Optional.of(identifier));
        DatabasePaymentIdentifierResolver resolver =
                new DatabasePaymentIdentifierResolver(repository);

        assertEquals("pan_tok_opaque", resolver.resolve(
                "ISSUER-1", PaymentIdentifierType.PAN,
                "5321960000003348").vaultReference());
        assertEquals("pan_tok_opaque", resolver.resolve(
                "ISSUER-1", PaymentIdentifierType.NETWORK_TOKEN,
                "pan_tok_opaque").vaultReference());
    }
}
