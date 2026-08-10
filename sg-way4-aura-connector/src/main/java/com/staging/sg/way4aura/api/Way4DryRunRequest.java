package com.staging.sg.way4aura.api;

import java.util.List;
import java.util.UUID;

public record Way4DryRunRequest(String schemaVersion, UUID onboardingCaseId,
        UUID merchantId, UUID merchantContractId, Merchant merchant,
        AccountContract accountContract, List<DeviceContract> deviceContracts,
        String idempotencyKey) {
    public record Merchant(String merchantType, String registrationNumber,
            String taxpayerIdentifier, String legalName, String tradingName,
            Address headquartersAddress, String mcc) {}
    public record Address(String country, String city, String postalCode,
            String line1, String location) {}
    public record AccountContract(String contractNumber, String sourceProductCode,
            String currencyCode) {}
    public record DeviceContract(UUID outletId, UUID terminalRequestId, UUID contractId,
            String contractNumber, String terminalId, String merchantId,
            String sourceProductCode, String sourceDeviceType, String currencyCode,
            String mcc, String location) {}
}
