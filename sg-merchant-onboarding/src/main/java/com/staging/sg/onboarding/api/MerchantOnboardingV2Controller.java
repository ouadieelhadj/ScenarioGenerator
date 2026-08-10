package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.service.MerchantOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant-onboarding/v2")
public class MerchantOnboardingV2Controller {
    private final MerchantOnboardingService service;

    public MerchantOnboardingV2Controller(MerchantOnboardingService service) {
        this.service = service;
    }

    @PutMapping("/dossiers/{id}")
    public DossierV2View update(@PathVariable UUID id, @Valid @RequestBody DossierV2Request request,
            Authentication authentication) {
        return DossierV2View.from(service.updateDossierV2(id, request.toData(), authentication.getName()));
    }

    @GetMapping("/dossiers/{id}")
    public DossierV2View get(@PathVariable UUID id, Authentication authentication) {
        return DossierV2View.from(service.getV2(id, authentication.getName()));
    }

    public record AddressRequest(@NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2, @Size(max = 120) String district,
            @NotBlank @Size(max = 120) String city, @Size(max = 120) String region,
            @Size(max = 24) String postalCode,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String country) {
        MerchantOnboardingService.AddressData toData() {
            return new MerchantOnboardingService.AddressData(line1, line2, district, city,
                    region, postalCode, country);
        }
    }

    public record RepresentativeRequest(@Size(max = 32) String title,
            @NotBlank @Size(max = 96) String firstName,
            @NotBlank @Size(max = 96) String lastName, LocalDate birthDate,
            @NotBlank @Size(max = 32) String phone, @Email @NotBlank String email,
            @NotBlank @Size(max = 32) String idType,
            @NotBlank @Size(max = 64) String idNumber,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String residenceCountry,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String nationality) {
        MerchantOnboardingService.RepresentativeData toData() {
            return new MerchantOnboardingService.RepresentativeData(title, firstName, lastName,
                    birthDate, phone, email, idType, idNumber, residenceCountry, nationality);
        }
    }

    public record BeneficialOwnerRequest(UUID id, @NotBlank @Size(max = 96) String firstName,
            @NotBlank @Size(max = 96) String lastName, boolean active) {
        MerchantOnboardingService.BeneficialOwnerData toData() {
            return new MerchantOnboardingService.BeneficialOwnerData(id, firstName, lastName, active);
        }
    }

    public record OutletProductRequest(@NotNull UUID productId,
            @Size(max = 64) String pricingPackCode, @Positive Integer pricingPackVersion,
            @Size(max = 16000) String pricingSnapshotJson) {
        MerchantOnboardingService.OutletProductData toData() {
            return new MerchantOnboardingService.OutletProductData(productId, pricingPackCode,
                    pricingPackVersion, pricingSnapshotJson);
        }
    }

    public record TerminalRequestRequest(UUID id, @NotNull UUID productId,
            @Min(1) @Max(999) int quantity,
            @NotBlank @Size(max = 64) String modelCode,
            @NotBlank @Size(max = 64) String connectivityCode,
            List<@Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,63}") String> optionCodes,
            @Size(max = 128) String externalReference) {
        MerchantOnboardingService.TerminalRequestData toData() {
            return new MerchantOnboardingService.TerminalRequestData(id, productId, quantity,
                    modelCode, connectivityCode, optionCodes == null ? List.of() : optionCodes,
                    externalReference);
        }
    }

    public record EcommerceStoreRequestRequest(UUID id, @NotNull UUID productId,
            @NotBlank @Size(max = 64) String storeCode,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 255) String allowedDomain,
            @NotBlank @Size(max = 512) String returnUrl,
            @NotBlank @Size(max = 512) String notificationUrl,
            @NotNull @Pattern(regexp = "[0-9]{3}") String currency,
            @NotBlank @Size(max = 32) String captureMode,
            List<@Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,63}") String> optionCodes,
            @Size(max = 128) String externalReference) {
        MerchantOnboardingService.EcommerceStoreData toData() {
            return new MerchantOnboardingService.EcommerceStoreData(id, productId, storeCode,
                    name, allowedDomain, returnUrl, notificationUrl, currency, captureMode,
                    optionCodes == null ? List.of() : optionCodes, externalReference);
        }
    }

    public record OutletRequest(UUID id, @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name, boolean principal, boolean active,
            @NotNull @Valid AddressRequest address,
            @NotBlank @Size(max = 32) String contactPhone,
            @Email @NotBlank String contactEmail,
            @NotNull @Valid RepresentativeRequest responsible,
            @Valid List<OutletProductRequest> products,
            @Valid List<TerminalRequestRequest> terminalRequests,
            @Valid List<EcommerceStoreRequestRequest> ecommerceStores) {
        MerchantOnboardingService.OutletData toData() {
            return new MerchantOnboardingService.OutletData(id, code, name, principal, active,
                    address.toData(), contactPhone, contactEmail, responsible.toData(),
                    products == null ? List.of() : products.stream().map(OutletProductRequest::toData).toList(),
                    terminalRequests == null ? List.of() : terminalRequests.stream().map(TerminalRequestRequest::toData).toList(),
                    ecommerceStores == null ? List.of() : ecommerceStores.stream().map(EcommerceStoreRequestRequest::toData).toList());
        }
    }

    public record DossierV2Request(@NotNull MerchantType merchantType,
            OrganizationLegalNature organizationLegalNature,
            @NotBlank @Size(max = 160) String legalName,
            @NotBlank @Size(max = 160) String tradingName,
            @NotBlank @Size(max = 64) String registrationNumber,
            @Size(max = 64) String taxIdentifier, @Size(max = 64) String ice,
            @Size(max = 96) String legalForm, @Size(max = 255) String businessActivity,
            @Size(max = 500) String associationPurpose,
            @NotBlank @Size(max = 32) String primaryPhone, @Email @NotBlank String primaryEmail,
            @NotNull @Valid AddressRequest headquartersAddress,
            @NotNull @Pattern(regexp = "\\d{4}") String mcc,
            @NotBlank @Size(max = 24) String rib,
            @NotNull @Valid RepresentativeRequest representative,
            @Valid List<BeneficialOwnerRequest> beneficialOwners,
            @NotEmpty @Valid List<OutletRequest> outlets,
            @PositiveOrZero long version) {
        MerchantOnboardingService.DossierV2Data toData() {
            return new MerchantOnboardingService.DossierV2Data(merchantType,
                    organizationLegalNature, legalName, tradingName, registrationNumber,
                    taxIdentifier, ice, legalForm, businessActivity, associationPurpose,
                    primaryPhone, primaryEmail, headquartersAddress.toData(), mcc, rib,
                    representative.toData(), beneficialOwners == null ? List.of()
                            : beneficialOwners.stream().map(BeneficialOwnerRequest::toData).toList(),
                    outlets.stream().map(OutletRequest::toData).toList(), version);
        }
    }

    public record AddressView(String line1, String line2, String district, String city,
            String region, String postalCode, String country) {}
    public record RepresentativeView(String title, String firstName, String lastName,
            LocalDate birthDate, String phone, String email, String idType, String idNumber,
            String residenceCountry, String nationality) {}
    public record BeneficialOwnerView(UUID id, String firstName, String lastName, boolean active) {
        static BeneficialOwnerView from(OnboardingBeneficialOwner value) {
            return new BeneficialOwnerView(value.id(), value.firstName(), value.lastName(), value.active());
        }
    }
    public record OutletProductView(UUID productId, String pricingPackCode,
            Integer pricingPackVersion, String pricingSnapshotJson) {
        static OutletProductView from(OnboardingOutletProduct value) {
            return new OutletProductView(value.productId(), value.pricingPackCode(),
                    value.pricingPackVersion(), value.pricingSnapshotJson());
        }
    }
    public record TerminalRequestView(UUID id, UUID productId, int quantity, String modelCode,
            String connectivityCode, List<String> optionCodes, TerminalRequestStatus status,
            String externalReference) {
        static TerminalRequestView from(TerminalRequest value) {
            return new TerminalRequestView(value.id(), value.productId(), value.quantity(),
                    value.modelCode(), value.connectivityCode(), value.optionCodes(), value.status(),
                    value.externalReference());
        }
    }
    public record EcommerceStoreView(UUID id, UUID productId, String storeCode, String name,
            String allowedDomain, String returnUrl, String notificationUrl, String currency,
            String captureMode, List<String> optionCodes, EcommerceStoreRequestStatus status,
            String externalReference) {
        static EcommerceStoreView from(EcommerceStoreRequest value) {
            return new EcommerceStoreView(value.id(), value.productId(), value.storeCode(),
                    value.name(), value.allowedDomain(), value.returnUrl(), value.notificationUrl(),
                    value.currency(), value.captureMode(), value.optionCodes(), value.status(),
                    value.externalReference());
        }
    }
    public record OutletView(UUID id, String code, String name, boolean principal, boolean active,
            AddressView address, String contactPhone, String contactEmail,
            RepresentativeView responsible, List<OutletProductView> products,
            List<TerminalRequestView> terminalRequests,
            List<EcommerceStoreView> ecommerceStores, long version) {
        static OutletView from(OnboardingOutlet value, MerchantOnboardingService.DossierV2Snapshot snapshot) {
            return new OutletView(value.id(), value.code(), value.name(), value.principal(), value.active(),
                    new AddressView(value.addressLine1(), value.addressLine2(), value.district(),
                            value.city(), value.region(), value.postalCode(), value.country()),
                    value.contactPhone(), value.contactEmail(),
                    new RepresentativeView(value.responsibleTitle(), value.responsibleFirstName(),
                            value.responsibleLastName(), value.responsibleBirthDate(),
                            value.responsiblePhone(), value.responsibleEmail(),
                            value.responsibleIdType(), value.responsibleIdNumber(),
                            value.responsibleResidenceCountry(), value.responsibleNationality()),
                    snapshot.outletProducts().stream().filter(item -> item.outletId().equals(value.id()))
                            .map(OutletProductView::from).toList(),
                    snapshot.terminalRequests().stream().filter(item -> item.outletId().equals(value.id()))
                            .map(TerminalRequestView::from).toList(),
                    snapshot.ecommerceStores().stream().filter(item -> item.outletId().equals(value.id()))
                            .map(EcommerceStoreView::from).toList(),
                    value.version());
        }
    }
    public record DossierV2View(UUID id, String reference, MerchantType merchantType,
            OrganizationLegalNature organizationLegalNature, String legalName, String tradingName,
            String registrationNumber, String taxIdentifier, String ice, String legalForm,
            String businessActivity, String associationPurpose, String primaryPhone,
            String primaryEmail, AddressView headquartersAddress, String mcc, String rib,
            RepresentativeView representative, List<BeneficialOwnerView> beneficialOwners,
            List<OutletView> outlets, OnboardingStatus status, long version) {
        static DossierV2View from(MerchantOnboardingService.DossierV2Snapshot snapshot) {
            MerchantOnboardingCase value = snapshot.dossier();
            return new DossierV2View(value.id(), value.reference(), value.merchantType(),
                    value.organizationLegalNature(), value.legalName(), value.tradingName(),
                    value.registrationNumber(), value.taxIdentifier(), value.ice(), value.legalForm(),
                    value.businessActivity(), value.associationPurpose(), value.primaryPhone(),
                    value.primaryEmail(), new AddressView(value.headquartersAddressLine1(),
                            value.headquartersAddressLine2(), value.headquartersDistrict(),
                            value.headquartersCity(), value.headquartersRegion(),
                            value.headquartersPostalCode(), value.country()), value.mcc(),
                    value.rib(),
                    new RepresentativeView(value.representativeTitle(), value.representativeFirstName(),
                            value.representativeLastName(), value.representativeBirthDate(),
                            value.representativePhone(), value.representativeEmail(),
                            value.representativeIdType(), value.representativeIdNumber(),
                            value.representativeResidenceCountry(), value.representativeNationality()),
                    snapshot.beneficialOwners().stream().map(BeneficialOwnerView::from).toList(),
                    snapshot.outlets().stream().map(item -> OutletView.from(item, snapshot)).toList(),
                    value.status(), value.version());
        }
    }
}
