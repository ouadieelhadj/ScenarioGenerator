package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmcsIssuerClearingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DmcsIssuerClearingTransactionRepository
        extends JpaRepository<DmcsIssuerClearingTransaction, Long> {
    boolean existsBySourceTypeAndLocalAuthorizationIdAndLifecycleStage(
            String sourceType, Long localAuthorizationId, String lifecycleStage);

    List<DmcsIssuerClearingTransaction>
    findByBusinessDateAndDirectionAndStatusOrderById(
            LocalDate businessDate, String direction, String status);
}
