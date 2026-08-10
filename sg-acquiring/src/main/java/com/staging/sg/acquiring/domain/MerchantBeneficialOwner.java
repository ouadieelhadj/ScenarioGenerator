package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_beneficial_owner")
public class MerchantBeneficialOwner {
    @Id private UUID id;
    @Column(name = "merchant_id", nullable = false, updatable = false) private UUID merchantId;
    @Column(name = "first_name", nullable = false, length = 96) private String firstName;
    @Column(name = "last_name", nullable = false, length = 96) private String lastName;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MerchantBeneficialOwner() {}
    public static MerchantBeneficialOwner active(UUID merchantId, String firstName, String lastName) {
        if (merchantId == null || AcceptanceProduct.blank(firstName) || AcceptanceProduct.blank(lastName))
            throw new IllegalArgumentException("MER-005: invalid beneficial owner");
        MerchantBeneficialOwner value = new MerchantBeneficialOwner();
        value.id = UUID.randomUUID(); value.merchantId = merchantId;
        value.firstName = firstName.trim(); value.lastName = lastName.trim(); value.active = true;
        value.createdAt = Instant.now(); value.updatedAt = value.createdAt; return value;
    }
    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public boolean active() { return active; }
}
