package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_outlet_product", uniqueConstraints =
        @UniqueConstraint(name = "uk_merchant_outlet_product", columnNames = {"outlet_id", "product_id"}))
public class MerchantOutletProduct {
    @Id private UUID id;
    @Column(name = "outlet_id", nullable = false, updatable = false) private UUID outletId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(name = "source_reference", nullable = false, length = 128, updatable = false) private String sourceReference;
    @Column(nullable = false) private boolean active;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;

    protected MerchantOutletProduct() {}
    public static MerchantOutletProduct active(UUID outletId, UUID productId, String sourceReference) {
        if (outletId == null || productId == null || AcceptanceProduct.blank(sourceReference))
            throw new IllegalArgumentException("PDV-005: invalid outlet product");
        MerchantOutletProduct value = new MerchantOutletProduct();
        value.id = UUID.randomUUID(); value.outletId = outletId; value.productId = productId;
        value.sourceReference = sourceReference; value.active = true; value.validFrom = Instant.now();
        return value;
    }
    public UUID id() { return id; }
    public UUID outletId() { return outletId; }
    public UUID productId() { return productId; }
    public boolean active() { return active; }
}
