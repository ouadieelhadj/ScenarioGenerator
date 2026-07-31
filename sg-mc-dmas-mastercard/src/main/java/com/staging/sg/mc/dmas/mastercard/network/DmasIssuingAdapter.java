package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.issuing.*;
import com.staging.sg.common.issuing.client.DatabaseIssuingClient;
import com.staging.sg.common.service.McDmasInterfaceService;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DmasIssuingAdapter {
    private final DatabaseIssuingClient issuing;
    private final McDmasInterfaceService interfaces;

    public DmasIssuingAdapter(
            DatabaseIssuingClient issuing,
            McDmasInterfaceService interfaces) {
        this.issuing = issuing;
        this.interfaces = interfaces;
    }

    public Decision authorize(ISOMsg message) {
        try {
            String reference = "DMAS-" + text(message, 7) + "-"
                    + text(message, 11) + "-" + text(message, 37);
            IssuingAuthorizationResponse response = issuing.authorize(
                    "DMAS",
                    new IssuingAuthorizationRequest(
                            "1.0", interfaces.bankCode(),
                            "DMAS_MASTERCARD", reference, reference,
                            reference, IssuingOperation.AUTHORIZATION, null,
                            PaymentIdentifierType.PAN, text(message, 2),
                            amount(message), text(message, 49),
                            text(message, 12), text(message, 41),
                            text(message, 42), text(message, 18),
                            text(message, 19), true, false,
                            hex(message, 52), "DMAS_PEK",
                            hex(message, 55),
                            Map.of("sourceMti", message.getMTI(),
                                    "processingCode", text(message, 3))));
            return new Decision(
                    responseCode(response), response.authorizationCode(),
                    response.arpcHex(), response.status().name(),
                    response.retryable());
        } catch (RuntimeException | org.jpos.iso.ISOException failure) {
            return new Decision("96", null, null, "UNKNOWN", true);
        }
    }

    private static String responseCode(IssuingAuthorizationResponse response) {
        if (response.status() == IssuingDecisionStatus.APPROVED
                || response.status()
                == IssuingDecisionStatus.PARTIALLY_APPROVED) return "00";
        return switch (response.internalResponseCode()) {
            case "CARD_NOT_FOUND" -> "14";
            case "CARD_NOT_ACTIVE", "CONTRACT_NOT_ACTIVE" -> "62";
            case "CARD_EXPIRED" -> "54";
            case "INSUFFICIENT_FUNDS" -> "51";
            case "PIN_INVALID" -> "55";
            default -> "96";
        };
    }

    private static long amount(ISOMsg m) {
        String value = text(m, 4);
        if (value == null || !value.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Invalid DMAS amount");
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
