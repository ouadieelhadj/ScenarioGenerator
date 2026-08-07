package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.ProvisioningJob;
import com.staging.sg.onboarding.domain.ProvisioningJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {
    Optional<ProvisioningJob> findByCaseId(UUID caseId);
    List<ProvisioningJob> findByStatusOrderByCreatedAtAsc(ProvisioningJobStatus status);
}
