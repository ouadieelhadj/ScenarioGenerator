package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamIssTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SwamIssTransactionRepository extends JpaRepository<SwamIssTransaction, Long> {
    Optional<SwamIssTransaction> findByStanAndTransmissionDt(String stan, String transmissionDt);
    Optional<SwamIssTransaction> findFirstByRrnAndClearingEligibleTrueOrderByCreatedAtDesc(String rrn);
}
