package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.*;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantOnboardingService {
    private final MerchantPortalAccountRepository accounts;
    private final MerchantOnboardingCaseRepository cases;
    private final WorkflowApprovalRequestRepository workflows;
    private final ProvisioningJobRepository jobs;
    private final OnboardingDocumentRepository documents;
    private final AcquiringProvisioningPort acquiring;
    private final IdentityInvitationPort identity;
    private final ObjectMapper objectMapper;

    public MerchantOnboardingService(MerchantPortalAccountRepository accounts,
            MerchantOnboardingCaseRepository cases,
            WorkflowApprovalRequestRepository workflows,
            ProvisioningJobRepository jobs,
            OnboardingDocumentRepository documents,
            AcquiringProvisioningPort acquiring,
            IdentityInvitationPort identity,
            ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.cases = cases;
        this.workflows = workflows;
        this.jobs = jobs;
        this.documents = documents;
        this.acquiring = acquiring;
        this.identity = identity;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Prospect createProspect(String login, String email, String acquirerId, String commercial) {
        return createProspect(login, email, acquirerId, commercial, null);
    }

    @Transactional
    public Prospect createProspect(String login, String email, String acquirerId,
            String commercial, String bearerAuthorization) {
        var invitation = identity.invite(login, email, bearerAuthorization);
        MerchantPortalAccount account = accounts.save(MerchantPortalAccount.invite(login, email, commercial));
        invitation.ifPresent(value -> account.registerPendingIdentity(value.userId().toString()));
        accounts.save(account);
        MerchantOnboardingCase dossier = cases.save(MerchantOnboardingCase.prospect(account.id(), acquirerId, commercial));
        return new Prospect(account, dossier, invitation.orElse(null));
    }

    @Transactional
    public MerchantPortalAccount linkIdentity(UUID accountId, String identityUserId) {
        MerchantPortalAccount account = account(accountId);
        account.linkIdentity(identityUserId);
        return accounts.save(account);
    }

    @Transactional
    public MerchantOnboardingCase updateDossier(UUID id, DossierData data, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireEditor(dossier, caller);
        dossier.updateDossier(data.legalName(), data.tradingName(), data.registrationNumber(),
                data.country(), data.mcc(), data.settlementAccountReference(),
                data.settlementCurrency(), data.productId(), data.acceptanceChannel(),
                data.outletCode(), data.outletName(), data.outletAddress(), data.terminalCount());
        return cases.save(dossier);
    }

    @Transactional
    public MerchantOnboardingCase submitKyc(UUID id, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireEditor(dossier, caller);
        requireRequiredDocumentsPresent(id);
        dossier.submitKyc(caller);
        return cases.save(dossier);
    }

    @Transactional
    public OnboardingDocument addDocument(UUID caseId, DocumentType type,
            String storageReference, String contentType, long contentLength,
            String sha256, String caller) {
        MerchantOnboardingCase dossier = dossier(caseId);
        requireEditor(dossier, caller);
        if (dossier.kycStatus() == KycStatus.VALIDATED || dossier.status() != OnboardingStatus.DRAFT)
            throw new IllegalStateException("Documents cannot be changed after KYC validation");
        int version = documents.findFirstByCaseIdAndTypeOrderByDocumentVersionDesc(caseId, type)
                .map(value -> value.documentVersion() + 1).orElse(1);
        return documents.save(OnboardingDocument.uploaded(caseId, type, version,
                storageReference, contentType, contentLength, sha256, caller));
    }

    @Transactional
    public OnboardingDocument reviewDocument(UUID documentId, boolean accepted,
            String reason, String reviewer) {
        OnboardingDocument document = document(documentId);
        MerchantOnboardingCase dossier = dossier(document.caseId());
        if (dossier.kycStatus() != KycStatus.PENDING_REVIEW)
            throw new IllegalStateException("KYC is not pending review");
        if (accepted) document.accept(reviewer); else document.reject(reviewer, reason);
        return documents.save(document);
    }

    @Transactional
    public MerchantOnboardingCase validateKyc(UUID id, String reviewer) {
        MerchantOnboardingCase dossier = dossier(id);
        requireRequiredDocumentsAccepted(id);
        dossier.validateKyc(reviewer);
        return cases.save(dossier);
    }

    @Transactional
    public MerchantOnboardingCase requestKycComplements(UUID id, String reviewer, String reason) {
        MerchantOnboardingCase dossier = dossier(id);
        dossier.requestKycComplements(reviewer, reason);
        return cases.save(dossier);
    }

    @Transactional
    public MerchantOnboardingCase rejectKyc(UUID id, String reviewer, String reason) {
        MerchantOnboardingCase dossier = dossier(id);
        dossier.rejectKyc(reviewer, reason);
        return cases.save(dossier);
    }

    @Transactional(readOnly = true)
    public List<OnboardingDocument> documents(UUID id, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireViewer(dossier, caller);
        return documents.findByCaseIdOrderByTypeAscDocumentVersionDesc(id);
    }

    @Transactional
    public WorkflowApprovalRequest submit(UUID id, String maker) {
        MerchantOnboardingCase dossier = dossier(id);
        requireEditor(dossier, maker);
        dossier.submit(maker);
        cases.save(dossier);
        return workflows.save(WorkflowApprovalRequest.onboarding(id, dossier.reference(), maker));
    }

    @Transactional
    public MerchantOnboardingCase approve(long workflowId, String checker) {
        WorkflowApprovalRequest workflow = workflow(workflowId);
        MerchantOnboardingCase dossier = dossier(workflow.caseId());
        dossier.approve(checker);
        workflow.approve(checker);
        workflows.save(workflow);
        return cases.save(dossier);
    }

    @Transactional
    public MerchantOnboardingCase reject(long workflowId, String checker, String reason) {
        WorkflowApprovalRequest workflow = workflow(workflowId);
        MerchantOnboardingCase dossier = dossier(workflow.caseId());
        dossier.reject(checker, reason);
        workflow.reject(checker);
        workflows.save(workflow);
        return cases.save(dossier);
    }

    @Transactional(readOnly = true)
    public MerchantOnboardingCase get(UUID id, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireViewer(dossier, caller);
        return dossier;
    }

    @Transactional(readOnly = true)
    public MerchantOnboardingCase myDossier(String caller) {
        MerchantPortalAccount account = accounts.findByLogin(caller)
                .orElseThrow(() -> new IllegalArgumentException("Merchant account not found for caller"));
        return cases.findFirstByAccountIdOrderByCreatedAtDesc(account.id())
                .orElseThrow(() -> new IllegalArgumentException("Onboarding dossier not found for caller"));
    }

    @Transactional(readOnly = true)
    public List<MerchantOnboardingCase> reviewQueue() {
        return cases.findByKycStatusOrderByCreatedAtAsc(KycStatus.PENDING_REVIEW);
    }

    @Transactional(readOnly = true)
    public MerchantOnboardingCase getForReview(UUID id) {
        return dossier(id);
    }

    @Transactional(readOnly = true)
    public List<OnboardingDocument> documentsForReview(UUID id) {
        dossier(id);
        return documents.findByCaseIdOrderByTypeAscDocumentVersionDesc(id);
    }

    @Transactional(readOnly = true)
    public OnboardingDocument documentContent(UUID caseId, UUID documentId, String caller) {
        MerchantOnboardingCase dossier = dossier(caseId);
        requireViewer(dossier, caller);
        OnboardingDocument value = document(documentId);
        if (!value.caseId().equals(caseId)) throw new IllegalArgumentException("Document does not belong to dossier");
        return value;
    }

    @Transactional(readOnly = true)
    public OnboardingDocument documentContentForReview(UUID documentId) {
        OnboardingDocument value = document(documentId);
        dossier(value.caseId());
        return value;
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> operations(String caller) {
        return workflows.findByCreatedByOrderByCreatedAtDesc(caller);
    }

    @Transactional(readOnly = true)
    public List<WorkflowApprovalRequest> approvals() {
        return workflows.findByStatusOrderByCreatedAtAsc("PENDING");
    }

    @Transactional
    public ProvisioningOutcome requestProvisioning(UUID caseId, ProvisioningMode mode,
            String correlationId) {
        MerchantOnboardingCase dossier = dossier(caseId);
        MerchantProvisioningCommand command = command(dossier);
        String idempotencyKey = "merchant-onboarding:" + caseId;
        if (mode == ProvisioningMode.IMMEDIATE && dossier.status() == OnboardingStatus.PROVISIONED) {
            try {
                MerchantProvisioningResult replay = acquiring.provision(command, idempotencyKey, correlationId);
                return ProvisioningOutcome.succeeded(dossier, null, replay);
            } catch (RuntimeException exception) {
                return ProvisioningOutcome.failed(dossier, null, exception.getMessage());
            }
        }
        if (mode == ProvisioningMode.BATCH) {
            ProvisioningJob existing = jobs.findByCaseId(caseId).orElse(null);
            if (existing != null) return ProvisioningOutcome.queued(dossier, existing);
            dossier.queue();
            cases.save(dossier);
            ProvisioningJob job = jobs.save(ProvisioningJob.pending(caseId, idempotencyKey, json(command)));
            return ProvisioningOutcome.queued(dossier, job);
        }
        return execute(dossier, command, idempotencyKey, correlationId, null);
    }

    @Transactional(readOnly = true)
    public List<MerchantProvisioningCommand> exportPendingBatch() {
        return jobs.findByStatusOrderByCreatedAtAsc(ProvisioningJobStatus.PENDING).stream()
                .map(job -> fromJson(job.payloadJson()))
                .toList();
    }

    @Transactional
    public List<ProvisioningOutcome> runBatch(int limit, boolean retryFailed, String correlationId) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        List<ProvisioningJob> selected = new ArrayList<>(jobs.findByStatusOrderByCreatedAtAsc(ProvisioningJobStatus.PENDING));
        if (retryFailed) selected.addAll(jobs.findByStatusOrderByCreatedAtAsc(ProvisioningJobStatus.FAILED));
        return selected.stream().limit(limit)
                .map(job -> execute(dossier(job.caseId()), fromJson(job.payloadJson()),
                        job.idempotencyKey(), correlationId + ":" + job.id(), job))
                .toList();
    }

    private ProvisioningOutcome execute(MerchantOnboardingCase dossier,
            MerchantProvisioningCommand command, String idempotencyKey,
            String correlationId, ProvisioningJob job) {
        dossier.startProvisioning();
        if (job != null) job.processing();
        try {
            MerchantProvisioningResult result = acquiring.provision(command, idempotencyKey, correlationId);
            dossier.provisioned(result.merchantId(), result.merchantAcceptorId());
            if (job != null) job.succeeded();
            return ProvisioningOutcome.succeeded(dossier, job, result);
        } catch (RuntimeException exception) {
            dossier.provisioningFailed();
            if (job != null) job.failed(exception.getMessage());
            return ProvisioningOutcome.failed(dossier, job, exception.getMessage());
        } finally {
            cases.save(dossier);
            if (job != null) jobs.save(job);
        }
    }

    private MerchantProvisioningCommand command(MerchantOnboardingCase dossier) {
        if (dossier.status() != OnboardingStatus.APPROVED
                && dossier.status() != OnboardingStatus.QUEUED_FOR_PROVISIONING
                && dossier.status() != OnboardingStatus.PROVISIONING_FAILED
                && dossier.status() != OnboardingStatus.PROVISIONED) {
            throw new IllegalStateException("Only an approved dossier can be provisioned");
        }
        return new MerchantProvisioningCommand(dossier.id(), dossier.reference(), dossier.acquirerId(),
                dossier.legalName(), dossier.tradingName(), dossier.registrationNumber(),
                dossier.country(), dossier.mcc(), dossier.settlementAccountReference(),
                dossier.settlementCurrency(), dossier.productId(), dossier.acceptanceChannel(),
                new MerchantProvisioningCommand.Outlet(dossier.outletCode(), dossier.outletName(),
                        dossier.outletAddress(), dossier.terminalCount()),
                dossier.submittedBy(), dossier.checkedBy());
    }

    private String json(MerchantProvisioningCommand command) {
        try { return objectMapper.writeValueAsString(command); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize provisioning command", exception); }
    }

    private MerchantProvisioningCommand fromJson(String json) {
        try { return objectMapper.readValue(json, MerchantProvisioningCommand.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored provisioning command", exception); }
    }

    private MerchantPortalAccount account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Merchant account not found: " + id));
    }
    private MerchantOnboardingCase dossier(UUID id) {
        return cases.findById(id).orElseThrow(() -> new IllegalArgumentException("Onboarding case not found: " + id));
    }
    private WorkflowApprovalRequest workflow(long id) {
        return workflows.findById(id).orElseThrow(() -> new IllegalArgumentException("Workflow request not found: " + id));
    }

    private OnboardingDocument document(UUID id) {
        return documents.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Onboarding document not found: " + id));
    }

    private void requireRequiredDocumentsPresent(UUID caseId) {
        for (DocumentType type : requiredDocumentTypes()) {
            if (documents.findFirstByCaseIdAndTypeOrderByDocumentVersionDesc(caseId, type).isEmpty())
                throw new IllegalStateException("Missing required document: " + type);
        }
    }

    private void requireRequiredDocumentsAccepted(UUID caseId) {
        for (DocumentType type : requiredDocumentTypes()) {
            OnboardingDocument document = documents
                    .findFirstByCaseIdAndTypeOrderByDocumentVersionDesc(caseId, type)
                    .orElseThrow(() -> new IllegalStateException("Missing required document: " + type));
            if (document.reviewStatus() != DocumentReviewStatus.ACCEPTED)
                throw new IllegalStateException("Required document is not accepted: " + type);
        }
    }

    private static List<DocumentType> requiredDocumentTypes() {
        return List.of(DocumentType.LEGAL_EXISTENCE,
                DocumentType.REPRESENTATIVE_IDENTITY, DocumentType.BANK_ACCOUNT_PROOF);
    }

    private void requireEditor(MerchantOnboardingCase dossier, String caller) {
        MerchantPortalAccount account = account(dossier.accountId());
        if (caller == null || (!caller.equals(account.login())
                && !caller.equals(dossier.createdByCommercial()))) {
            throw new IllegalStateException("Caller cannot edit this onboarding dossier");
        }
    }

    private void requireViewer(MerchantOnboardingCase dossier, String caller) {
        requireEditor(dossier, caller);
    }

    public record Prospect(MerchantPortalAccount account, MerchantOnboardingCase dossier,
            IdentityInvitationPort.Invitation identityInvitation) {}
    public record DossierData(String legalName, String tradingName, String registrationNumber,
            String country, String mcc, String settlementAccountReference,
            String settlementCurrency, UUID productId, String acceptanceChannel,
            String outletCode, String outletName, String outletAddress, int terminalCount) {}
    public record ProvisioningOutcome(MerchantOnboardingCase dossier, ProvisioningJob job,
            MerchantProvisioningResult result, String error) {
        static ProvisioningOutcome queued(MerchantOnboardingCase dossier, ProvisioningJob job) {
            return new ProvisioningOutcome(dossier, job, null, null);
        }
        static ProvisioningOutcome succeeded(MerchantOnboardingCase dossier, ProvisioningJob job,
                MerchantProvisioningResult result) {
            return new ProvisioningOutcome(dossier, job, result, null);
        }
        static ProvisioningOutcome failed(MerchantOnboardingCase dossier, ProvisioningJob job, String error) {
            return new ProvisioningOutcome(dossier, job, null, error);
        }
    }
}
