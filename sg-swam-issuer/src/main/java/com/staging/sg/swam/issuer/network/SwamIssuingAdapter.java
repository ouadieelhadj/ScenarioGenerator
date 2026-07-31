package com.staging.sg.swam.issuer.network;

import com.staging.sg.common.issuing.*;
import com.staging.sg.common.issuing.client.DatabaseIssuingClient;
import com.staging.sg.common.service.SwamInterfaceService;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SwamIssuingAdapter {
    private final DatabaseIssuingClient issuing;
    private final SwamInterfaceService interfaceService;

    public SwamIssuingAdapter(
            DatabaseIssuingClient issuing,
            SwamInterfaceService interfaceService) {
        this.issuing = issuing;
        this.interfaceService = interfaceService;
    }

    public Decision authorize(ISOMsg message) {
        try {
            String transactionId = reference(message);
            IssuingAuthorizationResponse response = issuing.authorize(
                    "SWAM",
                    new IssuingAuthorizationRequest(
                            "1.0", interfaceService.get().getBankCode(),
                            "SWAM_ISSUER", transactionId, transactionId,
                            transactionId, operation(message), null,
                            PaymentIdentifierType.PAN, text(message, 2),
                            amount(message), text(message, 49),
                            text(message, 12), text(message, 41),
                            text(message, 42), text(message, 18),
                            text(message, 19), true, false,
                            hex(message, 52), "SWAM_PEK",
                            hex(message, 55),
                            Map.of("sourceMti", message.getMTI(),
                                    "processingCode", text(message, 3))));
            return new Decision(
                    responseCode(response), response.authorizationCode(),
                    response.arpcHex(), response.status().name(),
                    response.retryable());
        } catch (RuntimeException | org.jpos.iso.ISOException failure) {
            return new Decision("909", null, null,
                    "UNKNOWN", true);
        }
    }

    private static String responseCode(IssuingAuthorizationResponse response) {
        if (response.status() == IssuingDecisionStatus.APPROVED
                || response.status()
                == IssuingDecisionStatus.PARTIALLY_APPROVED) return "000";
        return switch (response.internalResponseCode()) {
            case "CARD_NOT_FOUND" -> "114";
            case "CARD_NOT_ACTIVE", "CONTRACT_NOT_ACTIVE" -> "062";
            case "CARD_EXPIRED" -> "054";
            case "INSUFFICIENT_FUNDS" -> "116";
            case "PIN_INVALID" -> "117";
            default -> response.retryable() ? "909" : "100";
        };
    }

    private static IssuingOperation operation(ISOMsg message)
            throws org.jpos.iso.ISOException {
        return "1200".equals(message.getMTI())
                ? IssuingOperation.FINANCIAL
                : IssuingOperation.AUTHORIZATION;
    }

    private static String reference(ISOMsg m) {
        return "SWAM-" + text(m, 7) + "-" + text(m, 11)
                + "-" + text(m, 37);
    }

    private static long amount(ISOMsg m) {
        String value = text(m, 4);
        if (value == null || !value.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Invalid SWAM amount");
        }
        return Long.parseLong(value);
    }

    private static String text(ISOMsg m, int field) {
        return m.hasField(field) ? m.getString(field) : "";
    }

    private static String hex(ISOMsg m, int field) {
        return m.hasField(field) ? ISOUtil.hexString(m.getBytes(field)) : null;
    }

    public record Decision(
            String responseCode, String authorizationCode,
            String arpcHex, String status, boolean retryable) {}
}
