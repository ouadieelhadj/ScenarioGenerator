package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_beneficial_owner")
public class OnboardingBeneficialOwner {
    @Id private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "first_name", nullable = false, length = 96) private String firstName;
    @Column(name = "last_name", nullable = false, length = 96) private String lastName;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected OnboardingBeneficialOwner() {}

    public static OnboardingBeneficialOwner create(UUID caseId, UUID id,
            String firstName, String lastName, boolean active) {
        if (caseId == null || invalidName(firstName) || invalidName(lastName))
            throw new IllegalArgumentException("MER-005: invalid beneficial owner");
        OnboardingBeneficialOwner value = new OnboardingBeneficialOwner();
        value.id = id == null ? UUID.randomUUID() : id;
        value.caseId = caseId;
        value.firstName = firstName.trim();
        value.lastName = lastName.trim();
        value.active = active;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void change(String firstName, String lastName, boolean active) {
        if (invalidName(firstName) || invalidName(lastName))
            throw new IllegalArgumentException("MER-005: invalid beneficial owner");
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.active = active;
        this.updatedAt = Instant.now();
    }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    private static boolean invalidName(String value) {
        return value == null || value.isBlank() || value.trim().length() > 96;
    }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public boolean active() { return active; }
}
