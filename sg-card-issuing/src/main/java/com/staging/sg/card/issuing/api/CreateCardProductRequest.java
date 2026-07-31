package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.domain.CardType;

public record CreateCardProductRequest(
        String issuerId,
        String productCode,
        int productVersion,
        CardType cardType,
        String currency,
        boolean purchaseEnabled,
        boolean cashEnabled,
        boolean ecommerceEnabled) {
}
