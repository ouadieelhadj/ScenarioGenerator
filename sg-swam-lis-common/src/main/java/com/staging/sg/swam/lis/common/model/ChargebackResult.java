package com.staging.sg.swam.lis.common.model;
public record ChargebackResult(Long id, Long clearingTransactionId, Long parentChargebackId,
        String direction, String status, String transactionCode, int cycleNumber,
        String reasonCode, String reference, long amount, String currency) {}
