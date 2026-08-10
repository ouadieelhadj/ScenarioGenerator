package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "onboarding_terminal_request", indexes = {
        @Index(name = "ix_terminal_request_case", columnList = "case_id"),
        @Index(name = "ix_terminal_request_outlet", columnList = "outlet_id")
})
public class TerminalRequest {
    @Id private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "outlet_id", nullable = false, updatable = false) private UUID outletId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(nullable = false) private int quantity;
    @Column(name = "model_code", nullable = false, length = 64) private String modelCode;
    @Column(name = "connectivity_code", nullable = false, length = 64) private String connectivityCode;
    @Column(name = "option_codes", nullable = false, length = 1000) private String optionCodes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private TerminalRequestStatus status;
    @Column(name = "external_reference", length = 128) private String externalReference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected TerminalRequest() {}

    public static TerminalRequest create(UUID caseId, UUID id, UUID outletId, UUID productId,
            int quantity, String modelCode, String connectivityCode, List<String> optionCodes,
            String externalReference) {
        if (caseId == null || outletId == null || productId == null || quantity < 1 || quantity > 999)
            throw new IllegalArgumentException("TPE-001: outlet, product and positive quantity are required");
        TerminalRequest value = new TerminalRequest();
        value.id = id == null ? UUID.randomUUID() : id;
        value.caseId = caseId;
        value.outletId = outletId;
        value.productId = productId;
        value.quantity = quantity;
        value.modelCode = code(modelCode, "TPE-002");
        value.connectivityCode = code(connectivityCode, "TPE-003");
        value.optionCodes = normalizeOptions(optionCodes);
        value.status = TerminalRequestStatus.REQUESTED;
        value.externalReference = optional(externalReference, 128);
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    private static String code(String value, String requirement) {
        if (value == null || !value.matches("[A-Z0-9][A-Z0-9_-]{0,63}"))
            throw new IllegalArgumentException(requirement + ": invalid reference code");
        return value;
    }
    private static String normalizeOptions(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> normalized = values.stream().map(value -> code(value, "TPE-004")).distinct().sorted().toList();
        String result = String.join(",", normalized);
        if (result.length() > 1000) throw new IllegalArgumentException("TPE-004: too many options");
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
    public int quantity() { return quantity; }
    public String modelCode() { return modelCode; }
    public String connectivityCode() { return connectivityCode; }
    public List<String> optionCodes() { return optionCodes.isBlank() ? List.of() : Arrays.asList(optionCodes.split(",")); }
    public TerminalRequestStatus status() { return status; }
    public String externalReference() { return externalReference; }
    public void cancel() {
        if (status != TerminalRequestStatus.REQUESTED)
            throw new IllegalStateException("TPE-001: only an unprocessed request can be cancelled");
        status = TerminalRequestStatus.CANCELLED;
        updatedAt = Instant.now();
    }
}
