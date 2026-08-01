package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
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
    @Column(nullable = false, length = 2)
    private String country;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
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
        value.createdAt = Instant.now();
        return value;
    }

    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public String outletCode() { return outletCode; }
    public String name() { return name; }
    public String addressLine() { return addressLine; }
    public String country() { return country; }
    public boolean isActive() { return active; }
}
