package com.staging.sg.common.repository;

import com.staging.sg.common.entity.AcqAdvice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcqAdviceRepository extends JpaRepository<AcqAdvice, Long> {
    List<AcqAdvice> findByExecutionId(Long executionId);
    List<AcqAdvice> findByAcqAuthorizationId(Long acqAuthId);
    List<AcqAdvice> findAllByOrderBySentAtDesc();
}
