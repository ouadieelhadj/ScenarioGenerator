package com.staging.sg.threeds.member.repository;

import com.staging.sg.threeds.member.domain.MemberAuthentication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberAuthenticationRepository
        extends JpaRepository<MemberAuthentication, UUID> {
    Optional<MemberAuthentication> findByTransactionId(String transactionId);
    Optional<MemberAuthentication> findByDsTransId(UUID dsTransId);
}
