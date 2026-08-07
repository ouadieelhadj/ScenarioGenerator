package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.MerchantOnboardingCase;
import com.staging.sg.onboarding.port.MerchantProvisioningCommand;
import com.staging.sg.onboarding.service.MerchantOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
public class WorkflowController {
    private final MerchantOnboardingService service;

    public WorkflowController(MerchantOnboardingService service) { this.service = service; }

    @GetMapping("/api/workflow/requests/mine")
    public List<MerchantOnboardingController.WorkflowView> operations(
            Authentication authentication) {
        return service.operations(authentication.getName()).stream().map(MerchantOnboardingController.WorkflowView::from).toList();
    }

    @GetMapping("/api/workflow/approvals/mine")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_APPROVE')")
    public List<MerchantOnboardingController.WorkflowView> approvals() {
        return service.approvals().stream().map(MerchantOnboardingController.WorkflowView::from).toList();
    }

    @PostMapping("/api/workflow/approvals/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_APPROVE')")
    public MerchantOnboardingController.DossierView approve(@PathVariable long id,
            Authentication authentication) {
        return MerchantOnboardingController.DossierView.from(service.approve(id, authentication.getName()));
    }

    @PostMapping("/api/workflow/approvals/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_APPROVE')")
    public MerchantOnboardingController.DossierView reject(@PathVariable long id,
            Authentication authentication, @Valid @RequestBody RejectionRequest request) {
        MerchantOnboardingCase value = service.reject(id, authentication.getName(), request.reason());
        return MerchantOnboardingController.DossierView.from(value);
    }

    @GetMapping("/api/merchant-onboarding/v1/batches/pending")
    public List<MerchantProvisioningCommand> exportPendingBatch() { return service.exportPendingBatch(); }

    @PostMapping("/api/merchant-onboarding/v1/batches/run")
    @PreAuthorize("hasAnyRole('ADMIN','BACK_OFFICE') or hasAuthority('ONBOARDING_PROVISION')")
    public List<MerchantOnboardingController.ProvisioningView> runBatch(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean retryFailed,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return service.runBatch(limit, retryFailed, correlationId).stream()
                .map(MerchantOnboardingController.ProvisioningView::from).toList();
    }

    public record RejectionRequest(@NotBlank String reason) {}
}
