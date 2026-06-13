package com.staging.sg.issuer;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decision Engine — Mastercard Issuer.
 *
 * Modes :
 *   approve-all : approves all transactions
 *   rule-based  : applies configured rules
 */
@Component
public class McDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(McDecisionEngine.class);

    public static final String RC_APPROVED           = "00";
    public static final String RC_DO_NOT_HONOR       = "05";
    public static final String RC_INSUFFICIENT_FUNDS = "51";
    public static final String RC_EXPIRED_CARD       = "54";
    public static final String RC_INCORRECT_PIN      = "55";
    public static final String RC_ISSUER_UNAVAILABLE = "91";

    @Value("${mc.issuer.decision-mode:approve-all}")
    private String decisionMode;

    @Value("${mc.issuer.rules.max-amount:500000}")
    private long maxAmount;

    @Value("${mc.issuer.rules.block-mcc:}")
    private List<String> blockMcc;

    @Value("${mc.issuer.force-response-code:}")
    private String forceResponseCode;

    public Decision decide(ISOMsg request) {

        // Force response code (for testing)
        if (forceResponseCode != null && !forceResponseCode.isBlank()) {
            log.info("[ISSUING] Forced response code : {}", forceResponseCode);
            return new Decision(forceResponseCode, "Forced");
        }

        // approve-all mode
        if ("approve-all".equalsIgnoreCase(decisionMode)) {
            return new Decision(RC_APPROVED, "approve-all");
        }

        // rule-based mode

        // Rule 1 : max amount
        try {
            if (request.hasField(4)) {
                long amount = Long.parseLong(request.getString(4).trim());
                if (amount > maxAmount) {
                    log.info("[ISSUING] Declined — amount {} > limit {}", amount, maxAmount);
                    return new Decision(RC_INSUFFICIENT_FUNDS, "Amount exceeds limit");
                }
            }
        } catch (Exception e) {
            log.warn("[ISSUING] Error reading DE004 : {}", e.getMessage());
        }

        // Rule 2 : blocked MCC
        try {
            if (request.hasField(18) && blockMcc != null && !blockMcc.isEmpty()) {
                String mcc = request.getString(18);
                if (blockMcc.contains(mcc)) {
                    log.info("[ISSUING] Declined — MCC {} blocked", mcc);
                    return new Decision(RC_DO_NOT_HONOR, "MCC blocked : " + mcc);
                }
            }
        } catch (Exception e) {
            log.warn("[ISSUING] Error reading DE018 : {}", e.getMessage());
        }

        return new Decision(RC_APPROVED, "All rules passed");
    }

    public static class Decision {
        private final String responseCode;
        private final String reason;

        public Decision(String responseCode, String reason) {
            this.responseCode = responseCode;
            this.reason       = reason;
        }

        public String  responseCode() { return responseCode; }
        public String  reason()       { return reason; }
        public boolean isApproved()   { return RC_APPROVED.equals(responseCode); }
    }
}
