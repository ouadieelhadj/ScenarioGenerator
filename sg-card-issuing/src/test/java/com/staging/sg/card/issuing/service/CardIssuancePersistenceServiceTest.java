package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.port.ProtectedPan;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardInstrumentRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardIssuancePersistenceServiceTest {
    @Test
    void persistsInstrumentIdentifierAndOutboxAtomically() {
        CardContractRepository contracts = mock(CardContractRepository.class);
        CardInstrumentRepository instruments = mock(CardInstrumentRepository.class);
        PaymentIdentifierRepository identifiers =
                mock(PaymentIdentifierRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        UUID productId = UUID.randomUUID();
        CardContract contract = CardContract.draft(
                "ISSUER-1", "CONTRACT-1", "CUSTOMER-1", "HOLDER-1",
                "ACCOUNT-1", productId, "maker-1",
                "idem-contract", "fingerprint");
        contract.submit();
        contract.approve("checker-1");
        when(instruments.findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                "ISSUER-1", "maker-1", "idem-card")).thenReturn(Optional.empty());
        when(contracts.findById(contract.id())).thenReturn(Optional.of(contract));
        CardIssuancePersistenceService service =
                new CardIssuancePersistenceService(
                        contracts, instruments, identifiers, outbox);

        var response = service.persist(
                contract.id(), "ISSUER-1", "maker-1", "idem-card",
                "corr-card", "fingerprint-card",
                new ProtectedPan("vault-ref-1", "532196******3348", "2912"));

        assertEquals("532196******3348", response.maskedPan());
        verify(instruments).save(any());
        verify(identifiers).save(any(PaymentIdentifier.class));
        verify(outbox).save(any());
    }
}
