package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByAcquirerIdAndCreatedByAndCreationIdempotencyKey(
            String acquirerId, String createdBy, String creationIdempotencyKey);
}
