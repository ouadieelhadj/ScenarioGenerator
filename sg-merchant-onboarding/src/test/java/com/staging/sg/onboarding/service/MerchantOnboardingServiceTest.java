package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "jwt.secret=merchant-onboarding-test-key-not-for-runtime")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MerchantOnboardingServiceTest {
    @Autowired
    private MerchantOnboardingService service;
    @MockBean
    private AcquiringProvisioningPort acquiring;

    @Test
    void createsProspectAccountAndExposesMakerCheckerQueues() {
        Approved approved = approvedDossier();

        assertThat(service.operations("merchant")).extracting(WorkflowApprovalRequest::id)
                .containsExactly(approved.workflowId());
        assertThat(service.approvals()).isEmpty();
        assertThat(approved.dossier().status()).isEqualTo(OnboardingStatus.APPROVED);
    }

    @Test
    void immediateAndBatchUseTheSameCanonicalCommand() {
        Approved immediate = approvedDossier();
        UUID merchantId = UUID.randomUUID();
        when(acquiring.provision(any(), anyString(), anyString()))
                .thenReturn(new MerchantProvisioningResult(merchantId, "123456789012345", List.of()));

        MerchantOnboardingService.ProvisioningOutcome immediateResult = service.requestProvisioning(
                immediate.dossier().id(), ProvisioningMode.IMMEDIATE, "corr-immediate");
        assertThat(immediateResult.dossier().status()).isEqualTo(OnboardingStatus.PROVISIONED);
        MerchantOnboardingService.ProvisioningOutcome replay = service.requestProvisioning(
                immediate.dossier().id(), ProvisioningMode.IMMEDIATE, "corr-replay");
        assertThat(replay.result()).isEqualTo(immediateResult.result());

        Approved batched = approvedDossier("merchant2", "merchant2@example.test", "RC-456");
        MerchantOnboardingService.ProvisioningOutcome queued = service.requestProvisioning(
                batched.dossier().id(), ProvisioningMode.BATCH, "corr-batch");
        assertThat(queued.job().status()).isEqualTo(ProvisioningJobStatus.PENDING);
        List<MerchantProvisioningCommand> exported = service.exportPendingBatch();
        assertThat(exported).singleElement().satisfies(command -> {
            assertThat(command.onboardingCaseId()).isEqualTo(batched.dossier().id());
            assertThat(command.maker()).isEqualTo("merchant2");
            assertThat(command.checker()).isEqualTo("checker.user");
        });

        service.runBatch(100, false, "corr-run");
        var captor = org.mockito.ArgumentCaptor.forClass(MerchantProvisioningCommand.class);
        verify(acquiring, times(3)).provision(captor.capture(), anyString(), anyString());
        assertThat(captor.getAllValues().get(2)).isEqualTo(exported.getFirst());
    }

    @Test
    void failedProvisioningDoesNotInventMidOrTidAndCanBeRetried() {
        Approved approved = approvedDossier();
        service.requestProvisioning(approved.dossier().id(), ProvisioningMode.BATCH, "corr-queue");
        when(acquiring.provision(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Acquiring unavailable"))
                .thenReturn(new MerchantProvisioningResult(UUID.randomUUID(), "999999999999999", List.of()));

        MerchantOnboardingService.ProvisioningOutcome failed = service.runBatch(100, false, "corr-1").getFirst();
        assertThat(failed.dossier().status()).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
        assertThat(failed.dossier().merchantAcceptorId()).isNull();
        assertThat(failed.job().attempts()).isEqualTo(1);

        MerchantOnboardingService.ProvisioningOutcome retried = service.runBatch(100, true, "corr-2").getFirst();
        assertThat(retried.dossier().status()).isEqualTo(OnboardingStatus.PROVISIONED);
        assertThat(retried.job().attempts()).isEqualTo(2);
    }

    private Approved approvedDossier() { return approvedDossier("merchant", "merchant@example.test", "RC-123"); }

    private Approved approvedDossier(String login, String email, String registration) {
        MerchantOnboardingService.Prospect prospect = service.createProspect(login, email, "ACQ-01", "commercial.user");
        MerchantOnboardingService.DossierData data = new MerchantOnboardingService.DossierData(
                "Merchant Legal " + registration, "Merchant Shop", registration, "MA", "5411",
                "ACC-001", "504", UUID.randomUUID(), "BOTH", "OUT-01", "Main outlet", "Rabat", 2);
        service.updateDossier(prospect.dossier().id(), data, login);
        List<OnboardingDocument> kycDocuments = new java.util.ArrayList<>();
        for (DocumentType type : List.of(DocumentType.LEGAL_EXISTENCE,
                DocumentType.REPRESENTATIVE_IDENTITY, DocumentType.BANK_ACCOUNT_PROOF)) {
            kycDocuments.add(service.addDocument(prospect.dossier().id(), type,
                    "ged://merchant/" + registration + "/" + type, "application/pdf", 1024,
                    "a".repeat(64), login));
        }
        service.submitKyc(prospect.dossier().id(), login);
        for (OnboardingDocument document : kycDocuments) {
            service.reviewDocument(document.id(), true, null, "backoffice.user");
        }
        service.validateKyc(prospect.dossier().id(), "backoffice.user");
        long workflowId = service.submit(prospect.dossier().id(), login).id();
        MerchantOnboardingCase dossier = service.approve(workflowId, "checker.user");
        return new Approved(dossier, workflowId);
    }

    private record Approved(MerchantOnboardingCase dossier, long workflowId) {}
}
