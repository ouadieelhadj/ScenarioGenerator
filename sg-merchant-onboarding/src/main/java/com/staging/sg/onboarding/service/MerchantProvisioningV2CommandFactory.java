package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.MerchantProvisioningCommandV2;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MerchantProvisioningV2CommandFactory {
    private final OnboardingOutletRepository outlets;
    private final OnboardingBeneficialOwnerRepository owners;
    private final OnboardingOutletProductRepository products;
    private final TerminalRequestRepository terminals;
    private final EcommerceStoreRequestRepository stores;

    public MerchantProvisioningV2CommandFactory(OnboardingOutletRepository outlets,
            OnboardingBeneficialOwnerRepository owners,
            OnboardingOutletProductRepository products, TerminalRequestRepository terminals,
            EcommerceStoreRequestRepository stores) {
        this.outlets = outlets;
        this.owners = owners;
        this.products = products;
        this.terminals = terminals;
        this.stores = stores;
    }

    public MerchantProvisioningCommandV2 create(MerchantOnboardingCase dossier) {
        List<OnboardingOutlet> activeOutlets = outlets
                .findByCaseIdOrderByCreatedAtAsc(dossier.id()).stream()
                .filter(OnboardingOutlet::active).toList();
        if (activeOutlets.size() < 1 || activeOutlets.stream().filter(OnboardingOutlet::principal).count() != 1)
            throw new IllegalStateException("PDV-002: exactly one active principal outlet is required");

        var address = new MerchantProvisioningCommandV2.Address(
                dossier.headquartersAddressLine1(), dossier.headquartersAddressLine2(),
                dossier.headquartersDistrict(), dossier.headquartersCity(),
                dossier.headquartersRegion(), dossier.headquartersPostalCode(), dossier.country());
        var representative = new MerchantProvisioningCommandV2.Representative(
                dossier.representativeTitle(), dossier.representativeFirstName(),
                dossier.representativeLastName(), dossier.representativeBirthDate(),
                dossier.representativePhone(), dossier.representativeEmail(),
                dossier.representativeIdType(), dossier.representativeIdNumber(),
                dossier.representativeResidenceCountry(), dossier.representativeNationality());
        var beneficialOwners = owners.findByCaseIdAndActiveTrueOrderByCreatedAtAsc(dossier.id())
                .stream().map(value -> new MerchantProvisioningCommandV2.BeneficialOwner(
                        value.id(), value.firstName(), value.lastName())).toList();
        var merchant = new MerchantProvisioningCommandV2.LegalMerchant(
                dossier.merchantType().name(),
                dossier.organizationLegalNature() == null ? null : dossier.organizationLegalNature().name(),
                dossier.legalName(), dossier.tradingName(), dossier.registrationNumber(),
                dossier.taxIdentifier(), dossier.ice(), dossier.legalForm(),
                dossier.businessActivity(), dossier.associationPurpose(), dossier.primaryPhone(),
                dossier.primaryEmail(), dossier.rib(), address, representative,
                beneficialOwners, dossier.mcc());
        var settlement = new MerchantProvisioningCommandV2.Settlement(
                dossier.settlementAccountReference(), dossier.settlementCurrency());
        return new MerchantProvisioningCommandV2("2.0", dossier.id(), dossier.reference(),
                dossier.acquirerId(), merchant, settlement, dossier.acceptanceChannel(),
                activeOutlets.stream().map(this::outlet).toList(),
                dossier.submittedBy(), dossier.checkedBy());
    }

    private MerchantProvisioningCommandV2.Outlet outlet(OnboardingOutlet outlet) {
        UUID outletId = outlet.id();
        var address = new MerchantProvisioningCommandV2.Address(outlet.addressLine1(),
                outlet.addressLine2(), outlet.district(), outlet.city(), outlet.region(),
                outlet.postalCode(), outlet.country());
        var responsible = new MerchantProvisioningCommandV2.Representative(
                outlet.responsibleTitle(), outlet.responsibleFirstName(),
                outlet.responsibleLastName(), outlet.responsibleBirthDate(),
                outlet.responsiblePhone(), outlet.responsibleEmail(), outlet.responsibleIdType(),
                outlet.responsibleIdNumber(), outlet.responsibleResidenceCountry(),
                outlet.responsibleNationality());
        var outletProducts = products.findByOutletIdAndActiveTrueOrderByProductIdAsc(outletId)
                .stream().map(value -> new MerchantProvisioningCommandV2.OutletProduct(
                        value.productId(), value.pricingPackCode(), value.pricingPackVersion(),
                        value.pricingSnapshotJson())).toList();
        var terminalRequests = terminals.findByOutletIdOrderByCreatedAtAsc(outletId).stream()
                .filter(value -> value.status() == TerminalRequestStatus.REQUESTED)
                .map(value -> new MerchantProvisioningCommandV2.TerminalRequest(value.id(),
                        value.productId(), value.quantity(), value.modelCode(),
                        value.connectivityCode(), value.optionCodes())).toList();
        var ecommerceStores = stores.findByOutletIdOrderByCreatedAtAsc(outletId).stream()
                .filter(value -> value.status() == EcommerceStoreRequestStatus.REQUESTED)
                .map(value -> new MerchantProvisioningCommandV2.EcommerceStore(value.id(),
                        value.productId(), value.storeCode(), value.name(), value.allowedDomain(),
                        value.returnUrl(), value.notificationUrl(), value.currency(),
                        value.captureMode(), value.optionCodes())).toList();
        return new MerchantProvisioningCommandV2.Outlet(outletId, outlet.code(), outlet.name(),
                outlet.principal(), address, outlet.contactPhone(), outlet.contactEmail(),
                responsible, outletProducts, terminalRequests, ecommerceStores);
    }
}
