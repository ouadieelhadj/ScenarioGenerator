package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_workflow_request")
public class WorkflowApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "case_id", nullable = false, unique = true, updatable = false)
    private UUID caseId;
    @Column(name = "module_code", nullable = false, length = 48, updatable = false)
    private String moduleCode;
    @Column(name = "operation_type", nullable = false, length = 64, updatable = false)
    private String operationType;
    @Column(name = "object_reference", nullable = false, length = 64, updatable = false)
    private String objectReference;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "created_by", nullable = false, length = 96, updatable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "decided_by", length = 96)
    private String decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;

    protected WorkflowApprovalRequest() {}

    public static WorkflowApprovalRequest onboarding(UUID caseId, String reference, String maker) {
        WorkflowApprovalRequest value = new WorkflowApprovalRequest();
        value.caseId = caseId;
        value.moduleCode = "MERCHANT_ONBOARDING";
        value.operationType = "MERCHANT_DOSSIER_APPROVAL";
        value.objectReference = reference;
        value.status = "PENDING";
        value.createdBy = maker;
        value.createdAt = Instant.now();
        return value;
    }

    public void approve(String checker) { decide(checker, "APPROVED"); }
    public void reject(String checker) { decide(checker, "REJECTED"); }

    private void decide(String checker, String decision) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Workflow request is already decided");
        if (createdBy.equals(checker)) throw new IllegalStateException("Maker and checker must be different");
        status = decision;
        decidedBy = checker;
        decidedAt = Instant.now();
    }

    public Long id() { return id; }
    public UUID caseId() { return caseId; }
    public String moduleCode() { return moduleCode; }
    public String operationType() { return operationType; }
    public String objectReference() { return objectReference; }
    public String status() { return status; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
}
