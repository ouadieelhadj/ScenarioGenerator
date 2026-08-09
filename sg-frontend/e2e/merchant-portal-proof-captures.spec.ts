import { expect, test, type Page } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

const output = process.env['SG_GUIDE_SCREENSHOT_DIR']
  ?? path.resolve(process.cwd(), '..', 'tests', 'merchant-onboarding', 'evidence', 'three-channels', 'screenshots');
const summaryPath = path.resolve(process.cwd(), '..', 'tests', 'merchant-onboarding', 'evidence', 'three-channels', 'summary.json');
const summary = JSON.parse(readFileSync(summaryPath, 'utf8').replace(/^\uFEFF/, '')) as Array<{
  channel: string; dossierReference: string; mid: string; tids: string[];
}>;
const result = (channel: string) => summary.find(item => item.channel === channel)!;

function token(role: string, permissions: string[] = []): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: `${role.toLowerCase()}.proof`, role, permissions,
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.proof`;
}

async function authenticated(page: Page, role: string, permissions: string[] = []) {
  await page.addInitScript(value => localStorage.setItem('sg-token', value), token(role, permissions));
  await page.route('**/api/me/navigation', route => route.fulfill({ json: { legacyFallback: false, modules: [] } }));
}

async function screenshot(page: Page, name: string) {
  mkdirSync(output, { recursive: true });
  await page.screenshot({ path: path.join(output, name), fullPage: true });
}

test('preuve 01 - activation Web', async ({ page }) => {
  await page.route('**/auth/merchant-invitations/activate', route => route.fulfill({ json: { userId: 42, status: 'ACTIVE' } }));
  await page.goto('/activation?token=proof-token');
  await page.getByLabel('Nouveau mot de passe').fill('StrongPassword!42');
  await page.getByLabel('Confirmation').fill('StrongPassword!42');
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByText('Votre compte est actif')).toBeVisible();
  await screenshot(page, '01-web-activation-reussie.png');
});

test('preuve 02 - parcours Commercial Web', async ({ page }) => {
  const proof = result('commercial-web');
  await authenticated(page, 'COMMERCIAL', ['ONBOARDING_PROSPECT_CREATE']);
  await page.route('**/api/merchant-onboarding/v1/prospects', route => route.fulfill({ json: {
    account: { id: 'account-commercial', login: 'merchant.proof', email: 'merchant.proof@example.test', status: 'INVITATION_PENDING', identityUserId: '42' },
    dossier: { id: 'case-commercial', reference: proof.dossierReference, status: 'DRAFT' },
    identityInvitation: { userId: 42, invitationId: 'inv-proof', activationToken: 'masked-proof-token', expiresAt: '2026-08-10T12:00:00Z' },
  } }));
  await page.goto('/commercial/prospects/new');
  await page.getByLabel('Identifiant du commercant').fill('merchant.proof');
  await page.getByLabel('Adresse email').fill('merchant.proof@example.test');
  await page.getByLabel('Code acquereur').fill('ACQTEST');
  await page.getByRole('button', { name: 'Creer et inviter' }).click();
  await expect(page.getByText(proof.dossierReference)).toBeVisible();
  await screenshot(page, '02-web-commercial-prospect-cree.png');
});

test('preuve 03 - dossier Commercant Web', async ({ page }) => {
  const proof = result('merchant-web');
  await authenticated(page, 'MERCHANT');
  const dossier = {
    id: 'case-merchant', reference: proof.dossierReference, accountId: 'account-merchant', acquirerId: 'ACQTEST',
    legalName: 'Commerce preuve Web', tradingName: 'Boutique preuve', registrationNumber: 'RC-PROOF-WEB', country: 'MA', mcc: '5411',
    settlementAccountReference: 'ACC-PROOF', settlementCurrency: '504', productId: '5480f18c-14a4-4e87-8fe2-13782efc55c9',
    acceptanceChannel: 'TPE', outletCode: 'WEB-01', outletName: 'Point de vente principal', outletAddress: 'Casablanca', terminalCount: 1,
    status: 'DRAFT', kycStatus: 'NOT_STARTED', kycSubmittedBy: null, kycReviewedBy: null, complementReason: null,
    submittedBy: null, checkedBy: null, rejectionReason: null, acquiringMerchantId: null, merchantAcceptorId: null, createdAt: '2026-08-09T10:00:00Z',
  };
  await page.route('**/api/merchant-onboarding/v1/dossiers/mine', route => route.fulfill({ json: dossier }));
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-merchant/documents', route => route.fulfill({ json: [] }));
  await page.goto('/merchant/dossier');
  await expect(page.getByText(proof.dossierReference)).toBeVisible();
  await screenshot(page, '03-web-commercant-dossier.png');
});

test('preuve 04 - revue KYC Back-office', async ({ page }) => {
  const proof = result('merchant-web');
  await authenticated(page, 'BACK_OFFICE', ['ONBOARDING_KYC_REVIEW']);
  const dossier = { id: 'case-review', reference: proof.dossierReference, legalName: 'Commerce preuve Web', registrationNumber: 'RC-PROOF-WEB', mcc: '5411', kycStatus: 'PENDING_REVIEW' };
  const documents = ['LEGAL_EXISTENCE', 'REPRESENTATIVE_IDENTITY', 'BANK_ACCOUNT_PROOF'].map((type, index) => ({
    id: `doc-${index}`, caseId: 'case-review', type, version: 1, contentType: 'application/pdf', contentLength: 2048 + index,
    sha256: 'a'.repeat(64), reviewStatus: 'PENDING', uploadedBy: 'merchant.proof', reviewedBy: null, rejectionReason: null,
  }));
  await page.route('**/api/merchant-onboarding/v1/review/dossiers', route => route.fulfill({ json: [dossier] }));
  await page.route('**/api/merchant-onboarding/v1/review/dossiers/case-review/documents', route => route.fulfill({ json: documents }));
  await page.route('**/api/merchant-onboarding/v1/documents/*/review', async route => {
    const id = route.request().url().split('/').at(-2)!;
    await route.fulfill({ json: { ...documents.find(item => item.id === id), reviewStatus: 'ACCEPTED', reviewedBy: 'backoffice.reviewer' } });
  });
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-review/kyc/validate', route => route.fulfill({ json: { ...dossier, kycStatus: 'VALIDATED' } }));
  await page.goto('/backoffice/onboarding');
  await page.getByRole('button', { name: new RegExp(proof.dossierReference) }).click();
  await screenshot(page, '04-web-backoffice-pieces-kyc.png');
  for (const button of await page.getByRole('button', { name: 'Accepter' }).all()) await button.click();
  await page.getByRole('button', { name: 'Valider le KYC' }).click();
  await expect(page.getByText('KYC valide. Le Maker peut maintenant soumettre le dossier.')).toBeVisible();
  await screenshot(page, '05-web-backoffice-kyc-valide.png');
});

test('preuve 05 - Checker, batch et resultat MID TID', async ({ page }) => {
  const proof = result('commercial-web');
  await authenticated(page, 'CHECKER', ['ONBOARDING_APPROVE', 'ONBOARDING_PROVISION']);
  await page.route('**/api/workflow/approvals/mine', route => route.fulfill({ json: [{
    id: 191, caseId: 'case-checker', moduleCode: 'MERCHANT_ONBOARDING', operationType: 'MERCHANT_AFFILIATION',
    objectReference: proof.dossierReference, status: 'PENDING', createdBy: 'commercial.proof', createdAt: '2026-08-09T10:00:00Z',
  }] }));
  await page.route('**/api/workflow/approvals/191/approve', route => route.fulfill({ json: { id: 'case-checker', status: 'APPROVED' } }));
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-checker/provision?mode=BATCH', route => route.fulfill({ json: {
    dossier: { id: 'case-checker', status: 'QUEUED_FOR_PROVISIONING' }, jobId: 'job-proof', jobStatus: 'PENDING', result: null, error: null,
  } }));
  await page.route('**/api/merchant-onboarding/v1/batches/run?limit=100&retryFailed=false', route => route.fulfill({ json: [{
    dossier: { id: 'case-checker', reference: proof.dossierReference, status: 'PROVISIONED' }, jobId: 'job-proof', jobStatus: 'SUCCEEDED',
    result: { merchantAcceptorId: proof.mid, terminals: proof.tids.map(terminalId => ({ terminalId })) }, error: null,
  }] }));
  await page.goto('/workflow/my-approvals');
  await screenshot(page, '06-web-checker-demande.png');
  await page.getByRole('button', { name: 'Approuver' }).click();
  await page.getByRole('button', { name: 'Mettre en batch' }).click();
  await page.getByRole('button', { name: 'Executer le batch valide' }).click();
  await expect(page.getByText(new RegExp(proof.mid))).toBeVisible();
  await screenshot(page, '07-web-batch-mid-tid.png');
});
