package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.domain.PaymentIdentifierStatus;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIdentifierRepository
        extends JpaRepository<PaymentIdentifier, UUID> {
    Optional<PaymentIdentifier> findByInstrumentId(UUID instrumentId);
    Optional<PaymentIdentifier>
    findByIssuerIdAndIdentifierTypeAndVaultReferenceAndStatus(
            String issuerId, PaymentIdentifierType identifierType,
            String vaultReference, PaymentIdentifierStatus status);

    Optional<PaymentIdentifier> findByIssuerIdAndPanClearAndStatus(
            String issuerId, String panClear, PaymentIdentifierStatus status);

    Optional<PaymentIdentifier> findByIssuerIdAndVaultReferenceAndStatus(
            String issuerId, String tokenValue, PaymentIdentifierStatus status);

    boolean existsByVaultReference(String tokenValue);
}
