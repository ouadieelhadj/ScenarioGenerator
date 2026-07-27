package com.staging.sg.swam.lis.switching.repository;

import com.staging.sg.swam.lis.common.model.MatchStatus;
import com.staging.sg.swam.lis.switching.persistence.SwitchClearingTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface SwitchClearingTransactionRepository extends JpaRepository<SwitchClearingTransaction, Long> {
    Optional<SwitchClearingTransaction> findByFunctionalKey(String functionalKey);
    boolean existsByLocalSourceTypeAndLocalSidTransactionIdAndClearingCycle(
            String localSourceType, Long localSidTransactionId, int clearingCycle);
    Page<SwitchClearingTransaction> findByBankMemberIdAndMatchStatus(
            String bankMemberId, MatchStatus matchStatus, Pageable pageable);
    List<SwitchClearingTransaction> findByBusinessDayIdAndLocalSourceTypeOrderById(
            Long businessDayId, String localSourceType);
    List<SwitchClearingTransaction> findByBusinessDayIdAndAccountingStatusOrderById(
            Long businessDayId, com.staging.sg.swam.lis.common.model.AccountingStatus status);
}
