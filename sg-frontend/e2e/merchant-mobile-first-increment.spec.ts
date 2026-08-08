import { expect, test } from '@playwright/test';

test.use({ viewport: { width: 390, height: 844 } });

function jwt(role: string, permissions: string[] = []): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    sub: role === 'COMMERCIAL' ? 'commercial.mobile' : 'merchant.mobile',
    role,
    permissions,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
  })}.signature`;
}

async function login(page: import('@playwright/test').Page, role: string): Promise<void> {
  await page.route('http://localhost:8080/auth/login', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ token: jwt(role), login: 'mobile', role, expiresIn: 3600 }),
  }));
  await page.goto('/login');
  await page.locator('ion-input[formcontrolname="login"] input').fill('mobile');
  await page.locator('ion-input[formcontrolname="password"] input').fill('StrongPassword!1');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/home$/);
}

test('active le compte commercant depuis le deep link mobile', async ({ page }) => {
  await page.route('http://localhost:8080/auth/merchant-invitations/activate', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ userId: 42, status: 'ACTIVE' }),
  }));
  await page.goto('/activation?token=mobile-token');
  await page.locator('ion-input[formcontrolname="password"] input').fill('StrongPassword!1');
  await page.locator('ion-input[formcontrolname="confirmation"] input').fill('StrongPassword!1');
  await page.getByRole('button', { name: 'Activer mon compte' }).click();
  await expect(page.getByText('Compte active. Vous pouvez vous connecter.')).toBeVisible();
});

test('le Commercial cree le prospect dans les ecrans mobiles', async ({ page }) => {
  await login(page, 'COMMERCIAL');
  await expect(page.getByText('Nouveau prospect')).toBeVisible();
  await page.getByRole('button', { name: 'Creer le prospect' }).click();
  await expect(page).toHaveURL(/\/commercial\/prospect$/);
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/prospects', route => route.fulfill({
    status: 201,
    contentType: 'application/json',
    body: JSON.stringify({
      account: { id: 'account-1', login: 'merchant.one', email: 'merchant@example.test', status: 'INVITATION_PENDING', identityUserId: null },
      dossier: { id: 'dossier-1', reference: 'ONB-MOBILE-001', status: 'DRAFT' },
      identityInvitation: { userId: 99, invitationId: 'inv-1', activationToken: 'once-only', expiresAt: '2026-08-09T20:00:00Z' },
    }),
  }));
  await page.locator('ion-input[formcontrolname="login"] input:visible').fill('merchant.one');
  await page.locator('ion-input[formcontrolname="email"] input:visible').fill('merchant@example.test');
  await page.locator('ion-input[formcontrolname="acquirerId"] input:visible').fill('ACQ-001');
  await page.getByRole('button', { name: 'Creer et inviter' }).click();
  await expect(page.getByText(/Dossier ONB-MOBILE-001 cree/)).toBeVisible();
  await expect(page.locator('ion-input[readonly] input')).toHaveValue(/activation\?token=once-only/);
});

test('le Commercant reprend et modifie le meme dossier depuis le mobile', async ({ page }) => {
  await login(page, 'MERCHANT');
  const dossier = {
      id: 'dossier-mobile', reference: 'ONB-MOBILE-002', accountId: 'account-2', acquirerId: 'ACQ-001',
      legalName: 'Commerce Mobile', tradingName: 'Mobile Shop', registrationNumber: 'RC123', country: 'MA',
      mcc: '5411', settlementAccountReference: 'ACC-MOBILE', settlementCurrency: '504', productId: '5480f18c-14a4-4e87-8fe2-13782efc55c9',
      acceptanceChannel: 'TPE', outletCode: 'MOB-01', outletName: 'Mobile', outletAddress: 'Rabat', terminalCount: 2,
      status: 'DRAFT', kycStatus: 'NOT_STARTED', kycSubmittedBy: null, kycReviewedBy: null, complementReason: null, submittedBy: null, checkedBy: null,
      rejectionReason: null, acquiringMerchantId: null, merchantAcceptorId: null, createdAt: '2026-08-07T20:00:00Z',
  };
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/dossiers/mine', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(dossier),
  }));
  await page.route('http://localhost:8570/api/merchant-onboarding/v1/dossiers/dossier-mobile/documents', route => route.fulfill({ json: [] }));
  await page.getByRole('button', { name: 'Ouvrir mon dossier' }).click();
  await expect(page.getByText('ONB-MOBILE-002')).toBeVisible();
  await expect(page.getByText('Commerce Mobile')).toBeVisible();
  await expect(page.getByText('KYC NOT_STARTED')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Enregistrer' })).toBeVisible();
});
