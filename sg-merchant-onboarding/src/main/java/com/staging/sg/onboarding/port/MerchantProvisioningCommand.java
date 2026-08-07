package com.staging.sg.onboarding.port;

import java.util.UUID;

public record MerchantProvisioningCommand(
        UUID onboardingCaseId,
        String onboardingReference,
        String acquirerId,
        String legalName,
        String tradingName,
        String registrationNumber,
        String country,
        String mcc,
        String settlementAccountReference,
        String settlementCurrency,
        UUID productId,
        String acceptanceChannel,
        Outlet outlet,
        String maker,
        String checker) {
    public record Outlet(String code, String name, String address, int terminalCount) {}
}
