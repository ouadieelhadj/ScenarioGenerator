package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.OnboardingReferenceValue;
import com.staging.sg.onboarding.repository.OnboardingReferenceValueRepository;
import com.staging.sg.onboarding.service.OnboardingReferenceAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal/onboarding/v2/reference-administration")
@PreAuthorize("hasAuthority('REFERENCE_ADMIN')")
public class OnboardingReferenceAdministrationController {
    private final OnboardingReferenceAdministrationService service;
    private final OnboardingReferenceValueRepository references;

    public OnboardingReferenceAdministrationController(OnboardingReferenceAdministrationService service,
            OnboardingReferenceValueRepository references) {
        this.service = service; this.references = references;
    }

    @GetMapping("/{category}")
    public List<View> list(@PathVariable String category) {
        return references.findByIdCategoryOrderByLabelAsc(category.toUpperCase()).stream()
                .map(View::from).toList();
    }
    @PostMapping
    public View create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        return View.from(service.create(request.category(), request.code(), request.label(),
                request.attributesJson(), auth.getName()));
    }
    @PutMapping("/{category}/{code}")
    public View update(@PathVariable String category, @PathVariable String code,
            @Valid @RequestBody UpdateRequest request, Authentication auth) {
        return View.from(service.update(category, code, request.label(), request.attributesJson(),
                request.version(), auth.getName()));
    }
    @PostMapping("/{category}/{code}/activation")
    public View activation(@PathVariable String category, @PathVariable String code,
            @RequestBody ActivationRequest request, Authentication auth) {
        return View.from(service.setActive(category, code, request.active(), request.version(), auth.getName()));
    }

    public record CreateRequest(@NotBlank String category, @NotBlank String code,
            @NotBlank String label, String attributesJson) {}
    public record UpdateRequest(@NotBlank String label, String attributesJson, long version) {}
    public record ActivationRequest(boolean active, long version) {}
    public record View(String category, String code, String label, boolean active,
            String attributesJson, long version) {
        static View from(OnboardingReferenceValue value) { return new View(value.category(),
                value.code(), value.label(), value.active(), value.attributesJson(), value.version()); }
    }
}
