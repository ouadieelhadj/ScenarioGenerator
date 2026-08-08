package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.MerchantOnboardingCase;
import com.staging.sg.onboarding.domain.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantOnboardingCaseRepository extends JpaRepository<MerchantOnboardingCase, UUID> {
    List<MerchantOnboardingCase> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    Optional<MerchantOnboardingCase> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<MerchantOnboardingCase> findByKycStatusOrderByCreatedAtAsc(KycStatus status);
}
