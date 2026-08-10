package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.EcommerceStoreRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EcommerceStoreRequestRepository extends JpaRepository<EcommerceStoreRequest, UUID> {
    List<EcommerceStoreRequest> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    List<EcommerceStoreRequest> findByOutletIdOrderByCreatedAtAsc(UUID outletId);
}
