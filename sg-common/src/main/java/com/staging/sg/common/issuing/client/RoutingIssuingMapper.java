package com.staging.sg.common.issuing.client;

import com.staging.sg.common.issuing.*;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;

import java.util.Map;

public final class RoutingIssuingMapper {
    private RoutingIssuingMapper() {}

    public static IssuingAuthorizationRequest request(
            RoutingTransactionRequest source, String callerId) {
        Map<String, String> attributes = source.attributes() == null
                ? Map.of() : source.attributes();
        return new IssuingAuthorizationRequest(
                "1.0", attributes.get("issuerId"), callerId,
                source.transactionId(), source.correlationId(),
                source.idempotencyKey(), operation(source),
                source.originalTransactionId(), PaymentIdentifierType.PAN,
                source.pan(), parseAmount(source.amount()), source.currency(),
                attributes.get("localTransactionDateTime"),
                source.terminalId(), source.merchantId(),
                attributes.get("merchantCategoryCode"),
                attributes.get("countryCode"),
                Boolean.parseBoolean(attributes.getOrDefault(
                        "cardPresent", "true")),
                Boolean.parseBoolean(attributes.getOrDefault(
                        "ecommerce", "false")),
                source.pinBlockHex(), attributes.get("pinBlockKeyDomain"),
                source.emvDataHex(), attributes);
    }

    public static RoutingTransactionResponse response(
            IssuingAuthorizationResponse source, String route) {
        String posCode = posCode(source);
        return new RoutingTransactionResponse(
                source.transactionId(), source.status().name(), posCode,
                source.internalResponseCode(), source.authorizationCode(),
                route, "%012d".formatted(source.approvedAmountMinor()),
                source.arpcHex(), source.retryable(), source.attributes());
    }

    private static String posCode(IssuingAuthorizationResponse source) {
        return switch (source.internalResponseCode()) {
            case "APPROVED" -> "00";
            case "CARD_NOT_FOUND" -> "14";
            case "CARD_EXPIRED" -> "54";
            case "CARD_NOT_ACTIVE", "CONTRACT_NOT_ACTIVE" -> "62";
            case "CURRENCY_NOT_ALLOWED", "SERVICE_NOT_ALLOWED",
                    "OPERATION_NOT_SUPPORTED" -> "57";
            default -> source.status() == IssuingDecisionStatus.UNKNOWN
                    ? "91"
                    : source.status() == IssuingDecisionStatus.APPROVED
                    || source.status()
                    == IssuingDecisionStatus.PARTIALLY_APPROVED
                    ? "00" : "05";
        };
    }

    private static IssuingOperation operation(
            RoutingTransactionRequest source) {
        return switch (source.operation()) {
            case "HOLD" -> IssuingOperation.AUTHORIZATION;
            case "DEBIT", "CREDIT" -> IssuingOperation.FINANCIAL;
            case "REVERSAL" -> IssuingOperation.REVERSAL;
            case "ADVICE" -> IssuingOperation.ADVICE;
            case "CAPTURE" -> IssuingOperation.COMPLETION;
            case "INQUIRY" -> IssuingOperation.BALANCE_INQUIRY;
            default -> throw new IllegalArgumentException(
                    "Unsupported issuing operation");
        };
    }

    private static long parseAmount(String amount) {
        if (amount == null || !amount.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Invalid routing amount");
        }
        return Long.parseLong(amount);
    }
}
