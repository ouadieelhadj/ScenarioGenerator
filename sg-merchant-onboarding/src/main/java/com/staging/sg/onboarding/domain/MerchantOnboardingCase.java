package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_type", length = 32)
    private MerchantType merchantType;
    @Enumerated(EnumType.STRING)
    @Column(name = "organization_legal_nature", length = 24)
    private OrganizationLegalNature organizationLegalNature;
    @Column(name = "tax_identifier", length = 64)
    private String taxIdentifier;
    @Column(length = 64)
    private String ice;
    @Column(name = "legal_form", length = 96)
    private String legalForm;
    @Column(name = "business_activity", length = 255)
    private String businessActivity;
    @Column(name = "association_purpose", length = 500)
    private String associationPurpose;
    @Column(name = "primary_phone", length = 32)
    private String primaryPhone;
    @Column(name = "primary_email", length = 254)
    private String primaryEmail;
    @Column(name = "headquarters_address_line1", length = 255)
    private String headquartersAddressLine1;
    @Column(name = "headquarters_address_line2", length = 255)
    private String headquartersAddressLine2;
    @Column(name = "headquarters_district", length = 120)
    private String headquartersDistrict;
    @Column(name = "headquarters_city", length = 120)
    private String headquartersCity;
    @Column(name = "headquarters_region", length = 120)
    private String headquartersRegion;
    @Column(name = "headquarters_postal_code", length = 24)
    private String headquartersPostalCode;
    @Column(name = "representative_title", length = 32)
    private String representativeTitle;
    @Column(name = "representative_first_name", length = 96)
    private String representativeFirstName;
    @Column(name = "representative_last_name", length = 96)
    private String representativeLastName;
    @Column(name = "representative_birth_date")
    private LocalDate representativeBirthDate;
    @Column(name = "representative_phone", length = 32)
    private String representativePhone;
    @Column(name = "representative_email", length = 254)
    private String representativeEmail;
    @Column(name = "representative_id_type", length = 32)
    private String representativeIdType;
    @Column(name = "representative_id_number", length = 64)
    private String representativeIdNumber;
    @Column(name = "representative_residence_country", length = 2)
    private String representativeResidenceCountry;
    @Column(name = "representative_nationality", length = 2)
    private String representativeNationality;
    @Column(name = "rib", length = 24)
    private String rib;
    @Column(name = "settlement_account_reference", length = 96)
    private String settlementAccountReference;
    @Column(name = "settlement_currency", length = 3)
    private String settlementCurrency;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "acceptance_channel", length = 16)
    private String acceptanceChannel;
    @Enumerated(EnumType.STRING)
    @Column(name = "provisioning_destination", length = 16)
    private ProvisioningDestination provisioningDestination;
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

    public void selectProvisioningDestination(ProvisioningDestination destination) {
        requireStatus(OnboardingStatus.DRAFT,
                "Provisioning destination can only be selected on a draft dossier");
        if (destination == null)
            throw new IllegalArgumentException("PROV-001: provisioning destination is required");
        this.provisioningDestination = destination;
        this.updatedAt = Instant.now();
    }

    public void updateLegalProfile(MerchantType merchantType,
            OrganizationLegalNature organizationLegalNature, String legalName,
            String tradingName, String registrationNumber, String taxIdentifier,
            String ice, String legalForm, String businessActivity,
            String associationPurpose, String primaryPhone, String primaryEmail,
            String addressLine1, String addressLine2, String district, String city,
            String region, String postalCode, String country, String mcc, String rib,
            String representativeTitle, String representativeFirstName,
            String representativeLastName, LocalDate representativeBirthDate,
            String representativePhone, String representativeEmail,
            String representativeIdType, String representativeIdNumber,
            String representativeResidenceCountry, String representativeNationality) {
        requireStatus(OnboardingStatus.DRAFT, "Only a draft dossier can be updated");
        if (merchantType == null) throw new IllegalArgumentException("MER-001: merchantType is required");
        if (merchantType == MerchantType.ASSOCIATION_FOUNDATION && organizationLegalNature == null)
            throw new IllegalArgumentException("MER-001: organizationLegalNature is required");
        if (merchantType != MerchantType.ASSOCIATION_FOUNDATION && organizationLegalNature != null)
            throw new IllegalArgumentException("MER-001: organizationLegalNature is not applicable");
        require(legalName, "MER-006: legalName", 160);
        require(tradingName, "MER-006: tradingName", 160);
        require(registrationNumber, "MER-006: registrationNumber", 64);
        if (merchantType == MerchantType.PM) require(ice, "MER-003: ice", 64);
        if (merchantType == MerchantType.ASSOCIATION_FOUNDATION)
            require(associationPurpose, "MER-006: associationPurpose", 500);
        else require(businessActivity, "MER-006: businessActivity", 255);
        require(primaryPhone, "MER-006: primaryPhone", 32);
        email(primaryEmail, "MER-006: primaryEmail");
        require(addressLine1, "ADR-001: headquarters.line1", 255);
        require(city, "ADR-001: headquarters.city", 120);
        country(country, "ADR-001: headquarters.country");
        if (mcc == null || !mcc.matches("\\d{4}")) throw new IllegalArgumentException("REF-002: invalid mcc");
        String normalizedRib = require(rib, "MER-007: rib", 24).replace(" ", "");
        if (normalizedRib.length() > 24) throw new IllegalArgumentException("MER-007: rib exceeds 24 characters");
        require(representativeFirstName, "MER-004: representative.firstName", 96);
        require(representativeLastName, "MER-004: representative.lastName", 96);
        require(representativePhone, "MER-004: representative.phone", 32);
        email(representativeEmail, "MER-004: representative.email");
        require(representativeIdType, "MER-004: representative.idType", 32);
        require(representativeIdNumber, "MER-004: representative.idNumber", 64);
        country(representativeResidenceCountry, "MER-004: representative.residenceCountry");
        country(representativeNationality, "MER-004: representative.nationality");

        this.merchantType = merchantType;
        this.organizationLegalNature = organizationLegalNature;
        this.legalName = legalName.trim();
        this.tradingName = tradingName.trim();
        this.registrationNumber = registrationNumber.trim();
        this.taxIdentifier = optional(taxIdentifier, 64);
        this.ice = optional(ice, 64);
        this.legalForm = optional(legalForm, 96);
        this.businessActivity = optional(businessActivity, 255);
        this.associationPurpose = optional(associationPurpose, 500);
        this.primaryPhone = primaryPhone.trim();
        this.primaryEmail = primaryEmail.trim();
        this.headquartersAddressLine1 = addressLine1.trim();
        this.headquartersAddressLine2 = optional(addressLine2, 255);
        this.headquartersDistrict = optional(district, 120);
        this.headquartersCity = city.trim();
        this.headquartersRegion = optional(region, 120);
        this.headquartersPostalCode = optional(postalCode, 24);
        this.country = country;
        this.mcc = mcc;
        this.rib = normalizedRib;
        this.settlementAccountReference = normalizedRib;
        this.representativeTitle = optional(representativeTitle, 32);
        this.representativeFirstName = representativeFirstName.trim();
        this.representativeLastName = representativeLastName.trim();
        this.representativeBirthDate = representativeBirthDate;
        this.representativePhone = representativePhone.trim();
        this.representativeEmail = representativeEmail.trim();
        this.representativeIdType = representativeIdType.trim();
        this.representativeIdNumber = representativeIdNumber.trim();
        this.representativeResidenceCountry = representativeResidenceCountry;
        this.representativeNationality = representativeNationality;
        this.updatedAt = Instant.now();
    }

    private static String require(String value, String field, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Value is too long");
        return value.trim();
    }
    private static void email(String value, String field) {
        String normalized = require(value, field, 254);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException(field + " is invalid");
    }
    private static void country(String value, String field) {
        if (value == null || !value.matches("[A-Z]{2}"))
            throw new IllegalArgumentException(field + " is invalid");
    }

    public void submit(String maker) {
        requireStatus(OnboardingStatus.DRAFT, "Only a draft dossier can be submitted");
        if (legalName == null) throw new IllegalStateException("Dossier is incomplete");
        if (provisioningDestination == null)
            throw new IllegalStateException("PROV-001: provisioning destination is required before submission");
        if (provisioningDestination.includesWay4() && merchantType == null)
            throw new IllegalStateException("PROV-002: WAY4 requires the complete v2 legal profile");
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
        if (provisioningDestination == null)
            throw new IllegalStateException("PROV-001: provisioning destination is required before approval");
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
    public MerchantType merchantType() { return merchantType; }
    public OrganizationLegalNature organizationLegalNature() { return organizationLegalNature; }
    public String taxIdentifier() { return taxIdentifier; }
    public String ice() { return ice; }
    public String legalForm() { return legalForm; }
    public String businessActivity() { return businessActivity; }
    public String associationPurpose() { return associationPurpose; }
    public String primaryPhone() { return primaryPhone; }
    public String primaryEmail() { return primaryEmail; }
    public String headquartersAddressLine1() { return headquartersAddressLine1; }
    public String headquartersAddressLine2() { return headquartersAddressLine2; }
    public String headquartersDistrict() { return headquartersDistrict; }
    public String headquartersCity() { return headquartersCity; }
    public String headquartersRegion() { return headquartersRegion; }
    public String headquartersPostalCode() { return headquartersPostalCode; }
    public String rib() { return rib; }
    public String representativeTitle() { return representativeTitle; }
    public String representativeFirstName() { return representativeFirstName; }
    public String representativeLastName() { return representativeLastName; }
    public LocalDate representativeBirthDate() { return representativeBirthDate; }
    public String representativePhone() { return representativePhone; }
    public String representativeEmail() { return representativeEmail; }
    public String representativeIdType() { return representativeIdType; }
    public String representativeIdNumber() { return representativeIdNumber; }
    public String representativeResidenceCountry() { return representativeResidenceCountry; }
    public String representativeNationality() { return representativeNationality; }
    public String settlementAccountReference() { return settlementAccountReference; }
    public String settlementCurrency() { return settlementCurrency; }
    public UUID productId() { return productId; }
    public String acceptanceChannel() { return acceptanceChannel; }
    public ProvisioningDestination provisioningDestination() { return provisioningDestination; }
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
    public long version() { return version; }
}
