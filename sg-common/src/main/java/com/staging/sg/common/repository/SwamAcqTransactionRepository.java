package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamAcqTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SwamAcqTransactionRepository extends JpaRepository<SwamAcqTransaction, Long> {
    Optional<SwamAcqTransaction> findFirstByRrnAndClearingEligibleTrueOrderByCreatedAtDesc(String rrn);
}
