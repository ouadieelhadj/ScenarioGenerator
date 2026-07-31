package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.domain.CardInstrument;
import com.staging.sg.card.issuing.domain.CardInstrumentStatus;
import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.common.issuing.PaymentIdentifierType;

import java.util.UUID;

public record CardInstrumentRepresentation(
        UUID id,
        String issuerId,
        UUID contractId,
        String maskedPan,
        String expiryYymm,
        CardInstrumentStatus status,
        UUID paymentIdentifierId,
        PaymentIdentifierType paymentIdentifierType,
        boolean idempotentReplay) {

    public static CardInstrumentRepresentation from(
            CardInstrument instrument, PaymentIdentifier identifier,
            boolean idempotentReplay) {
        return new CardInstrumentRepresentation(
                instrument.id(), instrument.issuerId(), instrument.contractId(),
                instrument.maskedPan(), instrument.expiryYymm(),
                instrument.status(), identifier.id(), identifier.identifierType(),
                idempotentReplay);
    }
}
