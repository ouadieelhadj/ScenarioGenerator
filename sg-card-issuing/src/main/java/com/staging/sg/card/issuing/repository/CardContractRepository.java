package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.CardContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardContractRepository extends JpaRepository<CardContract, UUID> {
    Optional<CardContract> findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
            String issuerId, String createdBy, String creationIdempotencyKey);
}
