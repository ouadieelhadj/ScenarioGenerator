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

test('le commercant reprend et enregistre son dossier depuis le portail', async ({ page }) => {
  await authenticated(page, 'MERCHANT');
  const dossier = { id: 'case-mine', reference: 'ONB-WEB-001', accountId: 'account-2', acquirerId: 'ACQTEST',
    legalName: 'Commerce Web', tradingName: 'Boutique Web', registrationNumber: 'RC-WEB', country: 'MA', mcc: '5411',
    settlementAccountReference: 'ACC-WEB', settlementCurrency: '504', productId: '5480f18c-14a4-4e87-8fe2-13782efc55c9',
    acceptanceChannel: 'TPE', outletCode: 'WEB-01', outletName: 'Rabat', outletAddress: 'Rabat', terminalCount: 1,
    status: 'DRAFT', kycStatus: 'NOT_STARTED', kycSubmittedBy: null, kycReviewedBy: null, complementReason: null,
    submittedBy: null, checkedBy: null, rejectionReason: null, acquiringMerchantId: null, merchantAcceptorId: null, createdAt: '2026-08-08T20:00:00Z' };
  await page.route('**/api/merchant-onboarding/v1/dossiers/mine', route => route.fulfill({ json: dossier }));
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-mine/documents', route => route.fulfill({ json: [] }));
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-mine', async route => {
    expect(route.request().method()).toBe('PUT');
    expect(route.request().postDataJSON().mcc).toBe('5999');
    await route.fulfill({ json: { ...dossier, mcc: '5999' } });
  });
  await page.goto('/merchant/dashboard');
  await expect(page.getByRole('heading', { name: /Bienvenue/ })).toBeVisible();
  await page.getByRole('link', { name: 'Continuer mon onboarding' }).click();
  await page.getByLabel('MCC').fill('5999');
  await page.getByRole('button', { name: 'Enregistrer le dossier' }).click();
  await expect(page.getByText('Dossier enregistre.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Nouveau prospect' })).toHaveCount(0);
});

test('le Back-office accepte les trois pieces et valide le KYC', async ({ page }) => {
  await authenticated(page, 'BACK_OFFICE', ['ONBOARDING_KYC_REVIEW']);
  const dossier = { id:'case-review', reference:'ONB-REVIEW-1', legalName:'Commerce KYC', registrationNumber:'RC-KYC', mcc:'5411', kycStatus:'PENDING_REVIEW' };
  const documents = ['LEGAL_EXISTENCE','REPRESENTATIVE_IDENTITY','BANK_ACCOUNT_PROOF'].map((type,index) => ({ id:`doc-${index}`, caseId:'case-review', type, version:1, contentType:'application/pdf', contentLength:128, sha256:'a'.repeat(64), reviewStatus:'PENDING', uploadedBy:'merchant.kyc', reviewedBy:null, rejectionReason:null }));
  await page.route('**/api/merchant-onboarding/v1/review/dossiers', route => route.fulfill({ json:[dossier] }));
  await page.route('**/api/merchant-onboarding/v1/review/dossiers/case-review/documents', route => route.fulfill({ json:documents }));
  await page.route('**/api/merchant-onboarding/v1/documents/*/review', async route => {
    const id=route.request().url().split('/').at(-2)!; const document=documents.find(item=>item.id===id)!;
    await route.fulfill({ json:{...document,reviewStatus:'ACCEPTED',reviewedBy:'backoffice.e2e'} });
  });
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-review/kyc/validate', route => route.fulfill({ json:{...dossier,kycStatus:'VALIDATED'} }));
  await page.goto('/backoffice/onboarding');
  await page.getByRole('button', { name:/ONB-REVIEW-1/ }).click();
  for (const button of await page.getByRole('button', { name:'Accepter' }).all()) await button.click();
  await page.getByRole('button', { name:'Valider le KYC' }).click();
  await expect(page.getByText('KYC valide. Le Maker peut maintenant soumettre le dossier.')).toBeVisible();
});

test('le Checker approuve puis place le JSON en batch Acquiring', async ({ page }) => {
  await authenticated(page, 'CHECKER', ['ONBOARDING_APPROVE','ONBOARDING_PROVISION']);
  let provisioned=false;
  await page.route('**/api/workflow/approvals/mine', route => route.fulfill({ json:[{ id:91,caseId:'case-checker',moduleCode:'MERCHANT_ONBOARDING',operationType:'MERCHANT_AFFILIATION',objectReference:'ONB-CHECK-1',status:'PENDING',createdBy:'merchant.e2e',createdAt:'2026-08-08T20:00:00Z'}] }));
  await page.route('**/api/workflow/approvals/91/approve', route => route.fulfill({ json:{ id:'case-checker',status:'APPROVED' } }));
  await page.route('**/api/merchant-onboarding/v1/dossiers/case-checker/provision?mode=BATCH', route => { provisioned=true; return route.fulfill({ json:{ dossier:{id:'case-checker',status:'QUEUED_FOR_PROVISIONING'},jobId:'job-1',jobStatus:'PENDING',result:null,error:null } }); });
  await page.goto('/workflow/my-approvals');
  await page.getByRole('button', { name:'Approuver' }).click();
  await page.getByRole('button', { name:'Mettre en batch' }).click();
  expect(provisioned).toBeTruthy();
});
