package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IpmProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface IpmProcessingLogRepository extends JpaRepository<IpmProcessingLog, Long> {
    Optional<IpmProcessingLog> findByFileNameAndRoleAndDirection(String fileName, String role, String direction);
    Optional<IpmProcessingLog> findByChecksumAndRoleAndDirection(String checksum, String role, String direction);
    List<IpmProcessingLog> findByExecutionIdAndRole(Long executionId, String role);
    List<IpmProcessingLog> findAllByOrderByProcessedAtDesc();
}
