package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingFieldRule;
import com.staging.sg.onboarding.domain.OnboardingFieldRuleId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OnboardingFieldRuleRepository extends JpaRepository<OnboardingFieldRule, OnboardingFieldRuleId> {
    List<OnboardingFieldRule> findByIdMerchantTypeAndActiveTrueOrderByIdFieldPathAsc(String merchantType);
}
