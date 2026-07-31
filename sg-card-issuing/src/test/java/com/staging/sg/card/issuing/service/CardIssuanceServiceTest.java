package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CardInstrumentRepresentation;
import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.CardInstrumentStatus;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.CardType;
import com.staging.sg.card.issuing.port.PanVaultPort;
import com.staging.sg.card.issuing.port.ProtectedPan;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardInstrumentRepository;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardIssuanceServiceTest {
    @Test
    void reservesPanOutsidePersistenceAndReturnsProtectedInstrument() {
        CardContractRepository contracts = mock(CardContractRepository.class);
        CardProductRepository products = mock(CardProductRepository.class);
        CardInstrumentRepository instruments = mock(CardInstrumentRepository.class);
        PanVaultPort vault = mock(PanVaultPort.class);
        CardIssuancePersistenceService persistence =
                mock(CardIssuancePersistenceService.class);
        CardProduct product = activeProduct();
        CardContract contract = activeContract(product.id());
        when(instruments.findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                "ISSUER-1", "maker-1", "idem-card")).thenReturn(Optional.empty());
        when(contracts.findById(contract.id())).thenReturn(Optional.of(contract));
        when(products.findById(product.id())).thenReturn(Optional.of(product));
        ProtectedPan protectedPan = new ProtectedPan(
                "vault-ref-1", "532196******3348", "2912");
        when(vault.reserveVirtualPan(any())).thenReturn(protectedPan);
        var expected = new CardInstrumentRepresentation(
                UUID.randomUUID(), "ISSUER-1", contract.id(),
                "532196******3348", "2912", CardInstrumentStatus.INACTIVE,
                UUID.randomUUID(), PaymentIdentifierType.PAN, false);
        when(persistence.persist(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);
        CardIssuanceService service = new CardIssuanceService(
                contracts, products, instruments, vault, persistence);

        var response = service.issueVirtual(
                contract.id(), "ISSUER-1", "maker-1",
                "idem-card", "corr-card");

        assertEquals(expected, response);
        verify(vault).reserveVirtualPan(any());
        verify(persistence).persist(
                contract.id(), "ISSUER-1", "maker-1", "idem-card",
                "corr-card", CommandFingerprint.of(
                        "ISSUER-1", contract.id(), "VIRTUAL_CARD"),
                protectedPan);
    }

    @Test
    void inactiveContractNeverCallsPanVault() {
        CardContractRepository contracts = mock(CardContractRepository.class);
        CardProductRepository products = mock(CardProductRepository.class);
        CardInstrumentRepository instruments = mock(CardInstrumentRepository.class);
        PanVaultPort vault = mock(PanVaultPort.class);
        CardIssuancePersistenceService persistence =
                mock(CardIssuancePersistenceService.class);
        CardContract contract = CardContract.draft(
                "ISSUER-1", "CONTRACT-1", "CUSTOMER-1", "HOLDER-1",
                "ACCOUNT-1", UUID.randomUUID(), "maker-1",
                "idem-contract", "fingerprint");
        when(instruments.findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                "ISSUER-1", "maker-1", "idem-card")).thenReturn(Optional.empty());
        when(contracts.findById(contract.id())).thenReturn(Optional.of(contract));
        CardIssuanceService service = new CardIssuanceService(
                contracts, products, instruments, vault, persistence);

        assertThrows(IllegalStateException.class, () -> service.issueVirtual(
                contract.id(), "ISSUER-1", "maker-1",
                "idem-card", "corr-card"));

        verify(vault, never()).reserveVirtualPan(any());
        verify(persistence, never()).persist(
                any(), any(), any(), any(), any(), any(), any());
    }

    private static CardProduct activeProduct() {
        CardProduct product = CardProduct.draft(
                "ISSUER-1", "DEBIT-STD", 1, CardType.DEBIT, "504",
                true, true, true, "maker-1", "idem-product", "fingerprint");
        product.approve("checker-1");
        product.activate();
        return product;
    }

    private static CardContract activeContract(UUID productId) {
        CardContract contract = CardContract.draft(
                "ISSUER-1", "CONTRACT-1", "CUSTOMER-1", "HOLDER-1",
                "ACCOUNT-1", productId, "maker-1",
                "idem-contract", "fingerprint");
        contract.submit();
        contract.approve("checker-1");
        return contract;
    }
}
