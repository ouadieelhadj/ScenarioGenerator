package com.staging.sg.swam.lis.member.service;

import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.member.persistence.*;
import com.staging.sg.swam.lis.member.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class MemberChargebackService {
    private final MemberChargebackRepository chargebacks;
    private final MemberClearingTransactionRepository transactions;
    private final String memberId;
    public MemberChargebackService(MemberChargebackRepository chargebacks,
            MemberClearingTransactionRepository transactions,
            @Value("${swam.lis.bank-member-id}") String memberId) {
        this.chargebacks=chargebacks; this.transactions=transactions; this.memberId=memberId;
    }

    @Transactional
    public ChargebackResult emit(ChargebackRequest request) {
        MemberClearingTransaction tx=transactions.findById(request.clearingTransactionId())
                .orElseThrow(()->new IllegalArgumentException("Clearing transaction not found"));
        if(tx.getIncomingLisFileId()==null)
            throw new IllegalStateException("A chargeback requires an incoming LIS presentation");
        if(chargebacks.existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
                tx.getId(),ChargebackDirection.EMITTED,1,request.reasonCode()))
            throw new IllegalStateException("Duplicate first chargeback");
        MemberChargeback cb=base(tx,request,1,chargebackCode(tx.getTransactionType()));
        cb.setDirection(ChargebackDirection.EMITTED); cb.setStatus(ChargebackStatus.READY_TO_SEND);
        cb.setEmittedAt(LocalDateTime.now()); cb=chargebacks.save(cb);
        tx.setDisputeStatus(DisputeStatus.OPEN); tx.setUpdatedAt(LocalDateTime.now()); transactions.save(tx);
        return result(cb);
    }

    @Transactional
    public ChargebackResult represent(Long receivedChargebackId, RepresentationRequest request) {
        MemberChargeback parent=chargebacks.findById(receivedChargebackId)
                .orElseThrow(()->new IllegalArgumentException("Chargeback not found"));
        if(parent.getDirection()!=ChargebackDirection.RECEIVED
                || !(parent.getStatus()==ChargebackStatus.RECEIVED
                || parent.getStatus()==ChargebackStatus.UNDER_REVIEW))
            throw new IllegalStateException("Only a received open chargeback can be represented");
        MemberClearingTransaction tx=transactions.findById(parent.getClearingTransactionId()).orElseThrow();
        ChargebackRequest values=new ChargebackRequest(tx.getId(),parent.getReasonCode(),parent.getAmount(),
                parent.getCurrency(),parent.getCounterpartyMember(),request.createdBy(),request.justification());
        MemberChargeback representation=base(tx,values,2,presentationCode(tx.getTransactionType()));
        representation.setParentChargebackId(parent.getId()); representation.setDirection(ChargebackDirection.EMITTED);
        representation.setStatus(ChargebackStatus.READY_TO_SEND);
        representation=chargebacks.save(representation);
        parent.setStatus(ChargebackStatus.REPRESENTED); parent.setUpdatedAt(LocalDateTime.now()); chargebacks.save(parent);
        return result(representation);
    }

    private MemberChargeback base(MemberClearingTransaction tx,ChargebackRequest r,int cycle,String tc){
        MemberChargeback cb=new MemberChargeback(); cb.setBankMemberId(memberId);
        cb.setClearingTransactionId(tx.getId()); cb.setTransactionCode(tc); cb.setCycleNumber(cycle);
        cb.setReasonCode(r.reasonCode()); cb.setChargebackReference("%06d".formatted((chargebacks.count()+1)%1_000_000));
        cb.setAmount(r.amount()); cb.setCurrency(r.currency()); cb.setCounterpartyMember(r.counterpartyMember());
        cb.setCreatedBy(r.createdBy()); cb.setManualReason(r.manualReason()); cb.setDueDate(LocalDate.now().plusDays(7));
        return cb;
    }
    private static String chargebackCode(String type){return switch(type){case"CASH_WITHDRAWAL"->"17";case"CASH_ADVANCE"->"16";default->"15";};}
    private static String presentationCode(String type){return switch(type){case"CASH_WITHDRAWAL"->"07";case"CASH_ADVANCE"->"06";default->"05";};}
    private static ChargebackResult result(MemberChargeback c){return new ChargebackResult(c.getId(),
            c.getClearingTransactionId(),c.getParentChargebackId(),c.getDirection().name(),c.getStatus().name(),
            c.getTransactionCode(),c.getCycleNumber(),c.getReasonCode(),c.getChargebackReference(),c.getAmount(),c.getCurrency());}
}
