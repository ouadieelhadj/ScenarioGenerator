package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.service.OnboardingPricingAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/onboarding/v2/pricing")
public class OnboardingPricingAdministrationController {
    private final OnboardingPricingAdministrationService service;
    public OnboardingPricingAdministrationController(OnboardingPricingAdministrationService service) {
        this.service = service;
    }
    @PostMapping("/packs")
    @PreAuthorize("hasAuthority('PRICING_ADMIN')")
    public PackView createPack(@Valid @RequestBody PackRequest request, Authentication auth) {
        return PackView.from(service.createPack(request.code(), request.label(), auth.getName()));
    }
    @PostMapping("/packs/{code}/versions")
    @PreAuthorize("hasAuthority('PRICING_ADMIN')")
    public VersionView createVersion(@PathVariable String code,
            @Valid @RequestBody VersionRequest request, Authentication auth) {
        return VersionView.from(service.createVersion(code, request.version(),
                request.termsJson(), auth.getName()));
    }
    @PostMapping("/packs/{code}/versions/{version}/activate")
    @PreAuthorize("hasAuthority('PRICING_APPROVE')")
    public VersionView activate(@PathVariable String code, @PathVariable int version,
            Authentication auth) {
        return VersionView.from(service.activate(code, version, auth.getName()));
    }
    @PostMapping("/deviations")
    @PreAuthorize("hasAuthority('PRICING_ADMIN')")
    public DeviationView request(@Valid @RequestBody DeviationRequest request, Authentication auth) {
        return DeviationView.from(service.requestDeviation(request.outletProductId(),
                request.packCode(), request.packVersion(), request.afterJson(),
                request.reason(), auth.getName()));
    }
    @PostMapping("/deviations/{id}/approve")
    @PreAuthorize("hasAuthority('PRICING_APPROVE')")
    public DeviationView approve(@PathVariable UUID id, @RequestBody DecisionRequest request,
            Authentication auth) {
        return DeviationView.from(service.approveDeviation(id, request.version(), auth.getName()));
    }
    @PostMapping("/deviations/{id}/reject")
    @PreAuthorize("hasAuthority('PRICING_APPROVE')")
    public DeviationView reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request,
            Authentication auth) {
        return DeviationView.from(service.rejectDeviation(id, request.version(),
                request.reason(), auth.getName()));
    }
    public record PackRequest(@NotBlank String code, @NotBlank String label) {}
    public record VersionRequest(@Min(1) int version, @NotBlank String termsJson) {}
    public record DeviationRequest(@NotNull UUID outletProductId, @NotBlank String packCode,
            @Min(1) int packVersion, @NotBlank String afterJson, @NotBlank String reason) {}
    public record DecisionRequest(long version, String reason) {}
    public record PackView(String code, String label, String status, long version) {
        static PackView from(PricingPack value) { return new PackView(value.code(), value.label(), value.status().name(), value.version()); }
    }
    public record VersionView(UUID id, String packCode, int version, String status) {
        static VersionView from(PricingPackVersion value) { return new VersionView(value.id(), value.packCode(), value.versionNumber(), value.status().name()); }
    }
    public record DeviationView(UUID id, String status, long version) {
        static DeviationView from(TariffDeviation value) { return new DeviationView(value.id(), value.status().name(), value.version()); }
    }
}
