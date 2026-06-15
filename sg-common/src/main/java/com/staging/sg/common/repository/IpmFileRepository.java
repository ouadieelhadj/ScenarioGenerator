package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IpmFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IpmFileRepository extends JpaRepository<IpmFile, Long> {
    List<IpmFile> findByFileDate(LocalDate fileDate);
    List<IpmFile> findByExecutionId(Long executionId);
    List<IpmFile> findAllByOrderByGenerationDateDesc();
    List<IpmFile> findByStatus(String status);
}
