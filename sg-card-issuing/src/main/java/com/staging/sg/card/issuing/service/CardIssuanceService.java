package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CardInstrumentRepresentation;
import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.CardContractStatus;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.port.PanReservationCommand;
import com.staging.sg.card.issuing.port.PanVaultPort;
import com.staging.sg.card.issuing.port.ProtectedPan;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardInstrumentRepository;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Service
public class CardIssuanceService {
    private final CardContractRepository contracts;
    private final CardProductRepository products;
    private final CardInstrumentRepository instruments;
    private final PanVaultPort panVault;
    private final CardIssuancePersistenceService persistence;
    private PanTokenService tokens;

    public CardIssuanceService(
            CardContractRepository contracts,
            CardProductRepository products,
            CardInstrumentRepository instruments,
            PanVaultPort panVault,
            CardIssuancePersistenceService persistence) {
        this.contracts = contracts;
        this.products = products;
        this.instruments = instruments;
        this.panVault = panVault;
        this.persistence = persistence;
    }

    @Autowired
    void setPanTokenService(PanTokenService tokens) {
        this.tokens = tokens;
    }

    public CardInstrumentRepresentation issueVirtual(
            UUID contractId, String issuerId, String callerId,
            String idempotencyKey, String correlationId) {
        String fingerprint = CommandFingerprint.of(
                issuerId, contractId, "VIRTUAL_CARD");
        var existing = instruments
                .findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                        issuerId, callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().issuanceMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used for another card issuance");
            }
            return persistence.persist(
                    contractId, issuerId, callerId, idempotencyKey,
                    correlationId, fingerprint,
                    new ProtectedPan(
                            existing.get().panVaultReference(),
                            existing.get().maskedPan(),
                            existing.get().expiryYymm()));
        }
        CardContract contract = contracts.findById(contractId)
                .filter(value -> value.issuerId().equals(issuerId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown card contract"));
        if (contract.status() != CardContractStatus.ACTIVE) {
            throw new IllegalStateException(
                    "A card can only be issued from an active contract");
        }
        CardProduct product = products.findById(contract.productId())
                .filter(CardProduct::isActive)
                .filter(value -> value.issuerId().equals(issuerId))
                .orElseThrow(() -> new IllegalStateException(
                        "The card product is no longer active"));

        // No SQL transaction remains open while the external vault is called.
        // The vault must honor the same idempotency key on retries.
        ProtectedPan protectedPan = panVault.reserveVirtualPan(
                new PanReservationCommand(
                        issuerId, contract.id(), product.id(),
                        correlationId, idempotencyKey));
        return persistence.persist(
                contractId, issuerId, callerId, idempotencyKey,
                correlationId, fingerprint, protectedPan);
    }

    public CardInstrumentRepresentation register(
            UUID contractId, String issuerId, String callerId,
            String idempotencyKey, String correlationId,
            String clearPan, String expiryYymm) {
        String maskedPan = PanTokenService.mask(clearPan);
        if (expiryYymm == null || !expiryYymm.matches("\\d{4}")) {
            throw new IllegalArgumentException("Expiry must use YYMM");
        }
        String fingerprint = CommandFingerprint.of(
                issuerId, contractId, "REGISTER_CARD", clearPan, expiryYymm);
        var existing = instruments
                .findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                        issuerId, callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().issuanceMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used for another card issuance");
            }
            return persistence.persist(
                    contractId, issuerId, callerId, idempotencyKey,
                    correlationId, fingerprint,
                    new ProtectedPan(
                            existing.get().panVaultReference(),
                            existing.get().maskedPan(),
                            existing.get().expiryYymm()));
        }
        CardContract contract = contracts.findById(contractId)
                .filter(value -> value.issuerId().equals(issuerId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown card contract"));
        if (contract.status() != CardContractStatus.ACTIVE) {
            throw new IllegalStateException(
                    "A card can only be registered on an active contract");
        }
        products.findById(contract.productId())
                .filter(CardProduct::isActive)
                .filter(value -> value.issuerId().equals(issuerId))
                .orElseThrow(() -> new IllegalStateException(
                        "The card product is no longer active"));
        if (tokens == null) {
            throw new IllegalStateException("PAN token service is unavailable");
        }
        return persistence.persist(
                contractId, issuerId, callerId, idempotencyKey,
                correlationId, fingerprint,
                new ProtectedPan(
                        tokens.newToken(), clearPan, maskedPan, expiryYymm));
    }
}
