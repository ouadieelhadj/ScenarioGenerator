package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmcsAcquirerClearingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DmcsAcquirerClearingTransactionRepository
        extends JpaRepository<DmcsAcquirerClearingTransaction, Long> {
    boolean existsBySourceTypeAndLocalAuthorizationIdAndLifecycleStage(
            String sourceType, Long localAuthorizationId, String lifecycleStage);

    List<DmcsAcquirerClearingTransaction>
    findByBusinessDateAndDirectionAndStatusOrderById(
            LocalDate businessDate, String direction, String status);
}
