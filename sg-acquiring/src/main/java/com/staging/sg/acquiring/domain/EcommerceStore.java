package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ecommerce_store", uniqueConstraints =
        @UniqueConstraint(name = "uk_ecommerce_store_code", columnNames = {"merchant_id", "store_code"}))
public class EcommerceStore {
    @Id
    private UUID id;
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;
    @Column(name = "outlet_id", nullable = false, updatable = false)
    private UUID outletId;
    @Column(name = "store_code", nullable = false, length = 64, updatable = false)
    private String storeCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "allowed_domain", nullable = false, length = 255)
    private String allowedDomain;
    @Column(name = "return_url", nullable = false, length = 512)
    private String returnUrl;
    @Column(name = "notification_url", nullable = false, length = 512)
    private String notificationUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EcommerceStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected EcommerceStore() {}

    public static EcommerceStore draft(UUID merchantId, String storeCode, String name,
            String allowedDomain, String returnUrl, String notificationUrl) {
        return draft(merchantId, merchantId, storeCode, name, allowedDomain, returnUrl,
                notificationUrl);
    }

    public static EcommerceStore draft(UUID merchantId, UUID outletId, String storeCode, String name,
            String allowedDomain, String returnUrl, String notificationUrl) {
        if (merchantId == null || outletId == null || AcceptanceProduct.blank(storeCode)
                || AcceptanceProduct.blank(name) || !host(allowedDomain)
                || !https(returnUrl) || !https(notificationUrl)) {
            throw new IllegalArgumentException("Invalid ecommerce store");
        }
        EcommerceStore value = new EcommerceStore();
        value.id = UUID.randomUUID();
        value.merchantId = merchantId;
        value.outletId = outletId;
        value.storeCode = storeCode;
        value.name = name;
        value.allowedDomain = allowedDomain.toLowerCase();
        value.returnUrl = returnUrl;
        value.notificationUrl = notificationUrl;
        value.status = EcommerceStatus.DRAFT;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void ready() {
        require(EcommerceStatus.DRAFT, "Only a draft store can become ready");
        status = EcommerceStatus.READY;
        updatedAt = Instant.now();
    }

    public void activate() {
        require(EcommerceStatus.READY, "Only a ready store can be activated");
        status = EcommerceStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public UUID outletId() { return outletId; }
    public String storeCode() { return storeCode; }
    public String name() { return name; }
    public String allowedDomain() { return allowedDomain; }
    public String returnUrl() { return returnUrl; }
    public String notificationUrl() { return notificationUrl; }
    public EcommerceStatus status() { return status; }

    private void require(EcommerceStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    private static boolean host(String value) {
        return value != null && value.matches("(?i)[a-z0-9.-]+\\.[a-z]{2,}");
    }

    private static boolean https(String value) {
        return value != null && value.matches("https://[^\\s]+" );
    }
}
