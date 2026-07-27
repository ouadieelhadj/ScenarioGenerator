package com.staging.sg.swam.lis.switching.repository;

import com.staging.sg.swam.lis.switching.persistence.SwitchBatchExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SwitchBatchExecutionRepository extends JpaRepository<SwitchBatchExecution, Long> {
    Optional<SwitchBatchExecution> findByCorrelationId(UUID correlationId);
}
