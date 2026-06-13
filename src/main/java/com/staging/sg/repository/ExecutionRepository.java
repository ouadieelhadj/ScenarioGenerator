package com.staging.sg.repository;

import com.staging.sg.entity.Execution;
import com.staging.sg.entity.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    List<Execution> findByUserId(Long userId);
    List<Execution> findByTestId(Long testId);
    List<Execution> findByStatus(ExecutionStatus status);
    List<Execution> findByUserIdOrderByStartedAtDesc(Long userId);
    List<Execution> findAllByOrderByStartedAtDesc();
}
