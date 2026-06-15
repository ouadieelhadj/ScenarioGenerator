package com.staging.sg.common.repository;

import com.staging.sg.common.entity.AcqAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcqAuthorizationRepository extends JpaRepository<AcqAuthorization, Long> {
    List<AcqAuthorization> findByExecutionId(Long executionId);
    List<AcqAuthorization> findByApprovedTrueAndDe039Response(String de039);
    List<AcqAuthorization> findByExecutionIdAndApprovedTrue(Long executionId);
    List<AcqAuthorization> findAllByOrderBySentAtDesc();
}
