package com.staging.sg.swam.lis.member.service;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.member.persistence.*;
import com.staging.sg.swam.lis.member.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MemberAccountingServiceTest {
 @Test void postsBalancedIdempotentPairOnlyForReadyLisTransaction(){
  var days=mock(MemberBusinessDayRepository.class);var txs=mock(MemberClearingTransactionRepository.class);
  var entries=mock(MemberAccountingEntryRepository.class);var day=mock(MemberBusinessDay.class);
  var tx=mock(MemberClearingTransaction.class);LocalDate date=LocalDate.of(2026,7,26);
  when(day.getId()).thenReturn(1L);when(days.findByBankMemberIdAndBusinessDate("TESTGRP01",date))
    .thenReturn(Optional.of(day));when(txs.findByBusinessDayIdAndAccountingStatusOrderById(1L,AccountingStatus.READY))
    .thenReturn(List.of(tx));when(tx.getId()).thenReturn(7L);when(tx.getIncomingLisFileId()).thenReturn(9L);
  when(tx.getSettlementAmount()).thenReturn(1250L);when(tx.getSettlementCurrency()).thenReturn("504");
  var service=new MemberAccountingService(days,txs,entries,"TESTGRP01","SETTLEMENT","CONTROL");
  AccountingBatchResult result=service.post(date);
  assertEquals(2,result.entries());assertEquals(result.totalDebit(),result.totalCredit());
  assertEquals(1250,result.totalDebit());assertEquals("BALANCED",result.status());
  verify(entries,times(2)).save(any(MemberAccountingEntry.class));
  verify(tx).setAccountingStatus(AccountingStatus.ACCOUNTED);
 }
 @Test void rejectsAccountingWithoutIncomingLis(){
  var days=mock(MemberBusinessDayRepository.class);var txs=mock(MemberClearingTransactionRepository.class);
  var entries=mock(MemberAccountingEntryRepository.class);var day=mock(MemberBusinessDay.class);
  var tx=mock(MemberClearingTransaction.class);LocalDate date=LocalDate.of(2026,7,26);
  when(day.getId()).thenReturn(1L);when(days.findByBankMemberIdAndBusinessDate(anyString(),eq(date)))
    .thenReturn(Optional.of(day));when(txs.findByBusinessDayIdAndAccountingStatusOrderById(1L,AccountingStatus.READY))
    .thenReturn(List.of(tx));when(tx.getIncomingLisFileId()).thenReturn(null);
  var service=new MemberAccountingService(days,txs,entries,"TESTGRP01","SETTLEMENT","CONTROL");
  assertThrows(IllegalStateException.class,()->service.post(date));
  verify(entries,never()).save(any());
 }
}
