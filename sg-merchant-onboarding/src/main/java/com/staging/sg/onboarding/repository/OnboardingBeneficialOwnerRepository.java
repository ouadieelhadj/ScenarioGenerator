package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingBeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OnboardingBeneficialOwnerRepository extends JpaRepository<OnboardingBeneficialOwner, UUID> {
    List<OnboardingBeneficialOwner> findByCaseIdAndActiveTrueOrderByCreatedAtAsc(UUID caseId);
    List<OnboardingBeneficialOwner> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
