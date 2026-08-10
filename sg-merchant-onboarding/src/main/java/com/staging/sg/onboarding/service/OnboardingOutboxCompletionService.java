package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.MerchantProvisioningResultV2;
import com.staging.sg.onboarding.repository.MerchantOnboardingCaseRepository;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OnboardingOutboxCompletionService {
    private final OnboardingOutboxEventRepository events;
    private final MerchantOnboardingCaseRepository cases;

    public OnboardingOutboxCompletionService(OnboardingOutboxEventRepository events,
            MerchantOnboardingCaseRepository cases) {
        this.events = events;
        this.cases = cases;
    }

    @Transactional
    public void result(UUID eventId, MerchantProvisioningResultV2 result) {
        OnboardingOutboxEvent event = processing(eventId);
        if (result.isComplete() && !result.hasRetryableObjects()
                && result.merchantId() != null && result.merchantAcceptorId() != null
                && !result.merchantAcceptorId().isBlank()) {
            MerchantOnboardingCase dossier = dossier(event.aggregateId());
            if (dossier.status() == OnboardingStatus.APPROVED
                    || dossier.status() == OnboardingStatus.QUEUED_FOR_PROVISIONING
                    || dossier.status() == OnboardingStatus.PROVISIONING_FAILED) {
                dossier.startProvisioning();
                dossier.provisioned(result.merchantId(), result.merchantAcceptorId());
                cases.save(dossier);
            }
            event.complete();
        } else if (result.hasRetryableObjects()) {
            event.fail("ACQUIRING_PARTIAL_RETRYABLE",
                    "Acquiring reported retryable provisioning objects", true);
        } else {
            event.fail("ACQUIRING_FUNCTIONAL_FAILURE",
                    "Acquiring reported a final partial or failed result", false);
            markDossierFailed(event.aggregateId());
        }
        events.save(event);
    }

    @Transactional
    public void failure(UUID eventId, String code, String message, boolean retryable) {
        OnboardingOutboxEvent event = processing(eventId);
        event.fail(code, message, retryable);
        if (event.status() == OnboardingOutboxStatus.FAILED_FINAL)
            markDossierFailed(event.aggregateId());
        events.save(event);
    }

    private OnboardingOutboxEvent processing(UUID id) {
        OnboardingOutboxEvent event = events.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + id));
        if (event.status() != OnboardingOutboxStatus.PROCESSING)
            throw new IllegalStateException("Outbox event is not processing");
        return event;
    }

    private MerchantOnboardingCase dossier(UUID id) {
        return cases.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding case not found: " + id));
    }

    private void markDossierFailed(UUID caseId) {
        MerchantOnboardingCase dossier = dossier(caseId);
        if (dossier.status() == OnboardingStatus.APPROVED
                || dossier.status() == OnboardingStatus.QUEUED_FOR_PROVISIONING
                || dossier.status() == OnboardingStatus.PROVISIONING_FAILED) {
            if (dossier.status() != OnboardingStatus.PROVISIONING_FAILED) {
                dossier.startProvisioning();
                dossier.provisioningFailed();
            }
            cases.save(dossier);
        }
    }
}
