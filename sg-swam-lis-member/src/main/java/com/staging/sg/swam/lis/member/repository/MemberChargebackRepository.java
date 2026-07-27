package com.staging.sg.swam.lis.member.repository;

import com.staging.sg.swam.lis.common.model.ChargebackDirection;
import com.staging.sg.swam.lis.member.persistence.MemberChargeback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberChargebackRepository extends JpaRepository<MemberChargeback, Long> {
    Page<MemberChargeback> findByBankMemberIdAndDirection(
            String bankMemberId, ChargebackDirection direction, Pageable pageable);
    boolean existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
            Long clearingTransactionId, ChargebackDirection direction, int cycleNumber, String reasonCode);
    java.util.List<MemberChargeback> findByStatusOrderById(
            com.staging.sg.swam.lis.common.model.ChargebackStatus status);
}
