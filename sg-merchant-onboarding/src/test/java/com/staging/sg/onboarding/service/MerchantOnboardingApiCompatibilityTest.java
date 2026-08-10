package com.staging.sg.onboarding.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantOnboardingApiCompatibilityTest {
    @Test
    void v1PathAndPayloadRemainUnchanged() {
        assertEquals("/api/merchant-onboarding/v1",
                MerchantOnboardingController.class.getAnnotation(RequestMapping.class).value()[0]);
        List<String> components = Arrays.stream(MerchantOnboardingController.DossierRequest.class
                .getRecordComponents()).map(RecordComponent::getName).toList();
        assertEquals(List.of("legalName", "tradingName", "registrationNumber", "country", "mcc",
                "settlementAccountReference", "settlementCurrency", "productId", "acceptanceChannel",
                "outletCode", "outletName", "outletAddress", "terminalCount"), components);
    }

    @Test
    void v2PathAndPayloadExposeIncrementOneCollections() {
        assertEquals("/api/merchant-onboarding/v2",
                MerchantOnboardingV2Controller.class.getAnnotation(RequestMapping.class).value()[0]);
        List<String> components = Arrays.stream(MerchantOnboardingV2Controller.DossierV2Request.class
                .getRecordComponents()).map(RecordComponent::getName).toList();
        assertTrue(components.containsAll(List.of("merchantType", "organizationLegalNature",
                "headquartersAddress", "representative", "beneficialOwners", "outlets", "rib",
                "version")));

        List<String> responseComponents = Arrays.stream(MerchantOnboardingV2Controller.DossierV2View.class
                .getRecordComponents()).map(RecordComponent::getName).toList();
        assertTrue(responseComponents.contains("rib"));

        List<String> outletComponents = Arrays.stream(MerchantOnboardingV2Controller.OutletView.class
                .getRecordComponents()).map(RecordComponent::getName).toList();
        assertTrue(outletComponents.contains("responsible"));
    }

    @Test
    void v1ErrorShapeStaysUnchangedWhileV2AddsStableRequirementMetadata() {
        var v1 = new OnboardingExceptionHandler().badRequest(
                new IllegalArgumentException("PDV-002: outlets requires one principal"));
        assertEquals(List.of("error"), v1.getBody().keySet().stream().sorted().toList());

        var v2 = new OnboardingV2ExceptionHandler().badRequest(
                new IllegalArgumentException("PDV-002: outlets requires one principal"));
        assertEquals("PDV-002", v2.getBody().get("code"));
        assertEquals("outlets", v2.getBody().get("field"));
    }
}
