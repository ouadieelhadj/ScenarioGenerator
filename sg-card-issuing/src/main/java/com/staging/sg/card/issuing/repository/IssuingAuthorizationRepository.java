package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.IssuingAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IssuingAuthorizationRepository extends JpaRepository<IssuingAuthorization,UUID> {
    Optional<IssuingAuthorization> findByIssuerIdAndCallerIdAndIdempotencyKey(
            String issuerId,String callerId,String idempotencyKey);
    Optional<IssuingAuthorization> findByIssuerIdAndTransactionId(
            String issuerId,String transactionId);
}
