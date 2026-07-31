package com.staging.sg.card.issuing.port;

import com.staging.sg.common.issuing.PaymentIdentifierType;

public interface PaymentIdentifierResolutionPort {
    ResolvedPaymentIdentifier resolve(
            String issuerId, PaymentIdentifierType type, String presentedIdentifier);

    record ResolvedPaymentIdentifier(String vaultReference) {
        public ResolvedPaymentIdentifier {
            if (vaultReference == null || vaultReference.isBlank())
                throw new IllegalArgumentException("Missing vault reference");
        }
        @Override public String toString(){
            return "ResolvedPaymentIdentifier[vaultReference=REDACTED]";
        }
    }
}
