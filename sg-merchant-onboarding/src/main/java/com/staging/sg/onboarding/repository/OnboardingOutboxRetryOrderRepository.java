package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingOutboxRetryOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OnboardingOutboxRetryOrderRepository
        extends JpaRepository<OnboardingOutboxRetryOrder, UUID> {}
