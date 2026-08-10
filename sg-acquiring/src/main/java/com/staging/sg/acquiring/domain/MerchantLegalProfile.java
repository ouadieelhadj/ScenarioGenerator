package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_legal_profile")
public class MerchantLegalProfile {
    @Id @Column(name = "merchant_id") private UUID merchantId;
    @Column(name = "tax_identifier", length = 64) private String taxIdentifier;
    @Column(length = 64) private String ice;
    @Column(name = "legal_form", length = 96) private String legalForm;
    @Column(name = "business_activity", length = 255) private String businessActivity;
    @Column(name = "association_purpose", length = 500) private String associationPurpose;
    @Column(name = "primary_phone", length = 32) private String primaryPhone;
    @Column(name = "primary_email", length = 254) private String primaryEmail;
    @Column(length = 24) private String rib;
    @Column(name = "address_line1", length = 255) private String addressLine1;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 120) private String district;
    @Column(length = 120) private String city;
    @Column(length = 120) private String region;
    @Column(name = "postal_code", length = 24) private String postalCode;
    @Column(length = 2) private String country;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MerchantLegalProfile() {}

    public static MerchantLegalProfile create(UUID merchantId, String taxIdentifier, String ice,
            String legalForm, String businessActivity, String associationPurpose,
            String primaryPhone, String primaryEmail, String rib, String addressLine1,
            String addressLine2, String district, String city, String region,
            String postalCode, String country) {
        if (merchantId == null || AcceptanceProduct.blank(primaryPhone)
                || AcceptanceProduct.blank(primaryEmail) || AcceptanceProduct.blank(rib)
                || rib.replace(" ", "").length() > 24 || AcceptanceProduct.blank(addressLine1)
                || AcceptanceProduct.blank(city) || country == null || !country.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("MER-003/MER-006/MER-007/ADR-001: invalid legal profile");
        MerchantLegalProfile value = new MerchantLegalProfile();
        value.merchantId = merchantId;
        value.taxIdentifier = normalize(taxIdentifier);
        value.ice = normalize(ice);
        value.legalForm = normalize(legalForm);
        value.businessActivity = normalize(businessActivity);
        value.associationPurpose = normalize(associationPurpose);
        value.primaryPhone = primaryPhone.trim();
        value.primaryEmail = primaryEmail.trim();
        value.rib = rib.replace(" ", "");
        value.addressLine1 = addressLine1.trim();
        value.addressLine2 = normalize(addressLine2);
        value.district = normalize(district);
        value.city = city.trim();
        value.region = normalize(region);
        value.postalCode = normalize(postalCode);
        value.country = country;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID merchantId() { return merchantId; }
    public String ice() { return ice; }
    public String rib() { return rib; }
    public String city() { return city; }
}
