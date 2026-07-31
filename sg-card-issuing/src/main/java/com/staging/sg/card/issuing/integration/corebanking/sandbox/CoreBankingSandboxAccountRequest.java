package com.staging.sg.card.issuing.integration.corebanking.sandbox;

public record CoreBankingSandboxAccountRequest(
        String issuerId,
        String currency,
        long availableBalanceMinor,
        String status) {
}
