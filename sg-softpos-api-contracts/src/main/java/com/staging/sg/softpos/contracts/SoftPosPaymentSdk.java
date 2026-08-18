package com.staging.sg.softpos.contracts;

import com.staging.sg.softpos.contracts.SoftPosContracts.AcceptanceChannel;

/** Vendor-neutral boundary implemented by the selected certified mobile SDK. */
public interface SoftPosPaymentSdk {
    SdkResult accept(SdkRequest request);
    record SdkRequest(long amountMinor, String currency, AcceptanceChannel channel) {}
    record SdkResult(String sdkCredentialReference, String integrityReference, String provider, String providerTransactionReference) {}
}
