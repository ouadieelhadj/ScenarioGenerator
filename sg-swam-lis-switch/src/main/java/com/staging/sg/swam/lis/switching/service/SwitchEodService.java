package com.staging.sg.swam.lis.switching.service;

import com.staging.sg.common.entity.SwamIssTransaction;
import com.staging.sg.common.repository.SwamIssTransactionRepository;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.service.ClearingIdentityService;
import com.staging.sg.swam.lis.common.service.SidClearingRules;
import com.staging.sg.swam.lis.switching.persistence.SwitchBatchExecution;
import com.staging.sg.swam.lis.switching.persistence.SwitchBusinessDay;
import com.staging.sg.swam.lis.switching.persistence.SwitchClearingTransaction;
import com.staging.sg.swam.lis.switching.repository.SwitchBatchExecutionRepository;
import com.staging.sg.swam.lis.switching.repository.SwitchBusinessDayRepository;
import com.staging.sg.swam.lis.switching.repository.SwitchClearingTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SwitchEodService {
    private static final String SOURCE_TYPE = "SWAM_ISS";

    private final SwamIssTransactionRepository sourceRepository;
    private final SwitchBusinessDayRepository businessDayRepository;
    private final SwitchBatchExecutionRepository batchRepository;
    private final SwitchClearingTransactionRepository clearingRepository;
    private final String defaultMemberId;
    private final ClearingIdentityService identityService;

    public SwitchEodService(
            SwamIssTransactionRepository sourceRepository,
            SwitchBusinessDayRepository businessDayRepository,
            SwitchBatchExecutionRepository batchRepository,
            SwitchClearingTransactionRepository clearingRepository,
            @Value("${swam.lis.default-member-id}") String defaultMemberId,
            @Value("${swam.lis.pan-fingerprint-salt}") String fingerprintSalt) {
        this.sourceRepository = sourceRepository;
        this.businessDayRepository = businessDayRepository;
        this.batchRepository = batchRepository;
        this.clearingRepository = clearingRepository;
        this.defaultMemberId = defaultMemberId;
        this.identityService = new ClearingIdentityService(fingerprintSalt);
    }

    @Transactional
    public EodBatchResult execute(LocalDate businessDate, String requestedBy) {
        SwitchBusinessDay day = businessDayRepository
                .findByBankMemberIdAndBusinessDate("SWITCH", businessDate)
                .orElseGet(() -> createBusinessDay(businessDate));
        day.setStatus(BusinessDayStatus.CLOSING);
        businessDayRepository.save(day);

        SwitchBatchExecution batch = new SwitchBatchExecution();
        batch.setBusinessDayId(day.getId());
        batch.setBatchType("EOD_EXTRACTION");
        batch.setStatus(BatchStatus.RUNNING);
        batch.setStartedAt(LocalDateTime.now());
        batch.setRequestedBy(requestedBy);
        batch = batchRepository.save(batch);

        List<SwamIssTransaction> sources = sourceRepository
                .findByClearingEligibleTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        businessDate.atStartOfDay(), businessDate.plusDays(1).atStartOfDay());
        long created = 0;
        long skipped = 0;
        for (SwamIssTransaction source : sources) {
            if (clearingRepository.existsByLocalSourceTypeAndLocalSidTransactionIdAndClearingCycle(
                    SOURCE_TYPE, source.getId(), 1)) {
                skipped++;
                continue;
            }
            clearingRepository.save(toClearing(day, source));
            created++;
        }

        batch.setReadCount(sources.size());
        batch.setWriteCount(created);
        batch.setSkipCount(skipped);
        batch.setStatus(BatchStatus.COMPLETED);
        batch.setCompletedAt(LocalDateTime.now());
        batchRepository.save(batch);
        day.setStatus(BusinessDayStatus.CLOSED);
        day.setClosedAt(LocalDateTime.now());
        day.setUpdatedAt(LocalDateTime.now());
        businessDayRepository.save(day);
        return new EodBatchResult(batch.getCorrelationId(), businessDate,
                sources.size(), created, skipped, batch.getStatus().name());
    }

    private SwitchBusinessDay createBusinessDay(LocalDate date) {
        SwitchBusinessDay day = new SwitchBusinessDay();
        day.setBankMemberId("SWITCH");
        day.setBusinessDate(date);
        return businessDayRepository.save(day);
    }

    private SwitchClearingTransaction toClearing(SwitchBusinessDay day, SwamIssTransaction source) {
        String memberId = memberId(source);
        long clearingAmount = source.getClearingAmount() == null
                ? source.getAmount() : source.getClearingAmount();
        SwitchClearingTransaction target = new SwitchClearingTransaction();
        target.setBankMemberId(memberId);
        target.setBusinessDayId(day.getId());
        target.setLocalSourceType(SOURCE_TYPE);
        target.setLocalSidTransactionId(source.getId());
        target.setTransactionType(SidClearingRules.transactionType(
                source.getProcessingCode(), source.getMerchantCategoryCode()));
        target.setPanFingerprint(identityService.panFingerprint(source.getPan()));
        target.setMaskedPan(identityService.maskPan(source.getPan()));
        target.setRrn(source.getRrn());
        target.setStan(source.getStan());
        target.setAuthorizationCode(source.getAuthorizationCode());
        LocalDateTime transactionAt = SidClearingRules.transactionAt(
                source.getLocalTransactionDt(), source.getCreatedAt());
        target.setTransactionAt(transactionAt);
        target.setProcessingDate(day.getBusinessDate());
        target.setProcessingCode(source.getProcessingCode());
        target.setMcc(source.getMerchantCategoryCode());
        target.setPosDataCode(source.getPosDataCode());
        target.setTerminalId(source.getTerminalId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantName(SidClearingRules.merchantName(source.getMerchantNameLocation()));
        target.setMerchantCity(SidClearingRules.merchantCity(source.getMerchantNameLocation()));
        target.setTransactionAmount(clearingAmount);
        target.setTransactionCurrency(source.getCurrency());
        target.setBillingAmount(source.getBillingAmount());
        target.setBillingCurrency(source.getBillingCurrency());
        target.setSettlementAmount(source.getSettlementAmount());
        target.setSettlementCurrency(source.getSettlementCurrency());
        target.setSourcePresence(SourcePresence.LOCAL_ONLY);
        target.setMatchStatus(MatchStatus.AUTH_ONLY_SUSPECT);
        target.setAccountingStatus(AccountingStatus.NOT_ELIGIBLE);
        target.setFunctionalKey(identityService.functionalKey(memberId,
                source.getRrn(), source.getStan(), source.getAuthorizationCode(),
                ClearingIdentityService.canonicalTransactionDate(transactionAt),
                clearingAmount, source.getCurrency()));
        return target;
    }

    private String memberId(SwamIssTransaction source) {
        // This deployment represents the clearing perimeter of one bank.
        // DE32/DE33 remain transaction attributes; they are not tenant identifiers.
        return defaultMemberId;
    }
}
