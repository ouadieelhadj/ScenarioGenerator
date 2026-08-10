package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acquiring_product_binding", indexes =
        @Index(name = "ix_product_binding_resolution",
                columnList = "acquirer_id,usage,channel,currency,active"))
public class AcquiringProductBinding {
    @Id private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64) private String acquirerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private ProductBindingUsage usage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private AcceptanceChannel channel;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false) private boolean active;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "created_by", nullable = false, length = 96) private String createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected AcquiringProductBinding() {}

    public static AcquiringProductBinding active(String acquirerId, ProductBindingUsage usage,
            AcceptanceChannel channel, String currency, UUID productId, String actor) {
        if (AcceptanceProduct.blank(acquirerId) || usage == null || channel == null
                || !AcceptanceProduct.currency(currency) || productId == null
                || AcceptanceProduct.blank(actor))
            throw new IllegalArgumentException("PDV-005: invalid acquiring product binding");
        AcquiringProductBinding value = new AcquiringProductBinding();
        value.id = UUID.randomUUID(); value.acquirerId = acquirerId; value.usage = usage;
        value.channel = channel; value.currency = currency; value.productId = productId;
        value.active = true; value.validFrom = Instant.now(); value.createdBy = actor;
        value.createdAt = value.validFrom;
        return value;
    }

    public void deactivate() { active = false; validTo = Instant.now(); }
    public UUID id() { return id; }
    public String acquirerId() { return acquirerId; }
    public ProductBindingUsage usage() { return usage; }
    public AcceptanceChannel channel() { return channel; }
    public String currency() { return currency; }
    public UUID productId() { return productId; }
    public boolean active() { return active; }
}
