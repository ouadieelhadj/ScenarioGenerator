package com.staging.sg.swam.lis.switching.service;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.switching.persistence.*;
import com.staging.sg.swam.lis.switching.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class SwitchChargebackService {
    private final SwitchChargebackRepository chargebacks; private final SwitchClearingTransactionRepository transactions;
    private final String switchId;
    public SwitchChargebackService(SwitchChargebackRepository c,SwitchClearingTransactionRepository t,
            @Value("${swam.lis.switch-member-id}")String switchId){chargebacks=c;transactions=t;this.switchId=switchId;}
    @Transactional public ChargebackResult emit(ChargebackRequest r){
        SwitchClearingTransaction tx=transactions.findById(r.clearingTransactionId())
                .orElseThrow(()->new IllegalArgumentException("Clearing transaction not found"));
        if(tx.getIncomingLisFileId()==null)throw new IllegalStateException("A chargeback requires an incoming LIS presentation");
        if(chargebacks.existsByClearingTransactionIdAndDirectionAndCycleNumberAndReasonCode(
                tx.getId(),ChargebackDirection.EMITTED,1,r.reasonCode()))throw new IllegalStateException("Duplicate first chargeback");
        SwitchChargeback cb=base(tx,r,1,chargebackCode(tx.getTransactionType()));
        cb.setDirection(ChargebackDirection.EMITTED);cb.setStatus(ChargebackStatus.READY_TO_SEND);
        cb.setEmittedAt(LocalDateTime.now());cb=chargebacks.save(cb);tx.setDisputeStatus(DisputeStatus.OPEN);
        tx.setUpdatedAt(LocalDateTime.now());transactions.save(tx);return result(cb);
    }
    @Transactional public ChargebackResult represent(Long id,RepresentationRequest r){
        SwitchChargeback parent=chargebacks.findById(id).orElseThrow(()->new IllegalArgumentException("Chargeback not found"));
        if(parent.getDirection()!=ChargebackDirection.RECEIVED||!(parent.getStatus()==ChargebackStatus.RECEIVED
                ||parent.getStatus()==ChargebackStatus.UNDER_REVIEW))
            throw new IllegalStateException("Only a received open chargeback can be represented");
        SwitchClearingTransaction tx=transactions.findById(parent.getClearingTransactionId()).orElseThrow();
        ChargebackRequest values=new ChargebackRequest(tx.getId(),parent.getReasonCode(),parent.getAmount(),
                parent.getCurrency(),parent.getCounterpartyMember(),r.createdBy(),r.justification());
        SwitchChargeback rep=base(tx,values,2,presentationCode(tx.getTransactionType()));
        rep.setParentChargebackId(parent.getId());rep.setDirection(ChargebackDirection.EMITTED);
        rep.setStatus(ChargebackStatus.READY_TO_SEND);rep=chargebacks.save(rep);
        parent.setStatus(ChargebackStatus.REPRESENTED);parent.setUpdatedAt(LocalDateTime.now());chargebacks.save(parent);
        return result(rep);
    }
    private SwitchChargeback base(SwitchClearingTransaction tx,ChargebackRequest r,int cycle,String tc){
        SwitchChargeback cb=new SwitchChargeback();cb.setBankMemberId(switchId);cb.setClearingTransactionId(tx.getId());
        cb.setTransactionCode(tc);cb.setCycleNumber(cycle);cb.setReasonCode(r.reasonCode());
        cb.setChargebackReference("%06d".formatted((chargebacks.count()+1)%1_000_000));cb.setAmount(r.amount());
        cb.setCurrency(r.currency());cb.setCounterpartyMember(r.counterpartyMember());cb.setCreatedBy(r.createdBy());
        cb.setManualReason(r.manualReason());cb.setDueDate(LocalDate.now().plusDays(7));return cb;
    }
    private static String chargebackCode(String t){return switch(t){case"CASH_WITHDRAWAL"->"17";case"CASH_ADVANCE"->"16";default->"15";};}
    private static String presentationCode(String t){return switch(t){case"CASH_WITHDRAWAL"->"07";case"CASH_ADVANCE"->"06";default->"05";};}
    private static ChargebackResult result(SwitchChargeback c){return new ChargebackResult(c.getId(),c.getClearingTransactionId(),
            c.getParentChargebackId(),c.getDirection().name(),c.getStatus().name(),c.getTransactionCode(),c.getCycleNumber(),
            c.getReasonCode(),c.getChargebackReference(),c.getAmount(),c.getCurrency());}
}
