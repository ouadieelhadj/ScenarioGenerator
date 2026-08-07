package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.MerchantOnboardingCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantOnboardingCaseRepository extends JpaRepository<MerchantOnboardingCase, UUID> {
    List<MerchantOnboardingCase> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
