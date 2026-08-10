package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.AcquiringProvisioningPort;
import com.staging.sg.onboarding.repository.OnboardingOutletRepository;
import com.staging.sg.onboarding.repository.OnboardingReferenceValueRepository;
import com.staging.sg.onboarding.repository.OnboardingFieldRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "jwt.secret=merchant-onboarding-test-key-not-for-runtime")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MerchantOnboardingIncrement1Test {
    @Autowired private MerchantOnboardingService service;
    @Autowired private OnboardingOutletRepository outlets;
    @Autowired private OnboardingReferenceValueRepository references;
    @Autowired private OnboardingFieldRuleRepository fieldRules;
    @MockBean private AcquiringProvisioningPort acquiring;

    @BeforeEach
    void seedReferences() {
        references.save(OnboardingReferenceValue.active("COUNTRY", "MA", "Maroc"));
        references.save(OnboardingReferenceValue.active("MCC", "5411", "Epiceries et supermarches"));
        fieldRules.save(OnboardingFieldRule.active(MerchantType.PM, "taxIdentifier", true, 64));
    }

    @Test
    void pmRoundTripPersistsStructuredLegalProfileAndTwoOutlets() {
        var prospect = prospect("pm");
        var snapshot = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.PM, null, "ICE-001", "Retail", null, twoOutlets(), 0), "pm.user");

        assertEquals(MerchantType.PM, snapshot.dossier().merchantType());
        assertEquals("ICE-001", snapshot.dossier().ice());
        assertEquals("123456789012345678901234", snapshot.dossier().rib());
        assertEquals(2, snapshot.outlets().size());
        assertEquals(1, snapshot.outlets().stream().filter(OnboardingOutlet::active)
                .filter(OnboardingOutlet::principal).count());
        assertEquals(2, service.getV2(prospect.dossier().id(), "pm.user").outlets().size());
        OnboardingOutlet principal = snapshot.outlets().stream().filter(OnboardingOutlet::principal)
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(1980, 1, 1), principal.responsibleBirthDate());
        assertEquals("CIN", principal.responsibleIdType());
        assertEquals("AB1234", principal.responsibleIdNumber());
        assertEquals("MA", principal.responsibleResidenceCountry());
        assertEquals("MA", principal.responsibleNationality());
    }

    @Test
    void ppLegalProfileIsAcceptedAndRetained() {
        var prospect = prospect("pp");
        var snapshot = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.PP, null, null, "Independent retail", null,
                        List.of(outlet("PP-1", true)), 0), "pp.user");
        assertEquals(MerchantType.PP, snapshot.dossier().merchantType());
        assertEquals("Independent retail", snapshot.dossier().businessActivity());
    }

    @Test
    void autoEntrepreneurLegalProfileIsAcceptedAndRetained() {
        var prospect = prospect("ae");
        var snapshot = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.AE, null, null, "Online services", null,
                        List.of(outlet("AE-1", true)), 0), "ae.user");
        assertEquals(MerchantType.AE, snapshot.dossier().merchantType());
        assertEquals("Online services", snapshot.dossier().businessActivity());
    }

    @Test
    void associationKeepsAssociationNature() {
        var prospect = prospect("association");
        var snapshot = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.ASSOCIATION_FOUNDATION, OrganizationLegalNature.ASSOCIATION,
                        null, null, "Social purpose", List.of(outlet("A-1", true)), 0),
                "association.user");
        assertEquals(OrganizationLegalNature.ASSOCIATION,
                snapshot.dossier().organizationLegalNature());
    }

    @Test
    void foundationKeepsFoundationNature() {
        var prospect = prospect("foundation");
        var snapshot = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.ASSOCIATION_FOUNDATION, OrganizationLegalNature.FOUNDATION,
                        null, null, "Foundation purpose", List.of(outlet("F-1", true)), 0),
                "foundation.user");
        assertEquals(OrganizationLegalNature.FOUNDATION,
                snapshot.dossier().organizationLegalNature());
    }

    @Test
    void rejectsZeroOrSeveralActivePrincipalOutlets() {
        var prospect = prospect("principal");
        var zero = List.of(outlet("P-1", false));
        var two = List.of(outlet("P-1", true), outlet("P-2", true));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.updateDossierV2(
                prospect.dossier().id(), dossier(MerchantType.PM, null, "ICE-002", "Retail", null,
                        zero, 0), "principal.user")).getMessage().contains("PDV-002"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.updateDossierV2(
                prospect.dossier().id(), dossier(MerchantType.PM, null, "ICE-002", "Retail", null,
                        two, 0), "principal.user")).getMessage().contains("PDV-002"));
    }

    @Test
    void v1AdapterCreatesAndReusesOnePrincipalOutlet() {
        var prospect = prospect("legacy");
        var first = legacy("Legacy address");
        var second = legacy("Updated address");
        service.updateDossier(prospect.dossier().id(), first, "legacy.user");
        service.updateDossier(prospect.dossier().id(), second, "legacy.user");

        var migrated = outlets.findByCaseIdOrderByCreatedAtAsc(prospect.dossier().id());
        assertEquals(1, migrated.size());
        assertTrue(migrated.get(0).principal());
        assertEquals("Updated address", migrated.get(0).addressLine1());
    }

    @Test
    void rejectsStaleV2Version() {
        var prospect = prospect("version");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.updateDossierV2(
                prospect.dossier().id(), dossier(MerchantType.PM, null, "ICE-003", "Retail", null,
                        List.of(outlet("V-1", true)), 9), "version.user"))
                .getMessage().contains("CONCURRENCY"));
    }

    @Test
    void omittedOutletIsLogicallyDeactivatedWithoutLosingHistory() {
        var prospect = prospect("history");
        var first = service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.PM, null, "ICE-004", "Retail", null, twoOutlets(), 0),
                "history.user");
        OnboardingOutlet principal = first.outlets().stream().filter(OnboardingOutlet::principal)
                .findFirst().orElseThrow();
        var retained = new MerchantOnboardingService.OutletData(principal.id(), principal.code(),
                principal.name(), true, true, address(principal.addressLine1()),
                principal.contactPhone(), principal.contactEmail(), representative());

        service.updateDossierV2(prospect.dossier().id(),
                dossier(MerchantType.PM, null, "ICE-004", "Retail", null,
                        List.of(retained), first.dossier().version()), "history.user");

        var history = outlets.findByCaseIdOrderByCreatedAtAsc(prospect.dossier().id());
        assertEquals(2, history.size());
        assertEquals(1, history.stream().filter(OnboardingOutlet::active).count());
        assertEquals(1, history.stream().filter(OnboardingOutlet::principal).count());
    }

    @Test
    void appliesConfiguredCompletenessRuleByMerchantType() {
        var prospect = prospect("rules");
        var valid = dossier(MerchantType.PM, null, "ICE-005", "Retail", null,
                List.of(outlet("R-1", true)), 0);
        var missingTaxIdentifier = new MerchantOnboardingService.DossierV2Data(valid.merchantType(),
                valid.organizationLegalNature(), valid.legalName(), valid.tradingName(),
                valid.registrationNumber(), null, valid.ice(), valid.legalForm(),
                valid.businessActivity(), valid.associationPurpose(), valid.primaryPhone(),
                valid.primaryEmail(), valid.headquartersAddress(), valid.mcc(), valid.rib(),
                valid.representative(), valid.beneficialOwners(), valid.outlets(), valid.version());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.updateDossierV2(
                prospect.dossier().id(), missingTaxIdentifier, "rules.user"))
                .getMessage().contains("MER-002"));
    }

    private MerchantOnboardingService.Prospect prospect(String prefix) {
        return service.createProspect(prefix + ".user", prefix + "@example.test", "ACQ-01", "commercial.user");
    }

    private static MerchantOnboardingService.DossierData legacy(String address) {
        return new MerchantOnboardingService.DossierData("Legacy Legal", "Legacy Shop", "RC-LEGACY",
                "MA", "5411", "ACC-001", "504", UUID.randomUUID(), "BOTH",
                "LEGACY-1", "Legacy outlet", address, 1);
    }

    private static List<MerchantOnboardingService.OutletData> twoOutlets() {
        return List.of(outlet("OUT-1", true), outlet("OUT-2", false));
    }

    private static MerchantOnboardingService.DossierV2Data dossier(MerchantType type,
            OrganizationLegalNature nature, String ice, String activity, String purpose,
            List<MerchantOnboardingService.OutletData> outlets, long version) {
        return new MerchantOnboardingService.DossierV2Data(type, nature, "Legal name", "Trade name",
                "RC-" + UUID.randomUUID(), "IF-001", ice, "LEGAL_FORM", activity, purpose,
                "+212500000000", "merchant@example.test", address("Headquarters"), "5411",
                "123456789012345678901234", representative(),
                List.of(new MerchantOnboardingService.BeneficialOwnerData(null, "Owner", "One", true)),
                outlets, version);
    }

    private static MerchantOnboardingService.OutletData outlet(String code, boolean principal) {
        return new MerchantOnboardingService.OutletData(null, code, "Outlet " + code,
                principal, true, address("Address " + code), "+212511111111",
                code.toLowerCase() + "@example.test", representative());
    }

    private static MerchantOnboardingService.AddressData address(String line1) {
        return new MerchantOnboardingService.AddressData(line1, null, "District", "Rabat",
                "Rabat-Sale-Kenitra", "10000", "MA");
    }

    private static MerchantOnboardingService.RepresentativeData representative() {
        return new MerchantOnboardingService.RepresentativeData("MR", "Legal", "Representative",
                LocalDate.of(1980, 1, 1), "+212522222222", "representative@example.test",
                "CIN", "AB1234", "MA", "MA");
    }
}
