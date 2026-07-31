package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIdentifierRepository
        extends JpaRepository<PaymentIdentifier, UUID> {
    Optional<PaymentIdentifier> findByInstrumentId(UUID instrumentId);
}
