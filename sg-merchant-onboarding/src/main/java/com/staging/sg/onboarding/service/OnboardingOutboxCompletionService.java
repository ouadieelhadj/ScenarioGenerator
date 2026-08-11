package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.MerchantProvisioningResultV2;
import com.staging.sg.onboarding.repository.MerchantOnboardingCaseRepository;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import com.staging.sg.onboarding.repository.OnboardingWay4ExportStateRepository;
import com.staging.sg.onboarding.port.Way4ConnectorPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OnboardingOutboxCompletionService {
    private static final String ACQUIRING_EVENT_TYPE = "merchant.provisioning.requested";

    private final OnboardingOutboxEventRepository events;
    private final MerchantOnboardingCaseRepository cases;
    private final OnboardingWay4ExportStateRepository way4States;

    public OnboardingOutboxCompletionService(OnboardingOutboxEventRepository events,
            MerchantOnboardingCaseRepository cases, OnboardingWay4ExportStateRepository way4States) {
        this.events = events;
        this.cases = cases;
        this.way4States = way4States;
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
        if ("way4.export.requested".equals(event.eventType())) {
            way4States.findById(event.aggregateId()).ifPresent(state -> {
                state.failed(code, message, retryable);
                way4States.save(state);
            });
        }
        if (event.status() == OnboardingOutboxStatus.FAILED_FINAL
                && ACQUIRING_EVENT_TYPE.equals(event.eventType())) {
            markDossierFailed(event.aggregateId());
        }
        events.save(event);
    }

    @Transactional
    public void way4Result(UUID eventId, Way4ConnectorPort.Result result) {
        OnboardingOutboxEvent event = processing(eventId);
        OnboardingWay4ExportState state = way4States.findById(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("WAY4 export state not found"));
        state.generated(result.fileId());
        way4States.save(state);
        event.complete();
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
