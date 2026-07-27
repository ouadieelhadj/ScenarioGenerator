package com.staging.sg.swam.lis.switching.repository;

import com.staging.sg.swam.lis.common.model.ChargebackDirection;
import com.staging.sg.swam.lis.switching.persistence.SwitchChargeback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwitchChargebackRepository extends JpaRepository<SwitchChargeback, Long> {
    Page<SwitchChargeback> findByBankMemberIdAndDirection(
            String bankMemberId, ChargebackDirection direction, Pageable pageable);
    boolean existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
            Long clearingTransactionId, ChargebackDirection direction, int cycleNumber, String reasonCode);
    java.util.List<SwitchChargeback> findByStatusOrderById(
            com.staging.sg.swam.lis.common.model.ChargebackStatus status);
}
