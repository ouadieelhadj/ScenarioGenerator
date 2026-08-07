package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.service.MerchantOnboardingProvisioningService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/acquiring/v1/merchant-onboarding")
public class AcquiringOnboardingController {
    private final MerchantOnboardingProvisioningService service;

    public AcquiringOnboardingController(MerchantOnboardingProvisioningService service) {
        this.service = service;
    }

    @PostMapping
    public OnboardingProvisioningResult provision(@RequestBody OnboardingProvisioningRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return service.provision(request, idempotencyKey, correlationId);
    }

    public record OnboardingProvisioningRequest(UUID onboardingCaseId,
            String onboardingReference, String acquirerId, String legalName,
            String tradingName, String registrationNumber, String country, String mcc,
            String settlementAccountReference, String settlementCurrency, UUID productId,
            String acceptanceChannel, Outlet outlet, String maker, String checker) {}
    public record Outlet(String code, String name, String address, int terminalCount) {}
    public record OnboardingProvisioningResult(UUID merchantId, String merchantAcceptorId,
            List<TerminalResult> terminals) {}
    public record TerminalResult(UUID terminalDeviceId, String terminalId) {}
}
