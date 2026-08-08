export type AccountStatus = 'INVITATION_PENDING' | 'ACTIVE' | 'SUSPENDED';
export type OnboardingStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' |
  'QUEUED_FOR_PROVISIONING' | 'PROVISIONING' | 'PROVISIONED' | 'PROVISIONING_FAILED';
export type KycStatus = 'NOT_STARTED' | 'PENDING_REVIEW' | 'COMPLEMENTS_REQUIRED' |
  'VALIDATED' | 'REJECTED';

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
