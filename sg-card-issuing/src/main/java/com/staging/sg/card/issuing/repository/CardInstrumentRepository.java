package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.CardInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardInstrumentRepository extends JpaRepository<CardInstrument, UUID> {
    Optional<CardInstrument>
    findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
            String issuerId, String issuedBy, String issuanceIdempotencyKey);
}
