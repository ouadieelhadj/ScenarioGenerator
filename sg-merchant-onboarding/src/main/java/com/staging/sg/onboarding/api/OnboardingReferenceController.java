package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.OnboardingReferenceValue;
import com.staging.sg.onboarding.domain.OnboardingFieldRule;
import com.staging.sg.onboarding.repository.OnboardingFieldRuleRepository;
import com.staging.sg.onboarding.repository.OnboardingReferenceValueRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/merchant-onboarding/v2/references")
public class OnboardingReferenceController {
    private final OnboardingReferenceValueRepository references;
    private final OnboardingFieldRuleRepository fieldRules;
    public OnboardingReferenceController(OnboardingReferenceValueRepository references,
            OnboardingFieldRuleRepository fieldRules) {
        this.references = references;
        this.fieldRules = fieldRules;
    }

    @GetMapping("/field-rules/{merchantType}")
    public List<FieldRuleView> fieldRules(@PathVariable String merchantType) {
        return fieldRules.findByIdMerchantTypeAndActiveTrueOrderByIdFieldPathAsc(
                merchantType.toUpperCase()).stream().map(FieldRuleView::from).toList();
    }

    @GetMapping("/{category}")
    public List<ReferenceView> list(@PathVariable String category,
            @RequestParam(required = false) String q) {
        String search = q == null ? "" : q.trim().toLowerCase();
        return references.findByIdCategoryAndActiveTrueOrderByLabelAsc(category.toUpperCase()).stream()
                .filter(value -> search.isEmpty() || value.code().toLowerCase().contains(search)
                        || value.label().toLowerCase().contains(search))
                .map(ReferenceView::from).toList();
    }

    public record ReferenceView(String category, String code, String label) {
        static ReferenceView from(OnboardingReferenceValue value) {
            return new ReferenceView(value.category(), value.code(), value.label());
        }
    }
    public record FieldRuleView(String fieldPath, boolean required, Integer maxLength) {
        static FieldRuleView from(OnboardingFieldRule value) {
            return new FieldRuleView(value.fieldPath(), value.required(), value.maxLength());
        }
    }
}
