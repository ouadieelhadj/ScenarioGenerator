package com.staging.sg.swam.lis.member.repository;

import com.staging.sg.swam.lis.member.persistence.MemberBatchExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberBatchExecutionRepository extends JpaRepository<MemberBatchExecution, Long> {
    Optional<MemberBatchExecution> findByCorrelationId(UUID correlationId);
}
