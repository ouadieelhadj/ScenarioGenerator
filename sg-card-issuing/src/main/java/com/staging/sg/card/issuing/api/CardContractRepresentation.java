package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.common.contract.PaymentContractStatus;

import java.util.UUID;

public record CardContractRepresentation(
        UUID id,
        String issuerId,
        String externalReference,
        String customerId,
        String cardholderId,
        String fundingContractId,
        UUID productId,
        PaymentContractStatus status,
        boolean idempotentReplay) {

    public static CardContractRepresentation from(
            CardContract contract, boolean idempotentReplay) {
        return new CardContractRepresentation(
                contract.id(), contract.issuerId(), contract.externalReference(),
                contract.customerId(), contract.cardholderId(),
                contract.fundingContractId(), contract.productId(),
                contract.status(), idempotentReplay);
    }
}
