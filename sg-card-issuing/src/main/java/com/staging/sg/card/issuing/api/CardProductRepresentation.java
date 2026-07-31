package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.CardProductStatus;
import com.staging.sg.card.issuing.domain.CardType;

import java.util.UUID;

public record CardProductRepresentation(
        UUID id,
        String issuerId,
        String productCode,
        int productVersion,
        CardType cardType,
        String currency,
        CardProductStatus status,
        boolean purchaseEnabled,
        boolean cashEnabled,
        boolean ecommerceEnabled,
        boolean idempotentReplay) {

    public static CardProductRepresentation from(
            CardProduct product, boolean idempotentReplay) {
        return new CardProductRepresentation(
                product.id(), product.issuerId(), product.productCode(),
                product.productVersion(), product.cardType(), product.currency(),
                product.status(), product.purchaseEnabled(), product.cashEnabled(),
                product.ecommerceEnabled(), idempotentReplay);
    }
}
