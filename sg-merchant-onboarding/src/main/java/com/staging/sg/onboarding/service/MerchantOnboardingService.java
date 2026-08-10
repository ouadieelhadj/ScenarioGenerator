package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.*;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantOnboardingService {
    private final MerchantPortalAccountRepository accounts;
    private final MerchantOnboardingCaseRepository cases;
    private final WorkflowApprovalRequestRepository workflows;
    private final ProvisioningJobRepository jobs;
    private final OnboardingDocumentRepository documents;
    private final OnboardingOutletRepository outlets;
    private final OnboardingBeneficialOwnerRepository beneficialOwners;
    private final OnboardingReferenceValueRepository references;
    private final OnboardingFieldRuleRepository fieldRules;
    private final OnboardingOutletProductRepository outletProducts;
    private final TerminalRequestRepository terminalRequests;
    private final EcommerceStoreRequestRepository ecommerceStoreRequests;
    private final OnboardingOutboxService outbox;
    private final AcquiringProvisioningPort acquiring;
    private final IdentityInvitationPort identity;
    private final ObjectMapper objectMapper;

    public MerchantOnboardingService(MerchantPortalAccountRepository accounts,
            MerchantOnboardingCaseRepository cases,
            WorkflowApprovalRequestRepository workflows,
            ProvisioningJobRepository jobs,
            OnboardingDocumentRepository documents,
            OnboardingOutletRepository outlets,
            OnboardingBeneficialOwnerRepository beneficialOwners,
            OnboardingReferenceValueRepository references,
            OnboardingFieldRuleRepository fieldRules,
            OnboardingOutletProductRepository outletProducts,
            TerminalRequestRepository terminalRequests,
            EcommerceStoreRequestRepository ecommerceStoreRequests,
            OnboardingOutboxService outbox,
            AcquiringProvisioningPort acquiring,
            IdentityInvitationPort identity,
            ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.cases = cases;
        this.workflows = workflows;
        this.jobs = jobs;
        this.documents = documents;
        this.outlets = outlets;
        this.beneficialOwners = beneficialOwners;
        this.references = references;
        this.fieldRules = fieldRules;
        this.outletProducts = outletProducts;
        this.terminalRequests = terminalRequests;
        this.ecommerceStoreRequests = ecommerceStoreRequests;
        this.outbox = outbox;
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
        MerchantOnboardingCase saved = cases.save(dossier);
        adaptLegacyOutlet(saved);
        return saved;
    }

    @Transactional
    public DossierV2Snapshot updateDossierV2(UUID id, DossierV2Data data, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireEditor(dossier, caller);
        if (data != null && data.version() != dossier.version())
            throw new IllegalStateException("CONCURRENCY: dossier version is stale");
        if (data == null || data.headquartersAddress() == null || data.representative() == null)
            throw new IllegalArgumentException("MER-002: legal profile is incomplete");
        if (data.outlets() == null || data.outlets().isEmpty())
            throw new IllegalArgumentException("PDV-001: at least one outlet is required");
        long principals = data.outlets().stream().filter(OutletData::active).filter(OutletData::principal).count();
        if (principals != 1)
            throw new IllegalArgumentException("PDV-002: exactly one active principal outlet is required");
        HashSet<String> codes = new HashSet<>();
        for (OutletData outlet : data.outlets()) {
            if (!codes.add(outlet.code().trim()))
                throw new IllegalArgumentException("PDV-003: duplicate outlet code");
        }
        AddressData address = data.headquartersAddress();
        RepresentativeData representative = data.representative();
        requireReference("COUNTRY", address.country(), "ADR-001: headquartersAddress.country");
        requireReference("COUNTRY", representative.residenceCountry(), "MER-004: representative.residenceCountry");
        requireReference("COUNTRY", representative.nationality(), "MER-004: representative.nationality");
        requireReference("MCC", data.mcc(), "REF-002: mcc");
        for (OutletData outlet : data.outlets())
            requireReference("COUNTRY", outlet.address().country(), "ADR-001: outlets.address.country");
        validateConfiguredFieldRules(data);
        dossier.updateLegalProfile(data.merchantType(), data.organizationLegalNature(),
                data.legalName(), data.tradingName(), data.registrationNumber(),
                data.taxIdentifier(), data.ice(), data.legalForm(), data.businessActivity(),
                data.associationPurpose(), data.primaryPhone(), data.primaryEmail(),
                address.line1(), address.line2(), address.district(), address.city(),
                address.region(), address.postalCode(), address.country(), data.mcc(), data.rib(),
                representative.title(), representative.firstName(), representative.lastName(),
                representative.birthDate(), representative.phone(), representative.email(),
                representative.idType(), representative.idNumber(),
                representative.residenceCountry(), representative.nationality());
        cases.saveAndFlush(dossier);

        List<UUID> retainedOutletIds = new ArrayList<>();
        for (OutletData input : data.outlets()) {
            OnboardingOutlet outlet = input.id() == null ? null : outlets.findById(input.id()).orElse(null);
            AddressData outletAddress = input.address();
            RepresentativeData responsible = input.responsible();
            if (outlet != null && !outlet.caseId().equals(id))
                throw new IllegalArgumentException("PDV-003: outlet does not belong to dossier");
            if (outlet == null) {
                outlet = OnboardingOutlet.create(id, input.id(), input.code(), input.name(),
                        input.principal(), input.active(), outletAddress.line1(), outletAddress.line2(),
                        outletAddress.district(), outletAddress.city(), outletAddress.region(),
                        outletAddress.postalCode(), outletAddress.country(), input.contactPhone(),
                        input.contactEmail(), responsible.title(), responsible.firstName(),
                        responsible.lastName(), responsible.birthDate(), responsible.phone(),
                        responsible.email(), responsible.idType(), responsible.idNumber(),
                        responsible.residenceCountry(), responsible.nationality());
            } else {
                outlet.change(input.code(), input.name(), input.principal(), input.active(),
                        outletAddress.line1(), outletAddress.line2(), outletAddress.district(),
                        outletAddress.city(), outletAddress.region(), outletAddress.postalCode(),
                        outletAddress.country(), input.contactPhone(), input.contactEmail(),
                        responsible.title(), responsible.firstName(), responsible.lastName(),
                        responsible.birthDate(), responsible.phone(), responsible.email(),
                        responsible.idType(), responsible.idNumber(), responsible.residenceCountry(),
                        responsible.nationality());
            }
            outlets.save(outlet);
            retainedOutletIds.add(outlet.id());
            replaceOutletProducts(id, outlet.id(), input.products());
            replaceTerminalRequests(id, outlet.id(), input.terminalRequests());
            replaceEcommerceStores(id, outlet.id(), input.ecommerceStores());
        }
        for (OnboardingOutlet existing : outlets.findByCaseIdOrderByCreatedAtAsc(id)) {
            if (!retainedOutletIds.contains(existing.id()) && existing.active()) {
                existing.deactivate();
                outlets.save(existing);
            }
        }

        List<OnboardingBeneficialOwner> currentOwners =
                beneficialOwners.findByCaseIdOrderByCreatedAtAsc(id);
        List<UUID> retainedOwnerIds = new ArrayList<>();
        if (data.beneficialOwners() != null) {
            for (BeneficialOwnerData owner : data.beneficialOwners()) {
                OnboardingBeneficialOwner value = owner.id() == null ? null
                        : beneficialOwners.findById(owner.id()).orElse(null);
                if (value != null && !value.caseId().equals(id))
                    throw new IllegalArgumentException("MER-005: beneficial owner does not belong to dossier");
                if (value == null) value = OnboardingBeneficialOwner.create(id, owner.id(),
                        owner.firstName(), owner.lastName(), owner.active());
                else value.change(owner.firstName(), owner.lastName(), owner.active());
                beneficialOwners.save(value);
                retainedOwnerIds.add(value.id());
            }
        }
        for (OnboardingBeneficialOwner existing : currentOwners) {
            if (!retainedOwnerIds.contains(existing.id()) && existing.active()) {
                existing.deactivate();
                beneficialOwners.save(existing);
            }
        }
        return snapshot(dossier);
    }

    @Transactional(readOnly = true)
    public DossierV2Snapshot getV2(UUID id, String caller) {
        MerchantOnboardingCase dossier = dossier(id);
        requireViewer(dossier, caller);
        return snapshot(dossier);
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
        MerchantOnboardingCase saved = cases.save(dossier);
        outbox.enqueueApproved(saved);
        return saved;
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

    private void adaptLegacyOutlet(MerchantOnboardingCase dossier) {
        List<OnboardingOutlet> current = outlets.findByCaseIdOrderByCreatedAtAsc(dossier.id());
        if (current.size() > 1)
            throw new IllegalStateException("MIG-002: a multi-outlet dossier must be updated through API v2");
        if (current.isEmpty()) {
            outlets.save(OnboardingOutlet.fromLegacy(dossier.id(), dossier.outletCode(),
                    dossier.outletName(), dossier.outletAddress(), dossier.country()));
            return;
        }
        OnboardingOutlet outlet = current.get(0);
        outlet.change(dossier.outletCode(), dossier.outletName(), true, true,
                dossier.outletAddress(), null, null, "LEGACY", null, null, dossier.country(),
                "LEGACY", "legacy@invalid.local", null, "LEGACY", "LEGACY",
                null, "LEGACY", "legacy@invalid.local", null, null, null, null);
        outlets.save(outlet);
    }

    private DossierV2Snapshot snapshot(MerchantOnboardingCase dossier) {
        return new DossierV2Snapshot(dossier,
                outlets.findByCaseIdOrderByCreatedAtAsc(dossier.id()),
                beneficialOwners.findByCaseIdAndActiveTrueOrderByCreatedAtAsc(dossier.id()),
                outletProducts.findByCaseIdAndActiveTrueOrderByOutletIdAscProductIdAsc(dossier.id()),
                terminalRequests.findByCaseIdOrderByCreatedAtAsc(dossier.id()),
                ecommerceStoreRequests.findByCaseIdOrderByCreatedAtAsc(dossier.id()));
    }

    private void replaceOutletProducts(UUID caseId, UUID outletId, List<OutletProductData> requested) {
        List<OnboardingOutletProduct> existing =
                outletProducts.findByOutletIdAndActiveTrueOrderByProductIdAsc(outletId);
        List<UUID> retained = new ArrayList<>();
        for (OutletProductData input : requested == null ? List.<OutletProductData>of() : requested) {
            if (input.productId() == null)
                throw new IllegalArgumentException("PDV-005: productId is required");
            OnboardingOutletProduct current = existing.stream()
                    .filter(value -> value.productId().equals(input.productId())).findFirst().orElse(null);
            if (current == null) current = outletProducts.save(OnboardingOutletProduct.create(caseId,
                    outletId, input.productId(), input.pricingPackCode(), input.pricingPackVersion(),
                    input.pricingSnapshotJson()));
            retained.add(current.id());
        }
        for (OnboardingOutletProduct current : existing) {
            if (!retained.contains(current.id())) { current.deactivate(); outletProducts.save(current); }
        }
    }

    private void replaceTerminalRequests(UUID caseId, UUID outletId, List<TerminalRequestData> requested) {
        List<TerminalRequest> existing = terminalRequests.findByOutletIdOrderByCreatedAtAsc(outletId);
        List<UUID> retained = new ArrayList<>();
        for (TerminalRequestData input : requested == null ? List.<TerminalRequestData>of() : requested) {
            requireReference("TPE_MODEL", input.modelCode(), "TPE-002: modelCode");
            requireReference("TPE_CONNECTIVITY", input.connectivityCode(), "TPE-003: connectivityCode");
            for (String option : input.optionCodes() == null ? List.<String>of() : input.optionCodes())
                requireReference("TPE_OPTION", option, "TPE-004: optionCode");
            TerminalRequest current = input.id() == null ? null : terminalRequests.findById(input.id()).orElse(null);
            if (current != null && (!current.caseId().equals(caseId) || !current.outletId().equals(outletId)))
                throw new IllegalArgumentException("TPE-001: request does not belong to outlet");
            if (current == null) current = terminalRequests.save(TerminalRequest.create(caseId, input.id(),
                    outletId, input.productId(), input.quantity(), input.modelCode(),
                    input.connectivityCode(), input.optionCodes(), input.externalReference()));
            retained.add(current.id());
        }
        for (TerminalRequest current : existing) {
            if (!retained.contains(current.id()) && current.status() == TerminalRequestStatus.REQUESTED) {
                current.cancel(); terminalRequests.save(current);
            }
        }
    }

    private void replaceEcommerceStores(UUID caseId, UUID outletId, List<EcommerceStoreData> requested) {
        List<EcommerceStoreRequest> existing = ecommerceStoreRequests.findByOutletIdOrderByCreatedAtAsc(outletId);
        List<UUID> retained = new ArrayList<>();
        for (EcommerceStoreData input : requested == null ? List.<EcommerceStoreData>of() : requested) {
            requireReference("CAPTURE_MODE", input.captureMode(), "ECOM-003: captureMode");
            for (String option : input.optionCodes() == null ? List.<String>of() : input.optionCodes())
                requireReference("ECOMMERCE_OPTION", option, "ECOM-003: optionCode");
            EcommerceStoreRequest current = input.id() == null ? null
                    : ecommerceStoreRequests.findById(input.id()).orElse(null);
            if (current != null && (!current.caseId().equals(caseId) || !current.outletId().equals(outletId)))
                throw new IllegalArgumentException("ECOM-001: store does not belong to outlet");
            if (current == null) current = ecommerceStoreRequests.save(EcommerceStoreRequest.create(
                    caseId, input.id(), outletId, input.productId(), input.storeCode(), input.name(),
                    input.allowedDomain(), input.returnUrl(), input.notificationUrl(), input.currency(),
                    input.captureMode(), input.optionCodes(), input.externalReference()));
            retained.add(current.id());
        }
        for (EcommerceStoreRequest current : existing) {
            if (!retained.contains(current.id()) && current.status() == EcommerceStoreRequestStatus.REQUESTED) {
                current.cancel(); ecommerceStoreRequests.save(current);
            }
        }
    }

    private void requireReference(String category, String code, String field) {
        if (!references.existsByIdCategoryAndIdCodeAndActiveTrue(category, code))
            throw new IllegalArgumentException(field + " is unknown or inactive");
    }

    private void validateConfiguredFieldRules(DossierV2Data data) {
        for (OnboardingFieldRule rule : fieldRules
                .findByIdMerchantTypeAndActiveTrueOrderByIdFieldPathAsc(data.merchantType().name())) {
            String value = switch (rule.fieldPath()) {
                case "legalName" -> data.legalName();
                case "tradingName" -> data.tradingName();
                case "registrationNumber" -> data.registrationNumber();
                case "taxIdentifier" -> data.taxIdentifier();
                case "ice" -> data.ice();
                case "legalForm" -> data.legalForm();
                case "businessActivity" -> data.businessActivity();
                case "associationPurpose" -> data.associationPurpose();
                case "primaryPhone" -> data.primaryPhone();
                case "primaryEmail" -> data.primaryEmail();
                case "rib" -> data.rib();
                case "organizationLegalNature" -> data.organizationLegalNature() == null
                        ? null : data.organizationLegalNature().name();
                case "headquartersAddress.line1" -> data.headquartersAddress().line1();
                case "headquartersAddress.city" -> data.headquartersAddress().city();
                case "headquartersAddress.country" -> data.headquartersAddress().country();
                case "representative.firstName" -> data.representative().firstName();
                case "representative.lastName" -> data.representative().lastName();
                case "representative.phone" -> data.representative().phone();
                case "representative.email" -> data.representative().email();
                default -> null;
            };
            if (rule.required() && (value == null || value.isBlank()))
                throw new IllegalArgumentException("MER-002: " + rule.fieldPath() + " is required");
            if (value != null && rule.maxLength() != null && value.trim().length() > rule.maxLength())
                throw new IllegalArgumentException("MER-002: " + rule.fieldPath() + " exceeds configured length");
        }
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
    public record AddressData(String line1, String line2, String district, String city,
            String region, String postalCode, String country) {}
    public record RepresentativeData(String title, String firstName, String lastName,
            LocalDate birthDate, String phone, String email, String idType,
            String idNumber, String residenceCountry, String nationality) {}
    public record BeneficialOwnerData(UUID id, String firstName, String lastName, boolean active) {}
    public record OutletProductData(UUID productId, String pricingPackCode,
            Integer pricingPackVersion, String pricingSnapshotJson) {}
    public record TerminalRequestData(UUID id, UUID productId, int quantity, String modelCode,
            String connectivityCode, List<String> optionCodes, String externalReference) {}
    public record EcommerceStoreData(UUID id, UUID productId, String storeCode, String name,
            String allowedDomain, String returnUrl, String notificationUrl, String currency,
            String captureMode, List<String> optionCodes, String externalReference) {}
    public record OutletData(UUID id, String code, String name, boolean principal, boolean active,
            AddressData address, String contactPhone, String contactEmail,
            RepresentativeData responsible, List<OutletProductData> products,
            List<TerminalRequestData> terminalRequests, List<EcommerceStoreData> ecommerceStores) {
        public OutletData(UUID id, String code, String name, boolean principal, boolean active,
                AddressData address, String contactPhone, String contactEmail,
                RepresentativeData responsible) {
            this(id, code, name, principal, active, address, contactPhone, contactEmail,
                    responsible, List.of(), List.of(), List.of());
        }
    }
    public record DossierV2Data(MerchantType merchantType,
            OrganizationLegalNature organizationLegalNature, String legalName,
            String tradingName, String registrationNumber, String taxIdentifier,
            String ice, String legalForm, String businessActivity, String associationPurpose,
            String primaryPhone, String primaryEmail, AddressData headquartersAddress,
            String mcc, String rib, RepresentativeData representative,
            List<BeneficialOwnerData> beneficialOwners, List<OutletData> outlets, long version) {}
    public record DossierV2Snapshot(MerchantOnboardingCase dossier,
            List<OnboardingOutlet> outlets, List<OnboardingBeneficialOwner> beneficialOwners,
            List<OnboardingOutletProduct> outletProducts,
            List<TerminalRequest> terminalRequests,
            List<EcommerceStoreRequest> ecommerceStores) {}
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
