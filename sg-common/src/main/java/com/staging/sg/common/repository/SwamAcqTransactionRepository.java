package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamAcqTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SwamAcqTransactionRepository extends JpaRepository<SwamAcqTransaction, Long> {
    Optional<SwamAcqTransaction> findFirstByRrnAndClearingEligibleTrueOrderByCreatedAtDesc(String rrn);
    List<SwamAcqTransaction> findByClearingEligibleTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
