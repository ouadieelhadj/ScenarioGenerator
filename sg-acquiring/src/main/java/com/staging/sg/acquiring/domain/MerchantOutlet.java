package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "merchant_outlet", uniqueConstraints =
        @UniqueConstraint(name = "uk_merchant_outlet_code",
                columnNames = {"merchant_id", "outlet_code"}))
public class MerchantOutlet {
    @Id
    private UUID id;
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;
    @Column(name = "outlet_code", nullable = false, length = 64, updatable = false)
    private String outletCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 120) private String district;
    @Column(length = 120) private String city;
    @Column(length = 120) private String region;
    @Column(name = "postal_code", length = 24) private String postalCode;
    @Column(nullable = false, length = 2)
    private String country;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean principal;
    @Column(name = "contact_phone", length = 32) private String contactPhone;
    @Column(name = "contact_email", length = 254) private String contactEmail;
    @Column(name = "responsible_title", length = 32) private String responsibleTitle;
    @Column(name = "responsible_first_name", length = 96) private String responsibleFirstName;
    @Column(name = "responsible_last_name", length = 96) private String responsibleLastName;
    @Column(name = "responsible_birth_date") private LocalDate responsibleBirthDate;
    @Column(name = "responsible_phone", length = 32) private String responsiblePhone;
    @Column(name = "responsible_email", length = 254) private String responsibleEmail;
    @Column(name = "responsible_id_type", length = 32) private String responsibleIdType;
    @Column(name = "responsible_id_number", length = 64) private String responsibleIdNumber;
    @Column(name = "responsible_residence_country", length = 2) private String responsibleResidenceCountry;
    @Column(name = "responsible_nationality", length = 2) private String responsibleNationality;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private long version;

    protected MerchantOutlet() {}

    public static MerchantOutlet active(UUID merchantId, String outletCode,
            String name, String addressLine, String country) {
        if (merchantId == null || AcceptanceProduct.blank(outletCode)
                || AcceptanceProduct.blank(name) || AcceptanceProduct.blank(addressLine)
                || country == null || !country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Invalid merchant outlet");
        }
        MerchantOutlet value = new MerchantOutlet();
        value.id = UUID.randomUUID();
        value.merchantId = merchantId;
        value.outletCode = outletCode;
        value.name = name;
        value.addressLine = addressLine;
        value.country = country;
        value.active = true;
        value.principal = false;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void enrich(boolean principal, String addressLine1, String addressLine2,
            String district, String city, String region, String postalCode, String country,
            String contactPhone, String contactEmail, String responsibleTitle,
            String responsibleFirstName, String responsibleLastName,
            LocalDate responsibleBirthDate, String responsiblePhone, String responsibleEmail,
            String responsibleIdType, String responsibleIdNumber,
            String responsibleResidenceCountry, String responsibleNationality) {
        if (AcceptanceProduct.blank(addressLine1) || AcceptanceProduct.blank(city)
                || country == null || !country.matches("[A-Z]{2}")
                || AcceptanceProduct.blank(contactPhone) || AcceptanceProduct.blank(contactEmail)
                || AcceptanceProduct.blank(responsibleFirstName)
                || AcceptanceProduct.blank(responsibleLastName)
                || AcceptanceProduct.blank(responsiblePhone)
                || AcceptanceProduct.blank(responsibleEmail))
            throw new IllegalArgumentException("PDV-003/PDV-004: invalid structured outlet");
        this.principal = principal;
        this.addressLine = addressLine1.trim();
        this.addressLine2 = normalize(addressLine2);
        this.district = normalize(district);
        this.city = city.trim();
        this.region = normalize(region);
        this.postalCode = normalize(postalCode);
        this.country = country;
        this.contactPhone = contactPhone.trim();
        this.contactEmail = contactEmail.trim();
        this.responsibleTitle = normalize(responsibleTitle);
        this.responsibleFirstName = responsibleFirstName.trim();
        this.responsibleLastName = responsibleLastName.trim();
        this.responsibleBirthDate = responsibleBirthDate;
        this.responsiblePhone = responsiblePhone.trim();
        this.responsibleEmail = responsibleEmail.trim();
        this.responsibleIdType = normalize(responsibleIdType);
        this.responsibleIdNumber = normalize(responsibleIdNumber);
        this.responsibleResidenceCountry = country(responsibleResidenceCountry);
        this.responsibleNationality = country(responsibleNationality);
        this.updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String country(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("PDV-004: invalid responsible country");
        return value;
    }

    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public String outletCode() { return outletCode; }
    public String name() { return name; }
    public String addressLine() { return addressLine; }
    public String country() { return country; }
    public boolean isActive() { return active; }
    public boolean principal() { return principal; }
    public String city() { return city; }
    public String contactPhone() { return contactPhone; }
    public String contactEmail() { return contactEmail; }
    public LocalDate responsibleBirthDate() { return responsibleBirthDate; }
    public String responsibleIdType() { return responsibleIdType; }
    public String responsibleIdNumber() { return responsibleIdNumber; }
    public String responsibleResidenceCountry() { return responsibleResidenceCountry; }
    public String responsibleNationality() { return responsibleNationality; }
}
