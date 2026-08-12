package com.staging.sg.way4aura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "way4_file_batch", uniqueConstraints = {
        @UniqueConstraint(name = "uk_way4_file_number", columnNames = "file_number"),
        @UniqueConstraint(name = "uk_way4_file_name", columnNames = "extended_file_name"),
        @UniqueConstraint(name = "uk_way4_file_idempotency", columnNames = "idempotency_key")})
public class Way4FileBatch {
    @Id private UUID id;
    @Column(name = "file_number", nullable = false, updatable = false) private long fileNumber;
    @Column(name = "extended_file_name", nullable = false, length = 160, updatable = false) private String extendedFileName;
    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false) private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Column(name = "xml_sha256", length = 64) private String xmlSha256;
    @Column(name = "xsd_sha256", length = 64) private String xsdSha256;
    @Column(name = "mapping_version", nullable = false, updatable = false) private int mappingVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Way4FileStatus status;
    @Column(name = "generated_at", nullable = false, updatable = false) private Instant generatedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected Way4FileBatch() {}
    public static Way4FileBatch draft(long number, String key, String payloadHash, int mappingVersion) {
        Way4FileBatch value = new Way4FileBatch(); value.id = UUID.randomUUID(); value.fileNumber = number;
        value.extendedFileName = "FP_WAY4_" + String.format("%010d", number) + ".xml";
        value.idempotencyKey = key; value.payloadHash = payloadHash; value.mappingVersion = mappingVersion;
        value.status = Way4FileStatus.DRAFT; value.generatedAt = Instant.now(); value.updatedAt = value.generatedAt;
        return value;
    }
    public void validated(String xmlHash, String xsdHash) { if (status != Way4FileStatus.DRAFT)
        throw new IllegalStateException("File is not a draft"); xmlSha256 = xmlHash; xsdSha256 = xsdHash;
        status = Way4FileStatus.VALIDATED; updatedAt = Instant.now(); }
    public void staged() { if (status != Way4FileStatus.VALIDATED)
        throw new IllegalStateException("File is not validated"); status = Way4FileStatus.STAGED;
        updatedAt = Instant.now(); }
    public UUID id() { return id; } public long fileNumber() { return fileNumber; }
    public String extendedFileName() { return extendedFileName; } public String idempotencyKey() { return idempotencyKey; }
    public String payloadHash() { return payloadHash; } public String xmlSha256() { return xmlSha256; }
    public String xsdSha256() { return xsdSha256; } public int mappingVersion() { return mappingVersion; }
    public Way4FileStatus status() { return status; } public Instant generatedAt() { return generatedAt; }
}
