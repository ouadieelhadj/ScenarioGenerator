package com.staging.sg.onboarding.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MerchantProvisioningCommandV2(
        String schemaVersion, UUID onboardingCaseId, String onboardingReference,
        String acquirerId, LegalMerchant merchant, Settlement settlement,
        String acceptanceChannel, List<Outlet> outlets, String maker, String checker) {

    public record LegalMerchant(String merchantType, String organizationLegalNature,
            String legalName, String tradingName, String registrationNumber,
            String taxIdentifier, String ice, String legalForm, String businessActivity,
            String associationPurpose, String primaryPhone, String primaryEmail, String rib,
            Address headquartersAddress, Representative representative,
            List<BeneficialOwner> beneficialOwners, String mcc) {}
    public record Address(String line1, String line2, String district, String city,
            String region, String postalCode, String country) {}
    public record Representative(String title, String firstName, String lastName,
            LocalDate birthDate, String phone, String email, String idType, String idNumber,
            String residenceCountry, String nationality) {}
    public record BeneficialOwner(UUID sourceId, String firstName, String lastName) {}
    public record Settlement(String accountReference, String currency) {}
    public record Outlet(UUID sourceOutletId, String code, String name, boolean principal,
            Address address, String contactPhone, String contactEmail,
            Representative responsible, List<OutletProduct> products,
            List<TerminalRequest> terminalRequests, List<EcommerceStore> ecommerceStores) {}
    public record OutletProduct(UUID productId, String pricingPackCode,
            Integer pricingPackVersion, String pricingSnapshotJson) {}
    public record TerminalRequest(UUID sourceRequestId, UUID productId, int quantity,
            String modelCode, String connectivityCode, List<String> optionCodes) {}
    public record EcommerceStore(UUID sourceRequestId, UUID productId, String storeCode,
            String name, String allowedDomain, String returnUrl, String notificationUrl,
            String currency, String captureMode, List<String> optionCodes) {}
}
