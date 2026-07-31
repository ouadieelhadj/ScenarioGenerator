package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CreateCardProductRequest;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.CardType;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardProductServiceTest {
    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        CardProductRepository products = mock(CardProductRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CardProduct existing = CardProduct.draft(
                "ISSUER-1", "DEBIT-STD", 1, CardType.DEBIT, "504",
                true, false, false, "maker-1", "idem-1", "another-fingerprint");
        when(products.findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
                "ISSUER-1", "maker-1", "idem-1")).thenReturn(Optional.of(existing));
        CardProductService service = new CardProductService(products, outbox);

        assertThrows(IllegalStateException.class, () -> service.create(
                new CreateCardProductRequest(
                        "ISSUER-1", "DEBIT-STD", 1, CardType.DEBIT,
                        "504", true, false, false),
                "maker-1", "idem-1", "corr-1"));

        verify(products, never()).save(any());
        verify(outbox, never()).save(any());
    }
}
