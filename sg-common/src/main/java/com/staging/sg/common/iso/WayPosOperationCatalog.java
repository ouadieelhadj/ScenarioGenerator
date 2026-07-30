package com.staging.sg.common.iso;

/** Resolves every Basic/Extended operation family documented by OpenWay. */
public final class WayPosOperationCatalog {
    public enum Effect {
        HOLD, DEBIT, CREDIT, INQUIRY, CAPTURE, ADVICE, CONTROL, REVERSAL,
        RECONCILIATION, INITIALIZATION, KEY_CHANGE, FILE, UNSUPPORTED
    }

    public record Operation(String name, Effect effect, boolean extended) {}

    private WayPosOperationCatalog() {}

    public static Operation resolve(String mti, String processingCode) {
        return resolve(mti, processingCode, null);
    }

    public static Operation resolve(
            String mti, String processingCode, String networkId) {
        String type = mti == null ? "" : mti;
        String code = processingCode == null ? "" : processingCode;
        String prefix = code.length() >= 2 ? code.substring(0, 2) : code;
        if (type.startsWith("04")) return op("UNIVERSAL_REVERSAL", Effect.REVERSAL, false);
        if (type.startsWith("05")) return op("RECONCILIATION", Effect.RECONCILIATION, false);
        if ("0302".equals(type)) return op("FILE_UPDATE", Effect.FILE, true);
        if (type.startsWith("032")) {
            return op("BATCH_UPLOAD_ADVICE", Effect.FILE, false);
        }
        if (type.startsWith("083")) return op("REJECT", Effect.UNSUPPORTED, false);
        if (type.startsWith("08")) {
            if ("960000".equals(code)) return op("KEY_CHANGE", Effect.KEY_CHANGE, true);
            if ("930000".equals(code)) return op("POS_INITIALIZATION", Effect.INITIALIZATION, false);
            return op("NETWORK_MANAGEMENT_OTHER", Effect.UNSUPPORTED, false);
        }
        if (type.startsWith("91") || type.startsWith("97")) {
            return op("INFORMATION_INQUIRY", Effect.INQUIRY, false);
        }
        if (type.startsWith("012")) return op("INFORMATIONAL_ADVICE", Effect.ADVICE, false);
        if (type.startsWith("022")) {
            if ("102".equals(networkId)) {
                return op("AFD_COMPLETION", Effect.CAPTURE, true);
            }
            if ("202".equals(networkId) && "02".equals(prefix)) {
                return op("TIP_PURCHASE_COMPLETION", Effect.CAPTURE, false);
            }
            if ("202".equals(networkId)) {
                return op("AUTHORIZATION_CONFIRMATION", Effect.CAPTURE, false);
            }
            if ("59".equals(prefix)) {
                return op("BILL_PAYMENT_ADVICE", Effect.CAPTURE, true);
            }
            if ("200".equals(networkId)) {
                return op("OFFLINE_FINANCIAL_ADVICE", Effect.DEBIT, false);
            }
            return op("FINAL_ADVICE", Effect.ADVICE, false);
        }
        if (type.startsWith("01")) {
            return switch (prefix) {
                case "00" -> op("AUTHORIZATION", Effect.HOLD, false);
                case "16" -> op("LOYALTY_PROGRAM_REQUEST", Effect.INQUIRY, true);
                case "30" -> op("BALANCE_INQUIRY", Effect.INQUIRY, false);
                case "32" -> op("MINI_STATEMENT", Effect.INQUIRY, false);
                case "39" -> op("CARD_VERIFICATION", Effect.INQUIRY, false);
                case "50" -> op("UTILITY_PAYMENT_AUTHORIZATION", Effect.HOLD, true);
                case "51" -> op("PREAUTHORIZATION_CASH", Effect.HOLD, false);
                case "59" -> op("BILL_PAYMENT_AUTHORIZATION", Effect.HOLD, true);
                case "91" -> op("CARD_CONTROL_REQUEST", Effect.CONTROL, true);
                case "92" -> op("PIN_MANAGEMENT", Effect.CONTROL, true);
                default -> op("AUTHORIZATION_OTHER", Effect.HOLD, false);
            };
        }
        if (type.startsWith("02")) {
            return switch (prefix) {
                case "00" -> op("PURCHASE_OR_PAYMENT", Effect.DEBIT, false);
                case "09" -> op("PURCHASE_WITH_CASHBACK", Effect.DEBIT, false);
                case "50" -> op("UTILITY_PAYMENT", Effect.DEBIT, true);
                case "52" -> op("CASH_BY_CODE", Effect.DEBIT, false);
                case "02" -> op("TIP_COMPLETION", Effect.DEBIT, false);
                case "20" -> op("REFUND", Effect.CREDIT, false);
                case "21" -> op("CREDIT", Effect.CREDIT, true);
                case "24" -> op("CREDIT_VOUCHER", Effect.CREDIT, true);
                case "25" -> op("PURCHASE_RETURN", Effect.CREDIT, false);
                case "29" -> op("CASH_TO_CARD", Effect.CREDIT, false);
                case "23" -> op("MIR_EC_PURCHASE_RETURN", Effect.CREDIT, false);
                case "48" -> op("P2P_CARD_TO_CARD", Effect.DEBIT, true);
                case "59" -> op("BILL_PAYMENT_ADVICE", Effect.ADVICE, true);
                default -> op("FINANCIAL_OTHER", Effect.DEBIT, false);
            };
        }
        return op("UNSUPPORTED", Effect.UNSUPPORTED, false);
    }

    private static Operation op(String name, Effect effect, boolean extended) {
        return new Operation(name, effect, extended);
    }
}
