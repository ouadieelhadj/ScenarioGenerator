package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.domain.OnboardingOutboxRetryOrder;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import com.staging.sg.onboarding.repository.OnboardingOutboxRetryOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OnboardingOutboxAdministrationService {
    private final OnboardingOutboxEventRepository events;
    private final OnboardingOutboxRetryOrderRepository retryOrders;

    public OnboardingOutboxAdministrationService(OnboardingOutboxEventRepository events,
            OnboardingOutboxRetryOrderRepository retryOrders) {
        this.events = events;
        this.retryOrders = retryOrders;
    }

    @Transactional
    public OnboardingOutboxEvent retry(UUID eventId, String actor, String reason) {
        OnboardingOutboxEvent event = events.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
        retryOrders.save(OnboardingOutboxRetryOrder.create(event, actor, reason));
        event.manualRetry(actor, reason);
        return events.save(event);
    }
}
