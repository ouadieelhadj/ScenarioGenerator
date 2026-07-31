package com.staging.sg.card.issuing.api;

import java.util.UUID;

public record CreateCardContractRequest(
        String issuerId,
        String externalReference,
        String customerId,
        String cardholderId,
        String fundingContractId,
        UUID productId) {
}
