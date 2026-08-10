package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingOutboxEventRepository extends JpaRepository<OnboardingOutboxEvent, UUID> {
    Optional<OnboardingOutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            select * from onboarding_outbox
             where attempt_count < 8
               and ((status = 'PENDING' and available_at <= :now)
                 or (status = 'PROCESSING' and lease_until <= :now))
             order by created_at
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<OnboardingOutboxEvent> lockDispatchable(@Param("now") Instant now,
            @Param("limit") int limit);

    @Query(value = """
            select * from onboarding_outbox
             where status = 'PROCESSING' and lease_until <= :now and attempt_count >= 8
             order by created_at
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<OnboardingOutboxEvent> lockExpiredExhausted(@Param("now") Instant now,
            @Param("limit") int limit);
}
