package com.staging.sg.way4aura.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Way4DryRunRequest(String schemaVersion, UUID onboardingCaseId,
        String applicationRegNumber, UUID accountProductId, Merchant merchant,
        Settlement settlement, List<Outlet> outlets, String idempotencyKey) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Merchant(String merchantType, String registrationNumber,
            String taxIdentifier, String legalName, String tradingName,
            Address headquartersAddress, String mcc) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String line1, String line2, String district, String city,
            String region, String postalCode, String country) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Settlement(String accountReference, String currency) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Outlet(UUID sourceOutletId, String code, String name, boolean principal,
            Address address, List<OutletProduct> products, List<TerminalRequest> terminalRequests) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutletProduct(UUID productId) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TerminalRequest(UUID sourceRequestId, UUID productId, int quantity,
            String modelCode, String connectivityCode, List<String> optionCodes) {}
}
