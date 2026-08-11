package com.staging.sg.onboarding.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MerchantOnboardingCaseTest {
    @Test
    void enforcesMakerCheckerAndApprovalBeforeProvisioning() {
        MerchantOnboardingCase dossier = completeDossier();
        dossier.submitKyc("merchant.user");
        dossier.validateKyc("kyc.user");
        dossier.submit("merchant.user");

        assertThatThrownBy(() -> dossier.approve("merchant.user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different");

        dossier.approve("checker.user");
        dossier.startProvisioning();
        UUID acquiringMerchantId = UUID.randomUUID();
        dossier.provisioned(acquiringMerchantId, "123456789012345");

        assertThat(dossier.status()).isEqualTo(OnboardingStatus.PROVISIONED);
        assertThat(dossier.acquiringMerchantId()).isEqualTo(acquiringMerchantId);
        assertThat(dossier.merchantAcceptorId()).isEqualTo("123456789012345");
    }

    @Test
    void rejectsProvisioningBeforeApproval() {
        MerchantOnboardingCase dossier = completeDossier();
        assertThatThrownBy(dossier::startProvisioning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    void requiresTerminalForTpeDossier() {
        MerchantOnboardingCase dossier = MerchantOnboardingCase.prospect(
                UUID.randomUUID(), "ACQ-01", "commercial.user");
        assertThatThrownBy(() -> dossier.updateDossier("Legal", "Shop", "RC-1", "MA", "5411",
                "ACC-1", "504", UUID.randomUUID(), "TPE", "OUT-1", "Outlet", "Address", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void requiresExplicitDestinationBeforeMakerSubmission() {
        MerchantOnboardingCase dossier = MerchantOnboardingCase.prospect(
                UUID.randomUUID(), "ACQ-01", "commercial.user");
        dossier.updateDossier("Merchant Legal", "Merchant Shop", "RC-DEST", "MA", "5411",
                "ACC-001", "504", UUID.randomUUID(), "BOTH", "OUT-01", "Main outlet", "Rabat", 2);
        dossier.submitKyc("merchant.user");
        dossier.validateKyc("kyc.user");
        assertThatThrownBy(() -> dossier.submit("merchant.user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destination");
        dossier.selectProvisioningDestination(ProvisioningDestination.FUTURPAYMENT);
        dossier.submit("merchant.user");
        assertThat(dossier.status()).isEqualTo(OnboardingStatus.PENDING_APPROVAL);
    }

    private static MerchantOnboardingCase completeDossier() {
        MerchantOnboardingCase dossier = MerchantOnboardingCase.prospect(
                UUID.randomUUID(), "ACQ-01", "commercial.user");
        dossier.updateDossier("Merchant Legal", "Merchant Shop", "RC-123", "MA", "5411",
                "ACC-001", "504", UUID.randomUUID(), "BOTH", "OUT-01", "Main outlet", "Rabat", 2);
        dossier.selectProvisioningDestination(ProvisioningDestination.FUTURPAYMENT);
        return dossier;
    }
}
