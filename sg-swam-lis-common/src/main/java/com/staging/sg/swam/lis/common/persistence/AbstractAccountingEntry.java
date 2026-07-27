package com.staging.sg.swam.lis.common.persistence;
import jakarta.persistence.*;
import java.time.*;
@MappedSuperclass
public abstract class AbstractAccountingEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="business_day_id",nullable=false) private Long businessDayId;
 @Column(name="clearing_transaction_id",nullable=false) private Long clearingTransactionId;
 @Column(name="lis_file_id",nullable=false) private Long lisFileId;
 @Column(name="entry_key",nullable=false,length=96,unique=true) private String entryKey;
 @Column(name="account_code",nullable=false,length=40) private String accountCode;
 @Column(nullable=false) private long debit;
 @Column(nullable=false) private long credit;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="posting_date",nullable=false) private LocalDate postingDate;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public Long getId(){return id;} public Long getBusinessDayId(){return businessDayId;}
 public void setBusinessDayId(Long v){businessDayId=v;} public Long getClearingTransactionId(){return clearingTransactionId;}
 public void setClearingTransactionId(Long v){clearingTransactionId=v;} public Long getLisFileId(){return lisFileId;}
 public void setLisFileId(Long v){lisFileId=v;} public String getEntryKey(){return entryKey;}
 public void setEntryKey(String v){entryKey=v;} public String getAccountCode(){return accountCode;}
 public void setAccountCode(String v){accountCode=v;} public long getDebit(){return debit;}
 public void setDebit(long v){debit=v;} public long getCredit(){return credit;} public void setCredit(long v){credit=v;}
 public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
 public LocalDate getPostingDate(){return postingDate;} public void setPostingDate(LocalDate v){postingDate=v;}
}
