package com.staging.sg.swam.lis.common.model;
import java.time.LocalDate;
public record AccountingBatchResult(LocalDate businessDate,long transactions,long entries,
        long totalDebit,long totalCredit,String status){}
