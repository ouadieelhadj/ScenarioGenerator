package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "onboarding_outlet", uniqueConstraints =
        @UniqueConstraint(name = "uk_onboarding_outlet_code", columnNames = {"case_id", "outlet_code"}))
public class OnboardingOutlet {
    @Id private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "outlet_code", nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false) private boolean principal;
    @Column(nullable = false) private boolean active;
    @Column(name = "address_line1", nullable = false, length = 255) private String addressLine1;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 120) private String district;
    @Column(nullable = false, length = 120) private String city;
    @Column(length = 120) private String region;
    @Column(name = "postal_code", length = 24) private String postalCode;
    @Column(nullable = false, length = 2) private String country;
    @Column(name = "contact_phone", nullable = false, length = 32) private String contactPhone;
    @Column(name = "contact_email", nullable = false, length = 254) private String contactEmail;
    @Column(name = "responsible_title", length = 32) private String responsibleTitle;
    @Column(name = "responsible_first_name", nullable = false, length = 96) private String responsibleFirstName;
    @Column(name = "responsible_last_name", nullable = false, length = 96) private String responsibleLastName;
    @Column(name = "responsible_birth_date") private LocalDate responsibleBirthDate;
    @Column(name = "responsible_phone", nullable = false, length = 32) private String responsiblePhone;
    @Column(name = "responsible_email", nullable = false, length = 254) private String responsibleEmail;
    @Column(name = "responsible_id_type", length = 32) private String responsibleIdType;
    @Column(name = "responsible_id_number", length = 64) private String responsibleIdNumber;
    @Column(name = "responsible_residence_country", length = 2) private String responsibleResidenceCountry;
    @Column(name = "responsible_nationality", length = 2) private String responsibleNationality;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected OnboardingOutlet() {}

    public static OnboardingOutlet create(UUID caseId, UUID id, String code, String name,
            boolean principal, boolean active, String addressLine1, String addressLine2,
            String district, String city, String region, String postalCode, String country,
            String contactPhone, String contactEmail, String responsibleTitle,
            String responsibleFirstName, String responsibleLastName,
            LocalDate responsibleBirthDate, String responsiblePhone, String responsibleEmail,
            String responsibleIdType, String responsibleIdNumber,
            String responsibleResidenceCountry, String responsibleNationality) {
        OnboardingOutlet value = new OnboardingOutlet();
        value.id = id == null ? UUID.randomUUID() : id;
        value.caseId = require(caseId, "caseId");
        value.change(code, name, principal, active, addressLine1, addressLine2, district,
                city, region, postalCode, country, contactPhone, contactEmail,
                responsibleTitle, responsibleFirstName, responsibleLastName,
                responsibleBirthDate, responsiblePhone, responsibleEmail, responsibleIdType,
                responsibleIdNumber, responsibleResidenceCountry, responsibleNationality);
        value.createdAt = Instant.now();
        return value;
    }

    public static OnboardingOutlet fromLegacy(UUID caseId, String code, String name,
            String address, String country) {
        return create(caseId, null, code, name, true, true, address, null, null,
                "LEGACY", null, null, country, "LEGACY", "legacy@invalid.local",
                null, "LEGACY", "LEGACY", null, "LEGACY", "legacy@invalid.local",
                null, null, null, null);
    }

    public void change(String code, String name, boolean principal, boolean active,
            String addressLine1, String addressLine2, String district, String city,
            String region, String postalCode, String country, String contactPhone,
            String contactEmail, String responsibleTitle, String responsibleFirstName,
            String responsibleLastName, LocalDate responsibleBirthDate,
            String responsiblePhone, String responsibleEmail, String responsibleIdType,
            String responsibleIdNumber, String responsibleResidenceCountry,
            String responsibleNationality) {
        this.code = text(code, "outlet.code", 64);
        this.name = text(name, "outlet.name", 160);
        this.principal = principal;
        this.active = active;
        this.addressLine1 = text(addressLine1, "outlet.address.line1", 255);
        this.addressLine2 = optional(addressLine2, 255);
        this.district = optional(district, 120);
        this.city = text(city, "outlet.address.city", 120);
        this.region = optional(region, 120);
        this.postalCode = optional(postalCode, 24);
        if (country == null || !country.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("ADR-001: outlet.address.country is invalid");
        this.country = country;
        this.contactPhone = text(contactPhone, "outlet.contact.phone", 32);
        this.contactEmail = email(contactEmail, "outlet.contact.email");
        this.responsibleTitle = optional(responsibleTitle, 32);
        this.responsibleFirstName = text(responsibleFirstName, "outlet.responsible.firstName", 96);
        this.responsibleLastName = text(responsibleLastName, "outlet.responsible.lastName", 96);
        this.responsibleBirthDate = responsibleBirthDate;
        this.responsiblePhone = text(responsiblePhone, "outlet.responsible.phone", 32);
        this.responsibleEmail = email(responsibleEmail, "outlet.responsible.email");
        this.responsibleIdType = optional(responsibleIdType, 32);
        this.responsibleIdNumber = optional(responsibleIdNumber, 64);
        this.responsibleResidenceCountry = optionalCountry(responsibleResidenceCountry,
                "outlet.responsible.residenceCountry");
        this.responsibleNationality = optionalCountry(responsibleNationality,
                "outlet.responsible.nationality");
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.principal = false;
        this.updatedAt = Instant.now();
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static String text(String value, String field, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max)
            throw new IllegalArgumentException("PDV-003/PDV-004: " + field + " is invalid");
        return value.trim();
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Value is too long");
        return value.trim();
    }
    private static String email(String value, String field) {
        String normalized = text(value, field, 254);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("PDV-004: " + field + " is invalid");
        return normalized;
    }
    private static String optionalCountry(String value, String field) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("PDV-004: " + field + " is invalid");
        return value;
    }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String code() { return code; }
    public String name() { return name; }
    public boolean principal() { return principal; }
    public boolean active() { return active; }
    public String addressLine1() { return addressLine1; }
    public String addressLine2() { return addressLine2; }
    public String district() { return district; }
    public String city() { return city; }
    public String region() { return region; }
    public String postalCode() { return postalCode; }
    public String country() { return country; }
    public String contactPhone() { return contactPhone; }
    public String contactEmail() { return contactEmail; }
    public String responsibleTitle() { return responsibleTitle; }
    public String responsibleFirstName() { return responsibleFirstName; }
    public String responsibleLastName() { return responsibleLastName; }
    public LocalDate responsibleBirthDate() { return responsibleBirthDate; }
    public String responsiblePhone() { return responsiblePhone; }
    public String responsibleEmail() { return responsibleEmail; }
    public String responsibleIdType() { return responsibleIdType; }
    public String responsibleIdNumber() { return responsibleIdNumber; }
    public String responsibleResidenceCountry() { return responsibleResidenceCountry; }
    public String responsibleNationality() { return responsibleNationality; }
    public long version() { return version; }
}
