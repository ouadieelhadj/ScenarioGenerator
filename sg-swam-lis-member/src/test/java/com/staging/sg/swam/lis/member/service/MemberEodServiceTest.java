package com.staging.sg.swam.lis.member.service;

import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.repository.SwamAcqTransactionRepository;
import com.staging.sg.swam.lis.common.model.EodBatchResult;
import com.staging.sg.swam.lis.member.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberEodServiceTest {
    @Test
    void createsClearingTransactionAndSkipsAlreadyExtractedSource() {
        SwamAcqTransactionRepository source = mock(SwamAcqTransactionRepository.class);
        MemberBusinessDayRepository days = mock(MemberBusinessDayRepository.class);
        MemberBatchExecutionRepository batches = mock(MemberBatchExecutionRepository.class);
        MemberClearingTransactionRepository clearing = mock(MemberClearingTransactionRepository.class);
        when(days.findByBankMemberIdAndBusinessDate(any(), any())).thenReturn(Optional.empty());
        when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clearing.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SwamAcqTransaction first = source(1L, "620414260701");
        SwamAcqTransaction duplicate = source(2L, "620414260702");
        when(source.findByClearingEligibleTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of(first, duplicate));
        when(clearing.existsByLocalSourceTypeAndLocalSidTransactionIdAndClearingCycle("SWAM_ACQ", 1L, 1))
                .thenReturn(false);
        when(clearing.existsByLocalSourceTypeAndLocalSidTransactionIdAndClearingCycle("SWAM_ACQ", 2L, 1))
                .thenReturn(true);

        MemberEodService service = new MemberEodService(source, days, batches, clearing,
                "BANK01", "0123456789abcdef-test");
        EodBatchResult result = service.execute(LocalDate.of(2026, 7, 23), "TEST");

        assertThat(result.readCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(clearing, times(1)).save(any());
    }

    private SwamAcqTransaction source(Long id, String rrn) {
        SwamAcqTransaction transaction = new SwamAcqTransaction();
        transaction.setId(id);
        transaction.setPan("4000001234567899");
        transaction.setStan("123456");
        transaction.setRrn(rrn);
        transaction.setAuthorizationCode("654321");
        transaction.setProcessingCode("000000");
        transaction.setMerchantCategoryCode("5999");
        transaction.setLocalTransactionDt("260723145135");
        transaction.setCreatedAt(LocalDateTime.of(2026, 7, 23, 14, 51));
        transaction.setClearingAmount(1000L);
        transaction.setCurrency("504");
        return transaction;
    }
}
