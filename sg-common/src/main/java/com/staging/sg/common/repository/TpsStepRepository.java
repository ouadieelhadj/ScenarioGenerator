package com.staging.sg.common.repository;

import com.staging.sg.common.entity.TpsStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpsStepRepository extends JpaRepository<TpsStep, Long> {
    List<TpsStep> findByTestIdOrderByStepOrderAsc(Long testId);
    void          deleteByTestId(Long testId);
}
