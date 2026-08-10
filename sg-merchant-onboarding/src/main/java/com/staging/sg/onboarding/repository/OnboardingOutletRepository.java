package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingOutlet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingOutletRepository extends JpaRepository<OnboardingOutlet, UUID> {
    List<OnboardingOutlet> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    Optional<OnboardingOutlet> findByCaseIdAndCode(UUID caseId, String code);
    void deleteByCaseIdAndIdNotIn(UUID caseId, List<UUID> ids);
}
