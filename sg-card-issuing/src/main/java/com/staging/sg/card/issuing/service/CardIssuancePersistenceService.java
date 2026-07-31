package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CardInstrumentRepresentation;
import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.CardContractStatus;
import com.staging.sg.card.issuing.domain.CardInstrument;
import com.staging.sg.card.issuing.domain.OutboxEvent;
import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.port.ProtectedPan;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardInstrumentRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CardIssuancePersistenceService {
    private final CardContractRepository contracts;
    private final CardInstrumentRepository instruments;
    private final PaymentIdentifierRepository identifiers;
    private final OutboxEventRepository outbox;

    public CardIssuancePersistenceService(
            CardContractRepository contracts,
            CardInstrumentRepository instruments,
            PaymentIdentifierRepository identifiers,
            OutboxEventRepository outbox) {
        this.contracts = contracts;
        this.instruments = instruments;
        this.identifiers = identifiers;
        this.outbox = outbox;
    }

    @Transactional
    public CardInstrumentRepresentation persist(
            UUID contractId, String issuerId, String callerId,
            String idempotencyKey, String correlationId,
            String fingerprint, ProtectedPan protectedPan) {
        var existing = instruments
                .findByIssuerIdAndIssuedByAndIssuanceIdempotencyKey(
                        issuerId, callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().issuanceMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used for another card issuance");
            }
            PaymentIdentifier identifier = identifiers
                    .findByInstrumentId(existing.get().id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment identifier missing for existing instrument"));
            return CardInstrumentRepresentation.from(
                    existing.get(), identifier, true);
        }
        CardContract contract = ownedActiveContract(contractId, issuerId);
        CardInstrument instrument = CardInstrument.inactive(
                issuerId, contract.id(), protectedPan.vaultReference(),
                protectedPan.maskedPan(), protectedPan.expiryYymm(),
                callerId, idempotencyKey, fingerprint);
        instruments.save(instrument);
        PaymentIdentifier identifier = PaymentIdentifier.activePan(
                issuerId, instrument.id(), protectedPan.vaultReference(),
                protectedPan.maskedPan());
        identifiers.save(identifier);
        String payload = "{\"issuerId\":\"" + safe(issuerId)
                + "\",\"contractId\":\"" + contract.id()
                + "\",\"instrumentId\":\"" + instrument.id()
                + "\",\"maskedPan\":\"" + safe(instrument.maskedPan())
                + "\",\"status\":\"" + instrument.status() + "\"}";
        outbox.save(OutboxEvent.pending(
                "CardInstrument", instrument.id().toString(), "CardRequested",
                correlationId, payload));
        return CardInstrumentRepresentation.from(instrument, identifier, false);
    }

    private CardContract ownedActiveContract(UUID id, String issuerId) {
        CardContract contract = contracts.findById(id)
                .filter(value -> value.issuerId().equals(issuerId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown card contract"));
        if (contract.status() != CardContractStatus.ACTIVE) {
            throw new IllegalStateException(
                    "A card can only be issued from an active contract");
        }
        return contract;
    }

    private static String safe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
