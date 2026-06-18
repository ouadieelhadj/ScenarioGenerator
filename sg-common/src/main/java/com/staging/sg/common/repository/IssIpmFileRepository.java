package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IssIpmFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IssIpmFileRepository extends JpaRepository<IssIpmFile, Long> {
    List<IssIpmFile> findByExecutionId(Long executionId);
    List<IssIpmFile> findByDirection(String direction);
    List<IssIpmFile> findAllByOrderByGenerationDateDesc();
}
