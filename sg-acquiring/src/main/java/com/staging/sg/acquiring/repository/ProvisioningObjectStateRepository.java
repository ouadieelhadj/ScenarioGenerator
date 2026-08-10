package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.ProvisioningObjectState;
import com.staging.sg.acquiring.domain.ProvisioningObjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningObjectStateRepository extends JpaRepository<ProvisioningObjectState, UUID> {
    Optional<ProvisioningObjectState> findByIdempotencyKey(String idempotencyKey);
    Optional<ProvisioningObjectState> findByObjectTypeAndObjectId(String objectType, UUID objectId);
    List<ProvisioningObjectState> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            ProvisioningObjectStatus status, Instant now);
}
