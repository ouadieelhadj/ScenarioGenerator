package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.service.MerchantOnboardingService;
import com.staging.sg.onboarding.service.OnboardingDocumentStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant-onboarding/v1")
public class MerchantOnboardingController {
    private final MerchantOnboardingService service;
    private final OnboardingDocumentStorage storage;

    public MerchantOnboardingController(MerchantOnboardingService service,
            OnboardingDocumentStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @PostMapping("/prospects")
    @PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL') or hasAuthority('ONBOARDING_PROSPECT_CREATE')")
    public ResponseEntity<ProspectView> createProspect(@Valid @RequestBody ProspectRequest request,
            Authentication authentication, @RequestHeader("Authorization") String authorization) {
        MerchantOnboardingService.Prospect value = service.createProspect(
                request.login(), request.email(), request.acquirerId(), authentication.getName(), authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProspectView.from(value));
    }

    @PostMapping("/accounts/{accountId}/identity-link")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ONBOARDING_IDENTITY_LINK')")
    public AccountView linkIdentity(@PathVariable UUID accountId,
            @Valid @RequestBody IdentityLinkRequest request) {
        return AccountView.from(service.linkIdentity(accountId, request.identityUserId()));
    }

    @PutMapping("/dossiers/{id}")
    public DossierView updateDossier(@PathVariable UUID id, @Valid @RequestBody DossierRequest request,
            Authentication authentication) {
        MerchantOnboardingService.DossierData data = new MerchantOnboardingService.DossierData(
                request.legalName(), request.tradingName(), request.registrationNumber(),
                request.country(), request.mcc(), request.settlementAccountReference(),
                request.settlementCurrency(), request.productId(), request.acceptanceChannel(),
                request.outletCode(), request.outletName(), request.outletAddress(), request.terminalCount());
        return DossierView.from(service.updateDossier(id, data, authentication.getName()));
    }

    @GetMapping("/dossiers/{id}")
    public DossierView get(@PathVariable UUID id, Authentication authentication) {
        return DossierView.from(service.get(id, authentication.getName()));
    }

    @GetMapping("/dossiers/mine")
    @PreAuthorize("hasAnyRole('ADMIN','MERCHANT','COMMERCANT')")
    public DossierView mine(Authentication authentication) {
        return DossierView.from(service.myDossier(authentication.getName()));
    }

    @GetMapping("/review/dossiers")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAnyAuthority('ONBOARDING_KYC_REVIEW','ONBOARDING_APPROVE')")
    public List<DossierView> reviewQueue() {
        return service.reviewQueue().stream().map(DossierView::from).toList();
    }

    @GetMapping("/review/dossiers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAnyAuthority('ONBOARDING_KYC_REVIEW','ONBOARDING_APPROVE')")
    public DossierView getForReview(@PathVariable UUID id) {
        return DossierView.from(service.getForReview(id));
    }

    @GetMapping("/review/dossiers/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAnyAuthority('ONBOARDING_KYC_REVIEW','ONBOARDING_APPROVE')")
    public List<DocumentView> documentsForReview(@PathVariable UUID id) {
        return service.documentsForReview(id).stream().map(DocumentView::from).toList();
    }

    @PostMapping("/dossiers/{id}/documents")
    public ResponseEntity<DocumentView> addDocument(@PathVariable UUID id,
            @Valid @RequestBody DocumentRequest request, Authentication authentication) {
        OnboardingDocument value = service.addDocument(id, request.type(), request.storageReference(),
                request.contentType(), request.contentLength(), request.sha256(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentView.from(value));
    }

    @PostMapping(value = "/dossiers/{id}/document-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentView> uploadDocument(@PathVariable UUID id,
            @RequestParam DocumentType type, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        var stored = storage.store(id, file);
        OnboardingDocument value = service.addDocument(id, type, stored.storageReference(),
                stored.contentType(), stored.contentLength(), stored.sha256(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentView.from(value));
    }

    @GetMapping("/dossiers/{id}/documents")
    public List<DocumentView> documents(@PathVariable UUID id, Authentication authentication) {
        return service.documents(id, authentication.getName()).stream().map(DocumentView::from).toList();
    }

    @GetMapping("/dossiers/{id}/documents/{documentId}/content")
    public ResponseEntity<Resource> documentContent(@PathVariable UUID id,
            @PathVariable UUID documentId, Authentication authentication) {
        OnboardingDocument document = service.documentContent(id, documentId, authentication.getName());
        return content(document);
    }

    @GetMapping("/review/documents/{documentId}/content")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAnyAuthority('ONBOARDING_KYC_REVIEW','ONBOARDING_APPROVE')")
    public ResponseEntity<Resource> reviewDocumentContent(@PathVariable UUID documentId) {
        return content(service.documentContentForReview(documentId));
    }

    @PostMapping("/dossiers/{id}/kyc/submit")
    public DossierView submitKyc(@PathVariable UUID id, Authentication authentication) {
        return DossierView.from(service.submitKyc(id, authentication.getName()));
    }

    @PostMapping("/dossiers/{id}/kyc/validate")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_KYC_REVIEW')")
    public DossierView validateKyc(@PathVariable UUID id, Authentication authentication) {
        return DossierView.from(service.validateKyc(id, authentication.getName()));
    }

    @PostMapping("/dossiers/{id}/kyc/complements")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_KYC_REVIEW')")
    public DossierView requestComplements(@PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return DossierView.from(service.requestKycComplements(id, authentication.getName(), request.reason()));
    }

    @PostMapping("/dossiers/{id}/kyc/reject")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_KYC_REVIEW')")
    public DossierView rejectKyc(@PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return DossierView.from(service.rejectKyc(id, authentication.getName(), request.reason()));
    }

    @PostMapping("/documents/{documentId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_KYC_REVIEW')")
    public DocumentView reviewDocument(@PathVariable UUID documentId,
            @Valid @RequestBody DocumentReviewRequest request, Authentication authentication) {
        return DocumentView.from(service.reviewDocument(documentId, request.accepted(),
                request.reason(), authentication.getName()));
    }

    @PostMapping("/dossiers/{id}/submit")
    public WorkflowView submit(@PathVariable UUID id, Authentication authentication) {
        return WorkflowView.from(service.submit(id, authentication.getName()));
    }

    @PostMapping("/dossiers/{id}/provision")
    @PreAuthorize("hasAnyRole('ADMIN','CHECKER','BACK_OFFICE') or hasAuthority('ONBOARDING_PROVISION')")
    public ProvisioningView provision(@PathVariable UUID id,
            @RequestParam ProvisioningMode mode,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return ProvisioningView.from(service.requestProvisioning(id, mode, correlationId));
    }

    public record ProspectRequest(@NotBlank String login, @Email @NotBlank String email,
            @NotBlank String acquirerId) {}
    public record IdentityLinkRequest(@NotBlank String identityUserId) {}
    public record DocumentRequest(@NotNull DocumentType type, @NotBlank String storageReference,
            @NotBlank String contentType, @Min(1) @Max(20_000_000) long contentLength,
            @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256) {}
    public record ReviewRequest(@NotBlank String reason) {}
    public record DocumentReviewRequest(boolean accepted, String reason) {}
    public record DossierRequest(@NotBlank String legalName, @NotBlank String tradingName,
            @NotBlank String registrationNumber, @Pattern(regexp = "[A-Z]{2}") String country,
            @Pattern(regexp = "\\d{4}") String mcc, @NotBlank String settlementAccountReference,
            @Pattern(regexp = "\\d{3}") String settlementCurrency, @NotNull UUID productId,
            @Pattern(regexp = "TPE|ECOMMERCE|BOTH") String acceptanceChannel,
            @NotBlank String outletCode, @NotBlank String outletName, @NotBlank String outletAddress,
            @Min(0) @Max(999) int terminalCount) {}

    public record AccountView(UUID id, String login, String email, AccountStatus status, String identityUserId) {
        static AccountView from(MerchantPortalAccount value) {
            return new AccountView(value.id(), value.login(), value.email(), value.status(), value.identityUserId());
        }
    }
    public record IdentityInvitationView(Long userId, UUID invitationId,
            String activationToken, Instant expiresAt) {}
    public record ProspectView(AccountView account, DossierView dossier,
            IdentityInvitationView identityInvitation) {
        static ProspectView from(MerchantOnboardingService.Prospect value) {
            var invitation = value.identityInvitation();
            return new ProspectView(AccountView.from(value.account()), DossierView.from(value.dossier()),
                    invitation == null ? null : new IdentityInvitationView(invitation.userId(),
                            invitation.invitationId(), invitation.activationToken(), invitation.expiresAt()));
        }
    }
    public record DossierView(UUID id, String reference, UUID accountId, String acquirerId,
            String legalName, String tradingName, String registrationNumber, String country,
            String mcc, String settlementAccountReference, String settlementCurrency,
            UUID productId, String acceptanceChannel, String outletCode, String outletName,
            String outletAddress, int terminalCount, OnboardingStatus status,
            KycStatus kycStatus, String kycSubmittedBy, String kycReviewedBy, String complementReason,
            String submittedBy, String checkedBy, String rejectionReason,
            UUID acquiringMerchantId, String merchantAcceptorId, Instant createdAt) {
        static DossierView from(MerchantOnboardingCase value) {
            return new DossierView(value.id(), value.reference(), value.accountId(), value.acquirerId(),
                    value.legalName(), value.tradingName(), value.registrationNumber(), value.country(),
                    value.mcc(), value.settlementAccountReference(), value.settlementCurrency(),
                    value.productId(), value.acceptanceChannel(), value.outletCode(), value.outletName(),
                    value.outletAddress(), value.terminalCount(), value.status(),
                    value.kycStatus(), value.kycSubmittedBy(), value.kycReviewedBy(), value.complementReason(),
                    value.submittedBy(), value.checkedBy(), value.rejectionReason(),
                    value.acquiringMerchantId(), value.merchantAcceptorId(), value.createdAt());
        }
    }
    public record DocumentView(UUID id, UUID caseId, DocumentType type, int version,
            String storageReference, String contentType, long contentLength, String sha256,
            DocumentReviewStatus reviewStatus, String uploadedBy, String reviewedBy,
            String rejectionReason) {
        static DocumentView from(OnboardingDocument value) {
            return new DocumentView(value.id(), value.caseId(), value.type(), value.documentVersion(),
                    value.storageReference(), value.contentType(), value.contentLength(), value.sha256(),
                    value.reviewStatus(), value.uploadedBy(), value.reviewedBy(), value.rejectionReason());
        }
    }
    public record WorkflowView(Long id, UUID caseId, String moduleCode, String operationType,
            String objectReference, String status, String createdBy, Instant createdAt) {
        static WorkflowView from(WorkflowApprovalRequest value) {
            return new WorkflowView(value.id(), value.caseId(), value.moduleCode(), value.operationType(),
                    value.objectReference(), value.status(), value.createdBy(), value.createdAt());
        }
    }
    public record ProvisioningView(DossierView dossier, UUID jobId, String jobStatus,
            Object result, String error) {
        static ProvisioningView from(MerchantOnboardingService.ProvisioningOutcome value) {
            return new ProvisioningView(DossierView.from(value.dossier()),
                    value.job() == null ? null : value.job().id(),
                    value.job() == null ? null : value.job().status().name(), value.result(), value.error());
        }
    }

    private ResponseEntity<Resource> content(OnboardingDocument document) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header("Content-Disposition", "inline; filename=\"" + document.type().name().toLowerCase() + "-v" + document.documentVersion() + "\"")
                .body(storage.load(document.storageReference()));
    }
}
