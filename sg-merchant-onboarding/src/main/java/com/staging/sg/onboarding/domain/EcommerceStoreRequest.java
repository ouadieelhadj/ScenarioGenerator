package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "onboarding_ecommerce_store_request", uniqueConstraints =
        @UniqueConstraint(name = "uk_onboarding_store_code", columnNames = {"case_id", "store_code"}))
public class EcommerceStoreRequest {
    @Id private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "outlet_id", nullable = false, updatable = false) private UUID outletId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(name = "store_code", nullable = false, length = 64) private String storeCode;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "allowed_domain", nullable = false, length = 255) private String allowedDomain;
    @Column(name = "return_url", nullable = false, length = 512) private String returnUrl;
    @Column(name = "notification_url", nullable = false, length = 512) private String notificationUrl;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "capture_mode", nullable = false, length = 32) private String captureMode;
    @Column(name = "option_codes", nullable = false, length = 1000) private String optionCodes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private EcommerceStoreRequestStatus status;
    @Column(name = "external_reference", length = 128) private String externalReference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected EcommerceStoreRequest() {}

    public static EcommerceStoreRequest create(UUID caseId, UUID id, UUID outletId, UUID productId,
            String storeCode, String name, String allowedDomain, String returnUrl,
            String notificationUrl, String currency, String captureMode,
            List<String> optionCodes, String externalReference) {
        if (caseId == null || outletId == null || productId == null)
            throw new IllegalArgumentException("ECOM-001: case, outlet and product are required");
        if (allowedDomain == null || !allowedDomain.matches("(?i)[a-z0-9.-]+\\.[a-z]{2,}"))
            throw new IllegalArgumentException("ECOM-002: allowedDomain is invalid");
        if (!https(returnUrl) || !https(notificationUrl))
            throw new IllegalArgumentException("ECOM-002: return and notification URLs must use HTTPS");
        if (currency == null || !currency.matches("[0-9]{3}"))
            throw new IllegalArgumentException("ECOM-002: currency is invalid");
        EcommerceStoreRequest value = new EcommerceStoreRequest();
        value.id = id == null ? UUID.randomUUID() : id;
        value.caseId = caseId;
        value.outletId = outletId;
        value.productId = productId;
        value.storeCode = text(storeCode, 64, "ECOM-002");
        value.name = text(name, 160, "ECOM-002");
        value.allowedDomain = allowedDomain.toLowerCase();
        value.returnUrl = returnUrl;
        value.notificationUrl = notificationUrl;
        value.currency = currency;
        value.captureMode = code(captureMode);
        value.optionCodes = normalizeOptions(optionCodes);
        value.status = EcommerceStoreRequestStatus.REQUESTED;
        value.externalReference = optional(externalReference, 128);
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    private static boolean https(String value) { return value != null && value.matches("https://[^\\s]+"); }
    private static String text(String value, int max, String requirement) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(requirement + ": invalid value");
        return value.trim();
    }
    private static String code(String value) {
        if (value == null || !value.matches("[A-Z0-9][A-Z0-9_-]{0,31}"))
            throw new IllegalArgumentException("ECOM-003: invalid reference code");
        return value;
    }
    private static String normalizeOptions(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        String result = String.join(",", values.stream().map(EcommerceStoreRequest::code)
                .distinct().sorted().toList());
        if (result.length() > 1000) throw new IllegalArgumentException("ECOM-003: too many options");
        return result;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Value is too long");
        return value.trim();
    }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public UUID outletId() { return outletId; }
    public UUID productId() { return productId; }
    public String storeCode() { return storeCode; }
    public String name() { return name; }
    public String allowedDomain() { return allowedDomain; }
    public String returnUrl() { return returnUrl; }
    public String notificationUrl() { return notificationUrl; }
    public String currency() { return currency; }
    public String captureMode() { return captureMode; }
    public List<String> optionCodes() { return optionCodes.isBlank() ? List.of() : Arrays.asList(optionCodes.split(",")); }
    public EcommerceStoreRequestStatus status() { return status; }
    public String externalReference() { return externalReference; }
    public void cancel() {
        if (status != EcommerceStoreRequestStatus.REQUESTED)
            throw new IllegalStateException("ECOM-001: only an unprocessed store can be cancelled");
        status = EcommerceStoreRequestStatus.CANCELLED;
        updatedAt = Instant.now();
    }
}
