package com.staging.sg.common.repository;

import com.staging.sg.common.entity.AcqIpmFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AcqIpmFileRepository extends JpaRepository<AcqIpmFile, Long> {
    List<AcqIpmFile> findByExecutionId(Long executionId);
    List<AcqIpmFile> findByDirection(String direction);
    List<AcqIpmFile> findAllByOrderByGenerationDateDesc();
}
