import type { CurrentUser } from '../../app/core/models/auth.models';
import type { MerchantDocument } from '../../app/core/models/merchant-onboarding.models';

export function commercialAccess(user: CurrentUser | null): boolean {
  return !!user && (['COMMERCIAL', 'ADMIN'].includes(user.role) ||
    (user.role === 'ACQUIRER_ADMIN' && user.permissions.includes('ONBOARDING_PROSPECT_CREATE')));
}
export function merchantAccess(user: CurrentUser | null): boolean {
  return !!user && ['MERCHANT', 'COMMERCANT', 'ADMIN'].includes(user.role);
}
export function reviewAccess(user: CurrentUser | null): boolean {
  return !!user && (['ADMIN', 'CHECKER', 'BACK_OFFICE'].includes(user.role) ||
    user.permissions.some(p => ['ONBOARDING_KYC_REVIEW', 'ONBOARDING_APPROVE'].includes(p)));
}
export function reviewWriteAccess(user: CurrentUser | null): boolean {
  return !!user && (['ADMIN', 'CHECKER', 'BACK_OFFICE'].includes(user.role) || user.permissions.includes('ONBOARDING_KYC_REVIEW'));
}
const labels: Record<string, string> = {
  MERCHANT: 'Commerçant', COMMERCANT: 'Commerçant', COMMERCIAL: 'Commercial', ADMIN: 'Administrateur',
  BACK_OFFICE: 'Back-office', ACQUIRER_ADMIN: 'Administrateur acquéreur', CHECKER: 'Contrôleur', MAKER: 'Gestionnaire', VIEWER: 'Consultation',
  ACCOUNTING: 'Comptabilité', DRAFT: 'À compléter', AWAITING_CONFIRMATION: 'Confirmation attendue',
  IN_REVIEW: 'En revue commerciale', TO_DISTRIBUTE: 'À affecter', ASSIGNMENT_EXCEPTION: 'Affectation à résoudre',
  COMPLEMENTS_REQUIRED: 'Compléments demandés', REEXAMINATION: 'À réexaminer', READY_FOR_BO: 'Prêt pour le back-office',
  TRANSMITTED_BO: 'Transmis au back-office', NOT_STARTED: 'Non commencé', PENDING_REVIEW: 'En contrôle',
  VALIDATED: 'Validé', REJECTED: 'Refusé', PENDING_APPROVAL: 'Validation attendue', APPROVED: 'Approuvé',
  QUEUED_FOR_PROVISIONING: 'Création programmée', PROVISIONING: 'Création en cours', PROVISIONED: 'Créé',
  PROVISIONING_FAILED: 'Création à reprendre', ACCEPTED: 'Accepté', PENDING: 'À contrôler',
  LEGAL_EXISTENCE: 'Existence légale', REPRESENTATIVE_IDENTITY: 'Identité du représentant',
  BANK_ACCOUNT_PROOF: 'Justificatif bancaire', TAX_REGISTRATION: 'Immatriculation fiscale', ADDRESS_PROOF: 'Justificatif d’adresse',
  SIGNED_TPE_CONTRACT: 'Contrat TPE signé', SIGNED_ECOMMERCE_CONTRACT: 'Contrat e-commerce signé', SIGNED_CRC: 'CRC signé',
};
export function mobileLabel(value: string | null | undefined): string {
  return value ? labels[value] || 'Statut à consulter dans le dossier' : 'Non renseigné';
}
export function statusTone(value: string | null | undefined): string {
  if (['VALIDATED', 'ACCEPTED', 'APPROVED', 'PROVISIONED', 'READY_FOR_BO'].includes(value || '')) return 'success';
  if (['REJECTED', 'PROVISIONING_FAILED', 'ASSIGNMENT_EXCEPTION'].includes(value || '')) return 'danger';
  if (['COMPLEMENTS_REQUIRED', 'AWAITING_CONFIRMATION', 'REEXAMINATION'].includes(value || '')) return 'warning';
  return 'info';
}
export function latestDocuments(documents: MerchantDocument[]): MerchantDocument[] {
  const latest = new Map<string, MerchantDocument>();
  for (const document of documents) {
    const previous = latest.get(document.type);
    if (!previous || document.version > previous.version) latest.set(document.type, document);
  }
  return [...latest.values()];
}
export function requiredDocumentsAccepted(documents: MerchantDocument[]): boolean {
  const latest = latestDocuments(documents);
  return ['LEGAL_EXISTENCE', 'REPRESENTATIVE_IDENTITY', 'BANK_ACCOUNT_PROOF']
    .every(type => latest.some(document => document.type === type && document.reviewStatus === 'ACCEPTED'));
}

export interface MobileMenuSection {
  id: string;
  label: string;
  items: { label: string; route: string; exact: boolean }[];
}

/** Presentation only: server authorizations and route guards remain authoritative. */
export function mobileMenuSections(user: CurrentUser | null): MobileMenuSection[] {
  if (!user) return [];
  const sections: MobileMenuSection[] = [];
  if (merchantAccess(user)) sections.push({ id: 'merchant', label: 'Mon commerce', items: [
    { label: 'Mon dossier', route: '/merchant/dossier', exact: false },
    { label: 'Mes rattachements', route: '/merchant/associations', exact: true },
  ] });
  if (commercialAccess(user)) sections.push({ id: 'commercial', label: 'Activité commerciale', items: [
    { label: 'Nouveau prospect', route: '/commercial/prospect', exact: true },
    { label: 'Dossiers commerciaux', route: '/commercial/dossiers', exact: false },
  ] });
  if (reviewAccess(user)) sections.push({ id: 'review', label: 'Back-office', items: [
    { label: 'Contrôles KYC', route: '/backoffice/dossiers', exact: false },
  ] });
  // Do not infer this sensitive permission from ADMIN or another KYC permission.
  if (user.role === 'BACK_OFFICE' && user.permissions.includes('ONBOARDING_PERSONAL_EMAIL_SUPPORT')) {
    sections.find(section => section.id === 'review')!.items.push({
      label: 'Assistance e-mail', route: '/backoffice/email-assistance', exact: true,
    });
  }
  sections.push({ id: 'account', label: 'Mon compte', items: [
    { label: 'Mon espace', route: '/account', exact: true },
    ...(user.role === 'MERCHANT' ? [{ label: 'Mon adresse personnelle', route: '/merchant/personal-account', exact: true }] : []),
  ] });
  return sections;
}
