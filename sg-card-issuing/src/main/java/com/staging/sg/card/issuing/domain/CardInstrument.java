package com.staging.sg.card.issuing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuing_card_instrument")
public class CardInstrument {
    @Id
    private UUID id;
    @Column(name = "issuer_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "pan_vault_reference", nullable = false, unique = true,
            length = 128, updatable = false)
    private String panVaultReference;
    @Column(name = "masked_pan", nullable = false, length = 32)
    private String maskedPan;
    @Column(name = "expiry_yymm", nullable = false, length = 4)
    private String expiryYymm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CardInstrumentStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected CardInstrument() {
    }

    public static CardInstrument inactive(
            String issuerId, UUID contractId, String panVaultReference,
            String maskedPan, String expiryYymm) {
        if (issuerId == null || issuerId.isBlank() || contractId == null
                || panVaultReference == null || panVaultReference.isBlank()
                || maskedPan == null || !maskedPan.matches("\\d{6}\\*+\\d{4}")
                || expiryYymm == null || !expiryYymm.matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid protected card instrument");
        }
        CardInstrument value = new CardInstrument();
        value.id = UUID.randomUUID();
        value.issuerId = issuerId;
        value.contractId = contractId;
        value.panVaultReference = panVaultReference;
        value.maskedPan = maskedPan;
        value.expiryYymm = expiryYymm;
        value.status = CardInstrumentStatus.INACTIVE;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void activate(CardContractStatus contractStatus) {
        if (status == CardInstrumentStatus.ACTIVE) return;
        if (contractStatus != CardContractStatus.ACTIVE
                || status != CardInstrumentStatus.INACTIVE) {
            throw new IllegalStateException(
                    "Instrument requires an active contract and INACTIVE state");
        }
        status = CardInstrumentStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public void blockTemporarily() {
        if (status == CardInstrumentStatus.TEMPORARILY_BLOCKED) return;
        if (status != CardInstrumentStatus.ACTIVE) {
            throw new IllegalStateException("Only an active instrument can be blocked");
        }
        status = CardInstrumentStatus.TEMPORARILY_BLOCKED;
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String issuerId() { return issuerId; }
    public UUID contractId() { return contractId; }
    public String maskedPan() { return maskedPan; }
    public String expiryYymm() { return expiryYymm; }
    public CardInstrumentStatus status() { return status; }
}
