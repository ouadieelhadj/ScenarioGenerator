package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OnboardingOutboxDispatcher {
    private final boolean enabled;
    private final int batchSize;
    private final OnboardingOutboxReservationService reservations;
    private final OnboardingOutboxCompletionService completions;
    private final AcquiringProvisioningV2Port acquiring;
    private final ObjectMapper objectMapper;

    public OnboardingOutboxDispatcher(
            @Value("${merchant-onboarding.outbox.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.outbox.batch-size:20}") int batchSize,
            OnboardingOutboxReservationService reservations,
            OnboardingOutboxCompletionService completions,
            AcquiringProvisioningV2Port acquiring, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.reservations = reservations;
        this.completions = completions;
        this.acquiring = acquiring;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${merchant-onboarding.outbox.poll-delay-ms:5000}")
    public void dispatch() {
        if (!enabled) return;
        for (OnboardingOutboxReservationService.ReservedEvent event : reservations.reserve(batchSize))
            dispatch(event);
    }

    private void dispatch(OnboardingOutboxReservationService.ReservedEvent event) {
        try {
            MerchantProvisioningCommandV2 command = objectMapper.readValue(
                    event.payloadJson(), MerchantProvisioningCommandV2.class);
            completions.result(event.eventId(), acquiring.provision(command,
                    event.idempotencyKey(), event.correlationId()));
        } catch (ProvisioningTransportException exception) {
            completions.failure(event.eventId(), "ACQUIRING_TRANSPORT",
                    exception.getMessage(), exception.retryable());
        } catch (JsonProcessingException exception) {
            completions.failure(event.eventId(), "INVALID_OUTBOX_PAYLOAD",
                    "Stored provisioning event cannot be deserialized", false);
        } catch (RuntimeException exception) {
            completions.failure(event.eventId(), "UNEXPECTED_DISPATCH_FAILURE",
                    exception.getClass().getSimpleName(), true);
        }
    }
}
