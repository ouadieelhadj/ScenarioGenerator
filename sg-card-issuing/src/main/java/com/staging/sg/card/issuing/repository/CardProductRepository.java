package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.CardProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardProductRepository extends JpaRepository<CardProduct, UUID> {
    Optional<CardProduct> findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
            String issuerId, String createdBy, String creationIdempotencyKey);
}
