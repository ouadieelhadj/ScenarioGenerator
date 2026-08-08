package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.MerchantPortalAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantPortalAccountRepository extends JpaRepository<MerchantPortalAccount, UUID> {
    Optional<MerchantPortalAccount> findByLogin(String login);
}
