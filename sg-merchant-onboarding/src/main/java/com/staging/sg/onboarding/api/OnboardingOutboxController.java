package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.service.OnboardingOutboxAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/onboarding/v2/outbox")
public class OnboardingOutboxController {
    private final OnboardingOutboxAdministrationService service;

    public OnboardingOutboxController(OnboardingOutboxAdministrationService service) {
        this.service = service;
    }

    @PostMapping("/{eventId}/retry")
    @PreAuthorize("hasAuthority('ONBOARDING_RETRY')")
    public RetryView retry(@PathVariable UUID eventId, Authentication authentication,
            @Valid @RequestBody RetryRequest request) {
        return RetryView.from(service.retry(eventId, authentication.getName(), request.reason()));
    }

    public record RetryRequest(@NotBlank String reason) {}
    public record RetryView(UUID eventId, String status, int attempts) {
        static RetryView from(OnboardingOutboxEvent value) {
            return new RetryView(value.id(), value.status().name(), value.attempts());
        }
    }
}
