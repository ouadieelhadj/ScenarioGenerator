package com.staging.sg.softpos.sdk.simulator;

import com.staging.sg.softpos.contracts.SoftPosPaymentSdk;
import java.util.UUID;

/** Laboratory-only SDK. It emits aliases; card data never leaves the server test vault. */
public final class LaboratorySoftPosPaymentSdk implements SoftPosPaymentSdk {
    private final boolean laboratoryBuild;
    public LaboratorySoftPosPaymentSdk(boolean laboratoryBuild) { this.laboratoryBuild = laboratoryBuild; }
    @Override public SdkResult accept(SdkRequest request) {
        if (!laboratoryBuild) throw new IllegalStateException("Laboratory SDK is disabled outside a laboratory build");
        if (request == null || request.amountMinor() <= 0 || request.currency() == null || request.channel() == null)
            throw new IllegalArgumentException("Invalid SDK payment request");
        return new SdkResult("LABREF:APPROVED_CARD", "LAB-INTEGRITY:" + UUID.randomUUID(), "FUTURPAYMENT_LAB", UUID.randomUUID().toString());
    }
}
