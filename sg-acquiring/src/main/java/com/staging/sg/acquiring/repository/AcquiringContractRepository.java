package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcquiringContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AcquiringContractRepository extends JpaRepository<AcquiringContract, UUID> {
    Optional<AcquiringContract> findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
            String institutionId, String createdBy, String creationIdempotencyKey);
}
