package com.staging.sg.swam.lis.member.service;

import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.repository.SwamAcqTransactionRepository;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.common.service.ClearingIdentityService;
import com.staging.sg.swam.lis.common.service.SidClearingRules;
import com.staging.sg.swam.lis.member.persistence.MemberBatchExecution;
import com.staging.sg.swam.lis.member.persistence.MemberBusinessDay;
import com.staging.sg.swam.lis.member.persistence.MemberClearingTransaction;
import com.staging.sg.swam.lis.member.repository.MemberBatchExecutionRepository;
import com.staging.sg.swam.lis.member.repository.MemberBusinessDayRepository;
import com.staging.sg.swam.lis.member.repository.MemberClearingTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemberEodService {
    private static final String SOURCE_TYPE = "SWAM_ACQ";

    private final SwamAcqTransactionRepository sourceRepository;
    private final MemberBusinessDayRepository businessDayRepository;
    private final MemberBatchExecutionRepository batchRepository;
    private final MemberClearingTransactionRepository clearingRepository;
    private final String bankMemberId;
    private final ClearingIdentityService identityService;

    public MemberEodService(
            SwamAcqTransactionRepository sourceRepository,
            MemberBusinessDayRepository businessDayRepository,
            MemberBatchExecutionRepository batchRepository,
            MemberClearingTransactionRepository clearingRepository,
            @Value("${swam.lis.bank-member-id}") String bankMemberId,
            @Value("${swam.lis.pan-fingerprint-salt}") String fingerprintSalt) {
        this.sourceRepository = sourceRepository;
        this.businessDayRepository = businessDayRepository;
        this.batchRepository = batchRepository;
        this.clearingRepository = clearingRepository;
        this.bankMemberId = bankMemberId;
        this.identityService = new ClearingIdentityService(fingerprintSalt);
    }

    @Transactional
    public EodBatchResult execute(LocalDate businessDate, String requestedBy) {
        MemberBusinessDay day = businessDayRepository
                .findByBankMemberIdAndBusinessDate(bankMemberId, businessDate)
                .orElseGet(() -> createBusinessDay(businessDate));
        day.setStatus(BusinessDayStatus.CLOSING);
        businessDayRepository.save(day);

        MemberBatchExecution batch = new MemberBatchExecution();
        batch.setBusinessDayId(day.getId());
        batch.setBatchType("EOD_EXTRACTION");
        batch.setStatus(BatchStatus.RUNNING);
        batch.setStartedAt(LocalDateTime.now());
        batch.setRequestedBy(requestedBy);
        batch = batchRepository.save(batch);

        List<SwamAcqTransaction> sources = sourceRepository
                .findByClearingEligibleTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        businessDate.atStartOfDay(), businessDate.plusDays(1).atStartOfDay());
        long created = 0;
        long skipped = 0;
        for (SwamAcqTransaction source : sources) {
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

    private MemberBusinessDay createBusinessDay(LocalDate date) {
        MemberBusinessDay day = new MemberBusinessDay();
        day.setBankMemberId(bankMemberId);
        day.setBusinessDate(date);
        return businessDayRepository.save(day);
    }

    private MemberClearingTransaction toClearing(MemberBusinessDay day, SwamAcqTransaction source) {
        long clearingAmount = source.getClearingAmount() == null
                ? source.getAmount() : source.getClearingAmount();
        MemberClearingTransaction target = new MemberClearingTransaction();
        target.setBankMemberId(bankMemberId);
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
        target.setFunctionalKey(identityService.functionalKey(bankMemberId,
                source.getRrn(), source.getStan(), source.getAuthorizationCode(),
                ClearingIdentityService.canonicalTransactionDate(transactionAt),
                clearingAmount, source.getCurrency()));
        return target;
    }
}
