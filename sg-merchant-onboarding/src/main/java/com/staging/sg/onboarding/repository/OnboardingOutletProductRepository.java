package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingOutletProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OnboardingOutletProductRepository extends JpaRepository<OnboardingOutletProduct, UUID> {
    List<OnboardingOutletProduct> findByCaseIdAndActiveTrueOrderByOutletIdAscProductIdAsc(UUID caseId);
    List<OnboardingOutletProduct> findByOutletIdAndActiveTrueOrderByProductIdAsc(UUID outletId);
}
