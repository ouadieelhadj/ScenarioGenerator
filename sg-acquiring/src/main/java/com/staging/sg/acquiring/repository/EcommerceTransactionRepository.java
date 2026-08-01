package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.EcommerceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EcommerceTransactionRepository extends JpaRepository<EcommerceTransaction, UUID> {
    Optional<EcommerceTransaction> findByAcquirerIdAndIdempotencyKey(
            String acquirerId, String idempotencyKey);
}
