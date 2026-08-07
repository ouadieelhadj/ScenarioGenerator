package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_onboarding_case", uniqueConstraints = {
        @UniqueConstraint(name = "uk_merchant_onboarding_reference", columnNames = "case_reference"),
        @UniqueConstraint(name = "uk_merchant_onboarding_registration", columnNames = {"acquirer_id", "registration_number"})
})
public class MerchantOnboardingCase {
    @Id
    private UUID id;
    @Column(name = "case_reference", nullable = false, length = 40, updatable = false)
    private String reference;
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "created_by_commercial", nullable = false, length = 96, updatable = false)
    private String createdByCommercial;
    @Column(name = "legal_name", length = 160)
    private String legalName;
    @Column(name = "trading_name", length = 160)
    private String tradingName;
    @Column(name = "registration_number", length = 64)
    private String registrationNumber;
    @Column(length = 2)
    private String country;
    @Column(length = 4)
    private String mcc;
    @Column(name = "settlement_account_reference", length = 96)
    private String settlementAccountReference;
    @Column(name = "settlement_currency", length = 3)
    private String settlementCurrency;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "acceptance_channel", length = 16)
    private String acceptanceChannel;
    @Column(name = "outlet_code", length = 64)
    private String outletCode;
    @Column(name = "outlet_name", length = 160)
    private String outletName;
    @Column(name = "outlet_address", length = 255)
    private String outletAddress;
    @Column(name = "terminal_count")
    private int terminalCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OnboardingStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus;
    @Column(name = "kyc_submitted_by", length = 96)
    private String kycSubmittedBy;
    @Column(name = "kyc_reviewed_by", length = 96)
    private String kycReviewedBy;
    @Column(name = "complement_reason", length = 500)
    private String complementReason;
    @Column(name = "submitted_by", length = 96)
    private String submittedBy;
    @Column(name = "checked_by", length = 96)
    private String checkedBy;
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    @Column(name = "acquiring_merchant_id")
    private UUID acquiringMerchantId;
    @Column(name = "merchant_acceptor_id", length = 15)
    private String merchantAcceptorId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected MerchantOnboardingCase() {}

    public static MerchantOnboardingCase prospect(UUID accountId, String acquirerId, String commercial) {
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        MerchantPortalAccount.requireText(acquirerId, "acquirerId");
        MerchantPortalAccount.requireText(commercial, "commercial");
        MerchantOnboardingCase value = new MerchantOnboardingCase();
        value.id = UUID.randomUUID();
        value.reference = "ONB-" + value.id.toString().substring(0, 8).toUpperCase();
        value.accountId = accountId;
        value.acquirerId = acquirerId.trim();
        value.createdByCommercial = commercial.trim();
        value.status = OnboardingStatus.DRAFT;
        value.kycStatus = KycStatus.NOT_STARTED;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void updateDossier(String legalName, String tradingName, String registrationNumber,
            String country, String mcc, String settlementAccountReference,
            String settlementCurrency, UUID productId, String acceptanceChannel,
            String outletCode, String outletName, String outletAddress, int terminalCount) {
        requireStatus(OnboardingStatus.DRAFT, "Only a draft dossier can be updated");
        MerchantPortalAccount.requireText(legalName, "legalName");
        MerchantPortalAccount.requireText(tradingName, "tradingName");
        MerchantPortalAccount.requireText(registrationNumber, "registrationNumber");
        if (country == null || !country.matches("[A-Z]{2}")) throw new IllegalArgumentException("Invalid country");
        if (mcc == null || !mcc.matches("\\d{4}")) throw new IllegalArgumentException("Invalid mcc");
        MerchantPortalAccount.requireText(settlementAccountReference, "settlementAccountReference");
        if (settlementCurrency == null || !settlementCurrency.matches("\\d{3}")) throw new IllegalArgumentException("Invalid currency");
        if (productId == null) throw new IllegalArgumentException("productId is required");
        if (!"TPE".equals(acceptanceChannel) && !"ECOMMERCE".equals(acceptanceChannel)
                && !"BOTH".equals(acceptanceChannel)) throw new IllegalArgumentException("Invalid acceptanceChannel");
        MerchantPortalAccount.requireText(outletCode, "outletCode");
        MerchantPortalAccount.requireText(outletName, "outletName");
        MerchantPortalAccount.requireText(outletAddress, "outletAddress");
        if (terminalCount < 0 || terminalCount > 999) throw new IllegalArgumentException("Invalid terminalCount");
        if ("TPE".equals(acceptanceChannel) && terminalCount == 0) throw new IllegalArgumentException("TPE requires a terminal");
        this.legalName = legalName.trim();
        this.tradingName = tradingName.trim();
        this.registrationNumber = registrationNumber.trim();
        this.country = country;
        this.mcc = mcc;
        this.settlementAccountReference = settlementAccountReference.trim();
        this.settlementCurrency = settlementCurrency;
        this.productId = productId;
        this.acceptanceChannel = acceptanceChannel;
        this.outletCode = outletCode.trim();
        this.outletName = outletName.trim();
        this.outletAddress = outletAddress.trim();
        this.terminalCount = terminalCount;
        this.updatedAt = Instant.now();
    }

    public void submit(String maker) {
        requireStatus(OnboardingStatus.DRAFT, "Only a draft dossier can be submitted");
        if (legalName == null) throw new IllegalStateException("Dossier is incomplete");
        if (kycStatus != KycStatus.VALIDATED) throw new IllegalStateException("KYC must be validated before Maker/Checker");
        MerchantPortalAccount.requireText(maker, "maker");
        submittedBy = maker.trim();
        status = OnboardingStatus.PENDING_APPROVAL;
        updatedAt = Instant.now();
    }

    public void submitKyc(String submitter) {
        requireStatus(OnboardingStatus.DRAFT, "KYC can only be submitted for a draft dossier");
        if (legalName == null) throw new IllegalStateException("Dossier is incomplete");
        if (kycStatus != KycStatus.NOT_STARTED && kycStatus != KycStatus.COMPLEMENTS_REQUIRED)
            throw new IllegalStateException("KYC is not ready for submission");
        MerchantPortalAccount.requireText(submitter, "submitter");
        kycSubmittedBy = submitter.trim();
        kycStatus = KycStatus.PENDING_REVIEW;
        complementReason = null;
        updatedAt = Instant.now();
    }

    public void requestKycComplements(String reviewer, String reason) {
        requireKycPending(reviewer);
        MerchantPortalAccount.requireText(reason, "reason");
        kycReviewedBy = reviewer.trim();
        complementReason = reason.trim();
        kycStatus = KycStatus.COMPLEMENTS_REQUIRED;
        updatedAt = Instant.now();
    }

    public void validateKyc(String reviewer) {
        requireKycPending(reviewer);
        kycReviewedBy = reviewer.trim();
        complementReason = null;
        kycStatus = KycStatus.VALIDATED;
        updatedAt = Instant.now();
    }

    public void rejectKyc(String reviewer, String reason) {
        requireKycPending(reviewer);
        MerchantPortalAccount.requireText(reason, "reason");
        kycReviewedBy = reviewer.trim();
        complementReason = reason.trim();
        kycStatus = KycStatus.REJECTED;
        updatedAt = Instant.now();
    }

    private void requireKycPending(String reviewer) {
        if (kycStatus != KycStatus.PENDING_REVIEW) throw new IllegalStateException("KYC is not pending review");
        MerchantPortalAccount.requireText(reviewer, "reviewer");
        if (reviewer.trim().equals(kycSubmittedBy))
            throw new IllegalStateException("KYC submitter and reviewer must be different");
    }

    public void approve(String checker) {
        requireStatus(OnboardingStatus.PENDING_APPROVAL, "Dossier is not pending approval");
        MerchantPortalAccount.requireText(checker, "checker");
        if (checker.trim().equals(submittedBy)) throw new IllegalStateException("Maker and checker must be different");
        checkedBy = checker.trim();
        status = OnboardingStatus.APPROVED;
        updatedAt = Instant.now();
    }

    public void reject(String checker, String reason) {
        requireStatus(OnboardingStatus.PENDING_APPROVAL, "Dossier is not pending approval");
        MerchantPortalAccount.requireText(checker, "checker");
        MerchantPortalAccount.requireText(reason, "reason");
        if (checker.trim().equals(submittedBy)) throw new IllegalStateException("Maker and checker must be different");
        checkedBy = checker.trim();
        rejectionReason = reason.trim();
        status = OnboardingStatus.REJECTED;
        updatedAt = Instant.now();
    }

    public void queue() {
        requireStatus(OnboardingStatus.APPROVED, "Only an approved dossier can be provisioned");
        status = OnboardingStatus.QUEUED_FOR_PROVISIONING;
        updatedAt = Instant.now();
    }

    public void startProvisioning() {
        if (status != OnboardingStatus.APPROVED && status != OnboardingStatus.QUEUED_FOR_PROVISIONING
                && status != OnboardingStatus.PROVISIONING_FAILED) {
            throw new IllegalStateException("Dossier is not ready for provisioning");
        }
        status = OnboardingStatus.PROVISIONING;
        updatedAt = Instant.now();
    }

    public void provisioned(UUID merchantId, String mid) {
        requireStatus(OnboardingStatus.PROVISIONING, "Dossier is not being provisioned");
        if (merchantId == null) throw new IllegalArgumentException("merchantId is required");
        MerchantPortalAccount.requireText(mid, "mid");
        acquiringMerchantId = merchantId;
        merchantAcceptorId = mid.trim();
        status = OnboardingStatus.PROVISIONED;
        updatedAt = Instant.now();
    }

    public void provisioningFailed() {
        requireStatus(OnboardingStatus.PROVISIONING, "Dossier is not being provisioned");
        status = OnboardingStatus.PROVISIONING_FAILED;
        updatedAt = Instant.now();
    }

    private void requireStatus(OnboardingStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    public UUID id() { return id; }
    public String reference() { return reference; }
    public UUID accountId() { return accountId; }
    public String acquirerId() { return acquirerId; }
    public String createdByCommercial() { return createdByCommercial; }
    public String legalName() { return legalName; }
    public String tradingName() { return tradingName; }
    public String registrationNumber() { return registrationNumber; }
    public String country() { return country; }
    public String mcc() { return mcc; }
    public String settlementAccountReference() { return settlementAccountReference; }
    public String settlementCurrency() { return settlementCurrency; }
    public UUID productId() { return productId; }
    public String acceptanceChannel() { return acceptanceChannel; }
    public String outletCode() { return outletCode; }
    public String outletName() { return outletName; }
    public String outletAddress() { return outletAddress; }
    public int terminalCount() { return terminalCount; }
    public OnboardingStatus status() { return status; }
    public KycStatus kycStatus() { return kycStatus; }
    public String kycSubmittedBy() { return kycSubmittedBy; }
    public String kycReviewedBy() { return kycReviewedBy; }
    public String complementReason() { return complementReason; }
    public String submittedBy() { return submittedBy; }
    public String checkedBy() { return checkedBy; }
    public String rejectionReason() { return rejectionReason; }
    public UUID acquiringMerchantId() { return acquiringMerchantId; }
    public String merchantAcceptorId() { return merchantAcceptorId; }
    public Instant createdAt() { return createdAt; }
}
