package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.domain.OnboardingOutboxStatus;
import com.staging.sg.onboarding.domain.OnboardingWay4ExportState;
import com.staging.sg.onboarding.repository.MerchantOnboardingCaseRepository;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import com.staging.sg.onboarding.repository.OnboardingWay4ExportStateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class OnboardingOutboxCompletionIndependenceTest {

    @Test
    void finalWay4FailureDoesNotFailAcquiringProvisioning() {
        OnboardingOutboxEventRepository events = mock(OnboardingOutboxEventRepository.class);
        MerchantOnboardingCaseRepository cases = mock(MerchantOnboardingCaseRepository.class);
        OnboardingWay4ExportStateRepository way4States = mock(OnboardingWay4ExportStateRepository.class);
        OnboardingOutboxEvent event = mock(OnboardingOutboxEvent.class);
        UUID eventId = UUID.randomUUID();

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(event.status()).thenReturn(OnboardingOutboxStatus.PROCESSING,
                OnboardingOutboxStatus.FAILED_FINAL);
        when(event.eventType()).thenReturn("way4.export.requested");
        when(event.aggregateId()).thenReturn(UUID.randomUUID());
        OnboardingWay4ExportState state = mock(OnboardingWay4ExportState.class);
        when(way4States.findById(event.aggregateId())).thenReturn(Optional.of(state));

        new OnboardingOutboxCompletionService(events, cases, way4States)
                .failure(eventId, "WAY4_FUNCTIONAL_REJECT", "Rejected by WAY4", false);

        verify(event).fail("WAY4_FUNCTIONAL_REJECT", "Rejected by WAY4", false);
        verify(state).failed("WAY4_FUNCTIONAL_REJECT", "Rejected by WAY4", false);
        verify(way4States).save(state);
        verify(events).save(event);
        verifyNoInteractions(cases);
    }
}
