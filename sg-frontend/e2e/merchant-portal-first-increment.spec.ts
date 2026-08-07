import { expect, test } from '@playwright/test';

function token(role: string, permissions: string[] = []): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: `${role.toLowerCase()}.e2e`, role, permissions,
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.test`;
}

async function authenticated(page: import('@playwright/test').Page, role: string, permissions: string[] = []) {
  await page.addInitScript(value => localStorage.setItem('sg-token', value), token(role, permissions));
  await page.route('**/api/me/navigation', route => route.fulfill({ json: { legacyFallback: false, modules: [] } }));
}

test('active un compte commercant depuis le lien public', async ({ page }) => {
  await page.route('**/auth/merchant-invitations/activate', async route => {
    const body = route.request().postDataJSON();
    expect(body.token).toBe('activation-token-once');
    expect(body.password).toBe('StrongPassword!42');
    await route.fulfill({ json: { userId: 42, status: 'ACTIVE' } });
  });
  await page.goto('/activation?token=activation-token-once');
  await page.getByLabel('Nouveau mot de passe').fill('StrongPassword!42');
  await page.getByLabel('Confirmation').fill('StrongPassword!42');
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByText('Votre compte est actif')).toBeVisible();
});

test('le commercial cree le prospect via API reelle sans mot de passe', async ({ page }) => {
  await authenticated(page, 'COMMERCIAL', ['ONBOARDING_PROSPECT_CREATE']);
  await page.route('**/api/merchant-onboarding/v1/prospects', async route => {
    const body = route.request().postDataJSON();
    expect(body).toEqual({ login: 'merchant.demo', email: 'merchant.demo@example.test', acquirerId: 'ACQTEST' });
    await route.fulfill({ json: {
      account: { id: 'account-1', login: body.login, email: body.email, status: 'INVITATION_PENDING', identityUserId: '42' },
      dossier: { id: 'case-1', reference: 'ONB-CASE0001', accountId: 'account-1', acquirerId: body.acquirerId,
        legalName: null, tradingName: null, registrationNumber: null, country: null, mcc: null,
        acceptanceChannel: null, terminalCount: 0, status: 'DRAFT', kycStatus: 'NOT_STARTED',
        kycSubmittedBy: null, kycReviewedBy: null, complementReason: null, submittedBy: null,
        checkedBy: null, rejectionReason: null, acquiringMerchantId: null, merchantAcceptorId: null,
        createdAt: '2026-08-07T16:00:00Z' },
      identityInvitation: { userId: 42, invitationId: 'invitation-1', activationToken: 'activation-token-once', expiresAt: '2026-08-09T16:00:00Z' },
    } });
  });
  await page.goto('/commercial/prospects/new');
  await page.getByLabel('Identifiant du commercant').fill('merchant.demo');
  await page.getByLabel('Adresse email').fill('merchant.demo@example.test');
  await page.getByLabel('Code acquereur').fill('ACQTEST');
  await page.getByRole('button', { name: 'Creer et inviter' }).click();
  await expect(page.getByText('ONB-CASE0001')).toBeVisible();
  await expect(page.locator('input[readonly]')).toHaveValue(/activation-token-once/);
  await expect(page.getByText('SwitchLab')).toHaveCount(0);
});

test('le tableau de bord commercant reste ferme si API me dossier absente', async ({ page }) => {
  await authenticated(page, 'MERCHANT');
  await page.goto('/merchant/dashboard');
  await expect(page.getByRole('heading', { name: /Bienvenue/ })).toBeVisible();
  await expect(page.getByText('API de liste fermee - aucune donnee fictive')).toBeVisible();
  await expect(page.getByText('Planifiee au Lot 2')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Nouveau prospect' })).toHaveCount(0);
});
