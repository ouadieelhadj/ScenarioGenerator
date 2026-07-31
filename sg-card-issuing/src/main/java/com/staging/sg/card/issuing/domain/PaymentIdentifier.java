package com.staging.sg.card.issuing.domain;

import com.staging.sg.common.issuing.PaymentIdentifierType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuing_payment_identifier")
public class PaymentIdentifier {
    @Id
    private UUID id;
    @Column(name = "issuer_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 32, updatable = false)
    private PaymentIdentifierType identifierType;
    @Column(name = "vault_reference", nullable = false, unique = true,
            length = 128, updatable = false)
    private String vaultReference;
    @Column(name = "masked_value", nullable = false, length = 32)
    private String maskedValue;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentIdentifierStatus status;
    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;
    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected PaymentIdentifier() {
    }

    public static PaymentIdentifier activePan(
            String issuerId, UUID instrumentId,
            String vaultReference, String maskedValue) {
        if (issuerId == null || issuerId.isBlank() || instrumentId == null
                || vaultReference == null || vaultReference.isBlank()
                || maskedValue == null || !maskedValue.matches("\\d{6}\\*+\\d{4}")) {
            throw new IllegalArgumentException("Invalid protected payment identifier");
        }
        PaymentIdentifier value = new PaymentIdentifier();
        value.id = UUID.randomUUID();
        value.issuerId = issuerId;
        value.instrumentId = instrumentId;
        value.identifierType = PaymentIdentifierType.PAN;
        value.vaultReference = vaultReference;
        value.maskedValue = maskedValue;
        value.status = PaymentIdentifierStatus.ACTIVE;
        value.effectiveFrom = Instant.now();
        return value;
    }

    public UUID id() { return id; }
    public UUID instrumentId() { return instrumentId; }
    public PaymentIdentifierType identifierType() { return identifierType; }
    public String maskedValue() { return maskedValue; }
    public PaymentIdentifierStatus status() { return status; }
}
