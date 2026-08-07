package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_onboarding_document", uniqueConstraints =
        @UniqueConstraint(name = "uk_onboarding_document_version",
                columnNames = {"case_id", "document_type", "document_version"}))
public class OnboardingDocument {
    @Id
    private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 48, updatable = false)
    private DocumentType type;
    @Column(name = "document_version", nullable = false, updatable = false)
    private int documentVersion;
    @Column(name = "storage_reference", nullable = false, length = 512, updatable = false)
    private String storageReference;
    @Column(name = "content_type", nullable = false, length = 96, updatable = false)
    private String contentType;
    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;
    @Column(name = "sha256", nullable = false, length = 64, updatable = false)
    private String sha256;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 24)
    private DocumentReviewStatus reviewStatus;
    @Column(name = "uploaded_by", nullable = false, length = 96, updatable = false)
    private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
    @Column(name = "reviewed_by", length = 96)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected OnboardingDocument() {}

    public static OnboardingDocument uploaded(UUID caseId, DocumentType type, int version,
            String storageReference, String contentType, long contentLength,
            String sha256, String uploader) {
        if (caseId == null || type == null || version < 1 || blank(storageReference)
                || !allowedContentType(contentType) || contentLength < 1 || contentLength > 20_000_000
                || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}") || blank(uploader)) {
            throw new IllegalArgumentException("Invalid onboarding document metadata");
        }
        if (storageReference.startsWith("data:") || storageReference.contains("..")) {
            throw new IllegalArgumentException("Document storage reference must be opaque");
        }
        OnboardingDocument value = new OnboardingDocument();
        value.id = UUID.randomUUID();
        value.caseId = caseId;
        value.type = type;
        value.documentVersion = version;
        value.storageReference = storageReference;
        value.contentType = contentType;
        value.contentLength = contentLength;
        value.sha256 = sha256.toLowerCase();
        value.reviewStatus = DocumentReviewStatus.PENDING;
        value.uploadedBy = uploader;
        value.uploadedAt = Instant.now();
        return value;
    }

    public void accept(String reviewer) { review(reviewer, DocumentReviewStatus.ACCEPTED, null); }
    public void reject(String reviewer, String reason) {
        if (blank(reason)) throw new IllegalArgumentException("Rejection reason is required");
        review(reviewer, DocumentReviewStatus.REJECTED, reason.trim());
    }
    private void review(String reviewer, DocumentReviewStatus decision, String reason) {
        if (reviewStatus != DocumentReviewStatus.PENDING)
            throw new IllegalStateException("Document is already reviewed");
        if (blank(reviewer)) throw new IllegalArgumentException("Reviewer is required");
        if (uploadedBy.equals(reviewer)) throw new IllegalStateException("Uploader cannot review the same document");
        reviewStatus = decision;
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        rejectionReason = reason;
    }
    private static boolean allowedContentType(String value) {
        return "application/pdf".equals(value) || "image/jpeg".equals(value) || "image/png".equals(value);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public DocumentType type() { return type; }
    public int documentVersion() { return documentVersion; }
    public String storageReference() { return storageReference; }
    public String contentType() { return contentType; }
    public long contentLength() { return contentLength; }
    public String sha256() { return sha256; }
    public DocumentReviewStatus reviewStatus() { return reviewStatus; }
    public String uploadedBy() { return uploadedBy; }
    public String reviewedBy() { return reviewedBy; }
    public String rejectionReason() { return rejectionReason; }
}
