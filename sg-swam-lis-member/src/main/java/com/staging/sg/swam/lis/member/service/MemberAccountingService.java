package com.staging.sg.swam.lis.member.service;
import com.staging.sg.swam.lis.common.model.*;
import com.staging.sg.swam.lis.member.persistence.*;
import com.staging.sg.swam.lis.member.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
@Service
public class MemberAccountingService {
 private final MemberBusinessDayRepository days; private final MemberClearingTransactionRepository txs;
 private final MemberAccountingEntryRepository entries; private final String memberId,settlementAccount,controlAccount;
 public MemberAccountingService(MemberBusinessDayRepository d,MemberClearingTransactionRepository t,
  MemberAccountingEntryRepository e,@Value("${swam.lis.bank-member-id}")String m,
  @Value("${swam.lis.accounting.settlement-account}")String s,
  @Value("${swam.lis.accounting.control-account}")String c){days=d;txs=t;entries=e;memberId=m;settlementAccount=s;controlAccount=c;}
 @Transactional public AccountingBatchResult post(LocalDate date){
  MemberBusinessDay day=days.findByBankMemberIdAndBusinessDate(memberId,date).orElseThrow();
  var ready=txs.findByBusinessDayIdAndAccountingStatusOrderById(day.getId(),AccountingStatus.READY);
  long total=0,count=0;
  for(MemberClearingTransaction tx:ready){
   if(tx.getIncomingLisFileId()==null)throw new IllegalStateException("Accounting without incoming LIS is forbidden");
   long amount=tx.getSettlementAmount()==null?tx.getTransactionAmount():tx.getSettlementAmount();
   String currency=tx.getSettlementCurrency()==null?tx.getTransactionCurrency():tx.getSettlementCurrency();
   String base="MEMBER:"+tx.getId()+":"+tx.getIncomingLisFileId();
   if(!entries.existsByEntryKey(base+":D")){entries.save(entry(day,tx,base+":D",settlementAccount,amount,0,currency,date));count++;}
   if(!entries.existsByEntryKey(base+":C")){entries.save(entry(day,tx,base+":C",controlAccount,0,amount,currency,date));count++;}
   tx.setAccountingStatus(AccountingStatus.ACCOUNTED);tx.setUpdatedAt(java.time.LocalDateTime.now());txs.save(tx);total+=amount;
  }
  return new AccountingBatchResult(date,ready.size(),count,total,total,"BALANCED");
 }
 private static MemberAccountingEntry entry(MemberBusinessDay day,MemberClearingTransaction tx,String key,
  String account,long debit,long credit,String currency,LocalDate date){MemberAccountingEntry e=new MemberAccountingEntry();
  e.setBusinessDayId(day.getId());e.setClearingTransactionId(tx.getId());e.setLisFileId(tx.getIncomingLisFileId());
  e.setEntryKey(key);e.setAccountCode(account);e.setDebit(debit);e.setCredit(credit);e.setCurrency(currency);e.setPostingDate(date);return e;}
}
