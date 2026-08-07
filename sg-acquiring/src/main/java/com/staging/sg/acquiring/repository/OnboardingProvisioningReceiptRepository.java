package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.OnboardingProvisioningReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingProvisioningReceiptRepository
        extends JpaRepository<OnboardingProvisioningReceipt, String> {}
