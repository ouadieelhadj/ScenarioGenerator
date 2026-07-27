package com.staging.sg.swam.lis.member.repository;

import com.staging.sg.swam.lis.common.model.MatchStatus;
import com.staging.sg.swam.lis.member.persistence.MemberClearingTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MemberClearingTransactionRepository extends JpaRepository<MemberClearingTransaction, Long> {
    Optional<MemberClearingTransaction> findByFunctionalKey(String functionalKey);
    boolean existsByLocalSourceTypeAndLocalSidTransactionIdAndClearingCycle(
            String localSourceType, Long localSidTransactionId, int clearingCycle);
    Page<MemberClearingTransaction> findByBankMemberIdAndMatchStatus(
            String bankMemberId, MatchStatus matchStatus, Pageable pageable);
    List<MemberClearingTransaction> findByBusinessDayIdAndLocalSourceTypeOrderById(
            Long businessDayId, String localSourceType);
    List<MemberClearingTransaction> findByBusinessDayIdAndAccountingStatusOrderById(
            Long businessDayId, com.staging.sg.swam.lis.common.model.AccountingStatus status);
}
