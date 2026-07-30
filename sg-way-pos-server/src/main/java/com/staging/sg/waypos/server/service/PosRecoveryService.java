package com.staging.sg.waypos.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.domain.PosOutbox;
import com.staging.sg.waypos.server.repository.PosOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PosRecoveryService {
    private final PosOutboxRepository outbox;
    private final WayPosPayloadCipher cipher;
    private final ObjectMapper json;

    public PosRecoveryService(
            PosOutboxRepository outbox, WayPosPayloadCipher cipher,
            ObjectMapper json) {
        this.outbox = outbox;
        this.cipher = cipher;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleIfNeeded(
            RoutingTransactionRequest request,
            RoutingTransactionResponse response, String destination) {
        if (!response.retryable() && !"UNKNOWN".equals(response.status())) return;
        boolean repeat = java.util.Set.of("ADVICE", "REVERSAL", "CAPTURE")
                .contains(request.operation());
        String type = repeat ? "REPEAT" : "AUTOMATIC_REVERSAL";
        if (outbox.existsByTransactionIdAndMessageTypeAndDestination(
                request.transactionId(), type, destination)) {
            return;
        }
        RoutingTransactionRequest recovery = repeat
                ? repeat(request) : automaticReversal(request);
        try {
            WayPosPayloadCipher.Encrypted encrypted =
                    cipher.encrypt(json.writeValueAsString(recovery));
            outbox.save(PosOutbox.pending(
                    request.transactionId(), type, destination,
                    encrypted.ciphertext(), encrypted.iv(), encrypted.keyId()));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist POS recovery", e);
        }
    }

    private static RoutingTransactionRequest repeat(
            RoutingTransactionRequest request) {
        Map<String, String> attributes = new HashMap<>(
                request.attributes() == null ? Map.of() : request.attributes());
        attributes.put("recoveryMode", "REPEAT");
        return copy(request, repeatedMti(request.sourceMti()),
                request.transactionId(), request.idempotencyKey(),
                request.operation(), request.originalTransactionId(), attributes,
                request.pinBlockHex());
    }

    private static RoutingTransactionRequest automaticReversal(
            RoutingTransactionRequest request) {
        Map<String, String> attributes = new HashMap<>(
                request.attributes() == null ? Map.of() : request.attributes());
        attributes.put("operationName", "UNIVERSAL_REVERSAL");
        attributes.put("networkId", "402");
        attributes.put("automatic", "true");
        attributes.put("originalStan", request.stan());
        put(attributes, "originalTransmissionDateTime",
                attributes.get("transmissionDateTime"));
        String transactionId = UUID.nameUUIDFromBytes(
                ("auto-reversal:" + request.transactionId())
                        .getBytes(StandardCharsets.UTF_8)).toString();
        String idempotency = UUID.nameUUIDFromBytes(
                ("auto-reversal:" + request.idempotencyKey())
                        .getBytes(StandardCharsets.UTF_8)).toString();
        return copy(request, "0420", transactionId, idempotency,
                "REVERSAL", request.transactionId(), attributes, null);
    }

    private static RoutingTransactionRequest copy(
            RoutingTransactionRequest source, String mti,
            String transactionId, String idempotencyKey, String operation,
            String originalTransactionId, Map<String, String> attributes,
            String pinBlockHex) {
        return new RoutingTransactionRequest(
                source.schemaVersion(), transactionId, source.correlationId(),
                idempotencyKey, operation, mti, source.processingCode(),
                source.pan(), source.expiry(), source.amount(), source.currency(),
                source.stan(), source.rrn(), source.terminalId(),
                source.merchantId(), pinBlockHex, source.emvDataHex(),
                originalTransactionId, Map.copyOf(attributes));
    }

    private static String repeatedMti(String mti) {
        if (mti == null || mti.length() != 4) return mti;
        char[] value = mti.toCharArray();
        value[3] = '1';
        return new String(value);
    }

    private static void put(
            Map<String, String> values, String key, String value) {
        if (value != null) values.put(key, value);
    }
}
