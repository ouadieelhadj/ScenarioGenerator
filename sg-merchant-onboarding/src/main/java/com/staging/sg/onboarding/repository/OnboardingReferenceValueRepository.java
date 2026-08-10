package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingReferenceValue;
import com.staging.sg.onboarding.domain.OnboardingReferenceValueId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OnboardingReferenceValueRepository
        extends JpaRepository<OnboardingReferenceValue, OnboardingReferenceValueId> {
    boolean existsByIdCategoryAndIdCodeAndActiveTrue(String category, String code);
    List<OnboardingReferenceValue> findByIdCategoryAndActiveTrueOrderByLabelAsc(String category);
    List<OnboardingReferenceValue> findByIdCategoryOrderByLabelAsc(String category);
}
