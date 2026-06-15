package com.staging.sg.common.repository;

import com.staging.sg.common.entity.AcqReversal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcqReversalRepository extends JpaRepository<AcqReversal, Long> {
    List<AcqReversal> findByExecutionId(Long executionId);
    List<AcqReversal> findByAcqAuthorizationId(Long acqAuthId);
    List<AcqReversal> findAllByOrderBySentAtDesc();
}
