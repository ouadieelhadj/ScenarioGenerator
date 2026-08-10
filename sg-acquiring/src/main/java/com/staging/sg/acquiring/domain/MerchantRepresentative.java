package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "merchant_representative")
public class MerchantRepresentative {
    @Id private UUID id;
    @Column(name = "merchant_id", nullable = false, updatable = false) private UUID merchantId;
    @Column(length = 32) private String title;
    @Column(name = "first_name", nullable = false, length = 96) private String firstName;
    @Column(name = "last_name", nullable = false, length = 96) private String lastName;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Column(nullable = false, length = 32) private String phone;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "id_type", nullable = false, length = 32) private String idType;
    @Column(name = "id_number", nullable = false, length = 64) private String idNumber;
    @Column(name = "residence_country", nullable = false, length = 2) private String residenceCountry;
    @Column(nullable = false, length = 2) private String nationality;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MerchantRepresentative() {}

    public static MerchantRepresentative active(UUID merchantId, String title, String firstName,
            String lastName, LocalDate birthDate, String phone, String email, String idType,
            String idNumber, String residenceCountry, String nationality) {
        if (merchantId == null || AcceptanceProduct.blank(firstName) || AcceptanceProduct.blank(lastName)
                || AcceptanceProduct.blank(phone) || AcceptanceProduct.blank(email)
                || AcceptanceProduct.blank(idType) || AcceptanceProduct.blank(idNumber)
                || residenceCountry == null || !residenceCountry.matches("[A-Z]{2}")
                || nationality == null || !nationality.matches("[A-Z]{2}"))
            throw new IllegalArgumentException("MER-004: invalid representative");
        MerchantRepresentative value = new MerchantRepresentative();
        value.id = UUID.randomUUID(); value.merchantId = merchantId; value.title = title;
        value.firstName = firstName.trim(); value.lastName = lastName.trim(); value.birthDate = birthDate;
        value.phone = phone.trim(); value.email = email.trim(); value.idType = idType.trim();
        value.idNumber = idNumber.trim(); value.residenceCountry = residenceCountry;
        value.nationality = nationality; value.active = true; value.createdAt = Instant.now();
        value.updatedAt = value.createdAt; return value;
    }

    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public boolean active() { return active; }
}
