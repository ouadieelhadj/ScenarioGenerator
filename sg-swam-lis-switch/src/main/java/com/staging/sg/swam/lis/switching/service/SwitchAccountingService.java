package com.staging.sg.swam.lis.switching.service;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.switching.persistence.*;
import com.staging.sg.swam.lis.switching.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
@Service
public class SwitchAccountingService {
 private final SwitchBusinessDayRepository days;private final SwitchClearingTransactionRepository txs;
 private final SwitchAccountingEntryRepository entries;private final String settlementAccount,controlAccount;
 public SwitchAccountingService(SwitchBusinessDayRepository d,SwitchClearingTransactionRepository t,
  SwitchAccountingEntryRepository e,@Value("${swam.lis.accounting.settlement-account}")String s,
  @Value("${swam.lis.accounting.control-account}")String c){days=d;txs=t;entries=e;settlementAccount=s;controlAccount=c;}
 @Transactional public AccountingBatchResult post(LocalDate date){
  SwitchBusinessDay day=days.findByBankMemberIdAndBusinessDate("SWITCH",date).orElseThrow();
  var ready=txs.findByBusinessDayIdAndAccountingStatusOrderById(day.getId(),AccountingStatus.READY);long total=0,count=0;
  for(SwitchClearingTransaction tx:ready){if(tx.getIncomingLisFileId()==null)
   throw new IllegalStateException("Accounting without incoming LIS is forbidden");
   long amount=tx.getSettlementAmount()==null?tx.getTransactionAmount():tx.getSettlementAmount();
   String currency=tx.getSettlementCurrency()==null?tx.getTransactionCurrency():tx.getSettlementCurrency();
   String base="SWITCH:"+tx.getId()+":"+tx.getIncomingLisFileId();
   if(!entries.existsByEntryKey(base+":D")){entries.save(entry(day,tx,base+":D",controlAccount,amount,0,currency,date));count++;}
   if(!entries.existsByEntryKey(base+":C")){entries.save(entry(day,tx,base+":C",settlementAccount,0,amount,currency,date));count++;}
   tx.setAccountingStatus(AccountingStatus.ACCOUNTED);tx.setUpdatedAt(java.time.LocalDateTime.now());txs.save(tx);total+=amount;}
  return new AccountingBatchResult(date,ready.size(),count,total,total,"BALANCED");}
 private static SwitchAccountingEntry entry(SwitchBusinessDay day,SwitchClearingTransaction tx,String key,
  String account,long debit,long credit,String currency,LocalDate date){SwitchAccountingEntry e=new SwitchAccountingEntry();
  e.setBusinessDayId(day.getId());e.setClearingTransactionId(tx.getId());e.setLisFileId(tx.getIncomingLisFileId());
  e.setEntryKey(key);e.setAccountCode(account);e.setDebit(debit);e.setCredit(credit);e.setCurrency(currency);e.setPostingDate(date);return e;}
}
