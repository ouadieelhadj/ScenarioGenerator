export type AccountStatus = 'INVITATION_PENDING' | 'ACTIVE' | 'SUSPENDED';
export type OnboardingStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' |
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
  acceptanceChannel: string | null;
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
