export type AccountStatus = 'INVITATION_PENDING' | 'ACTIVE' | 'SUSPENDED';
export type OnboardingStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' |
  'QUEUED_FOR_PROVISIONING' | 'PROVISIONING' | 'PROVISIONED' | 'PROVISIONING_FAILED';
export type KycStatus = 'NOT_STARTED' | 'PENDING_REVIEW' | 'COMPLEMENTS_REQUIRED' |
  'VALIDATED' | 'REJECTED';
export type ProvisioningDestination = 'FUTURPAYMENT' | 'WAY4' | 'BOTH';

export interface MerchantPortalAccount {
  id: string;
  login: string;
  email: string;
  status: AccountStatus;
  identityUserId: string | null;
}

export interface MerchantDossier {
  id: string;
  reference: string;
  accountId: string;
  acquirerId: string;
  legalName: string | null;
  tradingName: string | null;
  registrationNumber: string | null;
  country: string | null;
  mcc: string | null;
  settlementAccountReference: string | null;
  settlementCurrency: string | null;
  productId: string | null;
  acceptanceChannel: string | null;
  outletCode: string | null;
  outletName: string | null;
  outletAddress: string | null;
  terminalCount: number;
  provisioningDestination: ProvisioningDestination | null;
  status: OnboardingStatus;
  kycStatus: KycStatus;
  kycSubmittedBy: string | null;
  kycReviewedBy: string | null;
  complementReason: string | null;
  submittedBy: string | null;
  checkedBy: string | null;
  rejectionReason: string | null;
  acquiringMerchantId: string | null;
  merchantAcceptorId: string | null;
  createdAt: string;
}

export interface MerchantDossierUpdate {
  legalName: string;
  tradingName: string;
  registrationNumber: string;
  country: string;
  mcc: string;
  settlementAccountReference: string;
  settlementCurrency: string;
  productId: string;
  acceptanceChannel: 'TPE' | 'ECOMMERCE' | 'BOTH';
  outletCode: string;
  outletName: string;
  outletAddress: string;
  terminalCount: number;
}

export type MerchantType = 'PP' | 'PM' | 'AE' | 'ASSOCIATION_FOUNDATION';
export type OrganizationLegalNature = 'ASSOCIATION' | 'FOUNDATION';

export interface MerchantAddressV2 {
  line1: string; line2: string | null; district: string | null; city: string;
  region: string | null; postalCode: string | null; country: string;
}
export interface MerchantRepresentativeV2 {
  title: string | null; firstName: string; lastName: string; birthDate: string | null;
  phone: string; email: string; idType: string; idNumber: string;
  residenceCountry: string; nationality: string;
}
export interface MerchantOutletProductV2 {
  productId: string; pricingPackCode: string | null; pricingPackVersion: number | null;
  pricingSnapshotJson: string | null;
}
export interface MerchantTerminalRequestV2 {
  id: string | null; productId: string; quantity: number; modelCode: string;
  connectivityCode: string; optionCodes: string[]; status?: string; externalReference: string | null;
}
export interface MerchantEcommerceStoreV2 {
  id: string | null; productId: string; storeCode: string; name: string;
  allowedDomain: string; returnUrl: string; notificationUrl: string; currency: string;
  captureMode: string; optionCodes: string[]; status?: string; externalReference: string | null;
}
export interface MerchantOutletV2 {
  id: string | null; code: string; name: string; principal: boolean; active: boolean;
  address: MerchantAddressV2; contactPhone: string; contactEmail: string;
  responsible: MerchantRepresentativeV2; products: MerchantOutletProductV2[];
  terminalRequests: MerchantTerminalRequestV2[]; ecommerceStores: MerchantEcommerceStoreV2[];
  version?: number;
}
export interface MerchantDossierV2 {
  id: string; reference: string; merchantType: MerchantType;
  provisioningDestination: ProvisioningDestination;
  organizationLegalNature: OrganizationLegalNature | null; legalName: string; tradingName: string;
  registrationNumber: string; taxIdentifier: string | null; ice: string | null;
  legalForm: string | null; businessActivity: string | null; associationPurpose: string | null;
  primaryPhone: string; primaryEmail: string; headquartersAddress: MerchantAddressV2;
  mcc: string; rib: string; representative: MerchantRepresentativeV2;
  beneficialOwners: Array<{ id: string | null; firstName: string; lastName: string; active: boolean }>;
  outlets: MerchantOutletV2[]; status: OnboardingStatus; version: number;
}
export type MerchantDossierV2Update = Omit<MerchantDossierV2, 'id' | 'reference' | 'status'>;

export interface MerchantReferenceValue {
  category: string; code: string; label: string;
}

export type MerchantDocumentType = 'LEGAL_EXISTENCE' | 'REPRESENTATIVE_IDENTITY' |
  'BANK_ACCOUNT_PROOF' | 'TAX_REGISTRATION' | 'ADDRESS_PROOF' |
  'SIGNED_TPE_CONTRACT' | 'SIGNED_ECOMMERCE_CONTRACT' | 'SIGNED_CRC';
export type MerchantDocumentReviewStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface MerchantDocument {
  id: string;
  caseId: string;
  type: MerchantDocumentType;
  version: number;
  storageReference: string;
  contentType: string;
  contentLength: number;
  sha256: string;
  reviewStatus: MerchantDocumentReviewStatus;
  uploadedBy: string;
  reviewedBy: string | null;
  rejectionReason: string | null;
}

export interface MerchantProvisioningView {
  dossier: MerchantDossier;
  jobId: string | null;
  jobStatus: string | null;
  result: {
    merchantId?: string;
    merchantAcceptorId?: string;
    terminals?: Array<{ terminalId: string; terminalDeviceId?: string | null }>;
  } | null;
  error: string | null;
}

export interface IdentityInvitation {
  userId: number;
  invitationId: string;
  activationToken: string;
  expiresAt: string;
}

export interface MerchantProspect {
  account: MerchantPortalAccount;
  dossier: MerchantDossier;
  identityInvitation: IdentityInvitation | null;
}

export interface CreateMerchantProspectRequest {
  login: string;
  email: string;
  acquirerId: string;
}

export interface MerchantActivationResponse {
  userId: number;
  status: string;
}

export interface Way4ExportCandidate {
  caseId: string; reference: string; legalName: string; registrationNumber: string;
  applicationRegNumber: string; status: 'PENDING' | 'REJECTED';
  lastErrorCode: string | null; lastErrorMessage: string | null;
}
export interface Way4BatchResult {
  fileId: string; fileName: string; merchantCount: number; status: string;
  xmlSha256: string; xsdSha256: string; xml: string;
}
export interface FuturPaymentCandidate {
  eventId: string; caseId: string; reference: string; legalName: string; registrationNumber: string;
  status: 'PENDING' | 'PROCESSING' | 'FAILED_FINAL'; attempts: number;
  lastErrorCode: string | null; lastErrorMessage: string | null;
}
