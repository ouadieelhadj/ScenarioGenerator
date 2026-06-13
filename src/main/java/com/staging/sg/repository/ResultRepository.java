package com.staging.sg.repository;

import com.staging.sg.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {
    List<Result> findByExecutionId(Long executionId);
    long         countByExecutionId(Long executionId);
    long         countByExecutionIdAndApprovedTrue(Long executionId);
    long         countByExecutionIdAndApprovedFalse(Long executionId);
}
