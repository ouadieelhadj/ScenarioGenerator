import { expect, test, type Page } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

test.use({ viewport: { width: 390, height: 844 } });
const output = process.env['SG_GUIDE_SCREENSHOT_DIR']
  ?? path.resolve(process.cwd(), '..', 'tests', 'merchant-onboarding', 'evidence', 'three-channels', 'screenshots');
const summary = JSON.parse(readFileSync(path.resolve(process.cwd(), '..', 'tests', 'merchant-onboarding', 'evidence', 'three-channels', 'summary.json'), 'utf8').replace(/^\uFEFF/, ''));
const proof = summary.find((item: { channel: string }) => item.channel === 'mobile');

function jwt(role: string): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: `mobile.${role.toLowerCase()}`, role, permissions: role === 'COMMERCIAL' ? ['ONBOARDING_PROSPECT_CREATE'] : [], iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 })}.proof`;
}

async function login(page: Page, role: string) {
  await page.route('http://localhost:8080/auth/login', route => route.fulfill({ json: { token: jwt(role), login: 'mobile.proof', role, expiresIn: 3600 } }));
  await page.goto('/login');
  await page.locator('ion-input[formcontrolname="login"] input').fill('mobile.proof');
  await page.locator('ion-input[formcontrolname="password"] input').fill('MaskedPassword!1');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/home$/);
}

async function screenshot(page: Page, name: string) {
  mkdirSync(output, { recursive: true });
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(output, name), fullPage: true });
}

test('preuve 08 - activation Mobile', async ({ page }) => {
  await page.route('http://localhost:8080/auth/merchant-invitations/activate', route => route.fulfill({ json: { userId: 42, status: 'ACTIVE' } }));
  await page.goto('/activation?token=proof-mobile-token');
  await page.locator('ion-input[formcontrolname="password"] input').fill('MaskedPassword!1');
  await page.locator('ion-input[formcontrolname="confirmation"] input').fill('MaskedPassword!1');
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByText('Compte active. Vous pouvez vous connecter.')).toBeVisible();
  await screenshot(page, '08-mobile-activation-reussie.png');
});

test('preuve 09 - Commercial Mobile', async ({ page }) => {
  await login(page, 'COMMERCIAL');
  await page.getByRole('button', { name: 'Creer le prospect' }).click();
  await expect(page).toHaveURL(/\/commercial\/prospect$/);
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/prospects', route => route.fulfill({ json: {
    account: { id: 'mobile-account', login: 'merchant.mobile.proof', email: 'merchant.mobile@example.test', status: 'INVITATION_PENDING', identityUserId: null },
    dossier: { id: 'mobile-case', reference: proof.dossierReference, status: 'DRAFT' },
    identityInvitation: { userId: 99, invitationId: 'mobile-inv', activationToken: 'masked-mobile-token', expiresAt: '2026-08-10T12:00:00Z' },
  } }));
  await page.locator('ion-input[formcontrolname="login"] input:visible').fill('merchant.mobile.proof');
  await page.locator('ion-input[formcontrolname="email"] input:visible').fill('merchant.mobile@example.test');
  await page.locator('ion-input[formcontrolname="acquirerId"] input:visible').fill('ACQTEST');
  await page.getByRole('button', { name: 'Creer et inviter' }).click();
  await expect(page.getByText(new RegExp(proof.dossierReference))).toBeVisible();
  await screenshot(page, '09-mobile-commercial-prospect-cree.png');
});

test('preuve 10 - dossier Commercant Mobile', async ({ page }) => {
  await login(page, 'MERCHANT');
  const dossier = {
    id: 'mobile-case', reference: proof.dossierReference, accountId: 'mobile-account', acquirerId: 'ACQTEST', legalName: 'Commerce preuve Mobile',
    tradingName: 'Boutique Mobile', registrationNumber: 'RC-MOBILE', country: 'MA', mcc: '5411', settlementAccountReference: 'ACC-MOBILE',
    settlementCurrency: '504', productId: '5480f18c-14a4-4e87-8fe2-13782efc55c9', acceptanceChannel: 'TPE', outletCode: 'MOB-01',
    outletName: 'Point de vente mobile', outletAddress: 'Rabat', terminalCount: 3, status: 'PROVISIONED', kycStatus: 'VALIDATED',
    kycSubmittedBy: 'mobile.merchant', kycReviewedBy: 'backoffice.reviewer', complementReason: null, submittedBy: 'mobile.merchant',
    checkedBy: 'checker.validator', rejectionReason: null, acquiringMerchantId: 'merchant-proof', merchantAcceptorId: proof.mid, createdAt: '2026-08-09T10:00:00Z',
  };
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/dossiers/mine', route => route.fulfill({ json: dossier }));
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/dossiers/mobile-case/documents', route => route.fulfill({ json: [] }));
  await page.getByRole('button', { name: 'Ouvrir mon dossier' }).click();
  await expect(page.getByText(proof.dossierReference)).toBeVisible();
  await expect(page.getByText('KYC VALIDATED')).toBeVisible();
  await screenshot(page, '10-mobile-commercant-dossier-valide.png');
});
