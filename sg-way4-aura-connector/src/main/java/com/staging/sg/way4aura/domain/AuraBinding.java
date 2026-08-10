package com.staging.sg.way4aura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "way4_aura_binding", indexes = @Index(name = "ix_way4_binding_resolution",
        columnList = "binding_type,source_code,active,valid_from,valid_to"))
public class AuraBinding {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "binding_type", nullable = false, length = 40) private AuraBindingType type;
    @Column(name = "source_code", nullable = false, length = 128) private String sourceCode;
    @Column(name = "aura_code", nullable = false, length = 128) private String auraCode;
    @Column(nullable = false) private int bindingVersion;
    @Column(nullable = false) private boolean active;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "source_reference", nullable = false, length = 255) private String sourceReference;
    @Column(name = "created_by", nullable = false, length = 96) private String createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;
    protected AuraBinding() {}
    public static AuraBinding create(AuraBindingType type, String sourceCode, String auraCode,
            int bindingVersion, Instant validFrom, Instant validTo, String sourceReference, String actor) {
        if (type == null || bindingVersion < 1 || validFrom == null || blank(sourceCode)
                || blank(auraCode) || blank(sourceReference) || blank(actor)
                || (validTo != null && !validTo.isAfter(validFrom)))
            throw new IllegalArgumentException("Invalid AURA binding");
        AuraBinding value = new AuraBinding(); value.id = UUID.randomUUID(); value.type = type;
        value.sourceCode = sourceCode.trim(); value.auraCode = auraCode.trim();
        value.bindingVersion = bindingVersion; value.active = true; value.validFrom = validFrom;
        value.validTo = validTo; value.sourceReference = sourceReference.trim();
        value.createdBy = actor.trim(); value.createdAt = Instant.now(); return value;
    }
    public void deactivate() { active = false; if (validTo == null) validTo = Instant.now(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public UUID id() { return id; } public AuraBindingType type() { return type; }
    public String sourceCode() { return sourceCode; } public String auraCode() { return auraCode; }
    public int bindingVersion() { return bindingVersion; } public boolean active() { return active; }
    public Instant validFrom() { return validFrom; } public Instant validTo() { return validTo; }
}
