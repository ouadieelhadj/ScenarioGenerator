import { expect, Page, Route, test } from '@playwright/test';

type MockState = {
  mutationRequests: number;
  crossProductRequests: string[];
};

const now = '2026-08-07T13:10:00+01:00';

function token(): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: 'switch-ui-proof', role: 'TESTER',
    permissions: ['DEPLOYMENT_VIEW', 'DEPLOYMENT_PREPARE', 'USER_MANAGE', 'ROLE_MANAGE'],
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.contract`;
}

async function fulfillJson(route: Route, json: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(json) });
}

function service(code: string, label: string, limitation: string) {
  return { code, label, configured: false, status: 'UNKNOWN', capabilities: [], limitation };
}

function overview(domain: string, services: ReturnType<typeof service>[]) {
  return {
    schemaVersion: '1.0', domain, overallStatus: 'UNKNOWN', services, features: [],
    checkedAt: now, correlationId: `corr-switch-${domain}`,
  };
}

async function installContract(page: Page): Promise<MockState> {
  const state: MockState = { mutationRequests: 0, crossProductRequests: [] };
  await page.addInitScript(value => {
    localStorage.clear();
    localStorage.setItem('sg-token', value);
    localStorage.setItem('sg-lang', 'fr');
  }, token());

  page.on('request', request => {
    const url = request.url();
    if (/localhost:(8090|8532)|\/api\/switchlab\//i.test(url)) state.crossProductRequests.push(url);
  });

  await page.route('http://localhost:8091/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) state.mutationRequests++;

    if (path === '/api/me/navigation') return fulfillJson(route, {
      legacyFallback: false,
      modules: [{ code: 'MEMBER_SWITCH', labelKey: 'modules.switch', icon: 'pi pi-building', children: [] }],
    });
    if (path === '/api/switch/v1/interfaces/capabilities') return fulfillJson(route, {
      registryAvailable: false, makerCheckerAvailable: false, activationAvailable: false,
      reason: 'Registre membre non configuré',
    });
    if (path === '/api/switch/v1/interfaces') return fulfillJson(route, []);
    if (path === '/api/switch/v1/acquiring/overview') return fulfillJson(route, {
      ...overview('acquiring', [
        service('SG_ACQUIRING', 'Acquisition membre', 'URL membre non configurée'),
        service('SG_WAY_POS_SERVER', 'WayPOS Server membre', 'URL membre non configurée'),
      ]),
      features: [{
        code: 'ACQUIRING_READ', label: 'Consultation acquisition', status: 'BLOCKED',
        backendEndpointAvailable: false, consultationAvailable: false, actionAvailable: false,
        makerCheckerRequired: true, limitation: 'API membre non configurée',
      }],
    });
    if (path === '/api/switch/v1/domains/networks') return fulfillJson(route, overview('networks', [
      service('SG_MC_DMAS_MEMBER', 'Mastercard DMAS membre', 'URL membre non configurée'),
      service('SG_MC_SMS_ACQUIRER', 'Mastercard SMS acquéreur', 'URL membre non configurée'),
      service('SG_SWAM_ACQUIRER', 'SWAM acquéreur', 'URL membre non configurée'),
      service('SG_VISA_ONLINE_MEMBER', 'Visa Online membre', 'URL membre non configurée'),
    ]));
    if (path === '/api/switch/v1/domains/clearing') return fulfillJson(route, overview('clearing', [
      service('SG_DMCS_ACQUIRER', 'Mastercard Clearing acquéreur', 'URL membre non configurée'),
      service('SG_SWAM_LIS_MEMBER', 'SWAM LIS membre', 'URL membre non configurée'),
      service('SG_VISA_BASE2_MEMBER', 'Visa Base II membre', 'URL membre non configurée'),
    ]));

    if (path === '/api/admin/deployments/catalog') return fulfillJson(route, {
      modules: [], targetOperatingSystems: ['WINDOWS', 'LINUX'],
      compatibleShells: { WINDOWS: ['POWERSHELL', 'CMD_WINDOWS', 'GIT_BASH'], LINUX: ['BASH_LINUX'] },
      databaseTypes: ['NONE', 'POSTGRESQL', 'ORACLE'],
    });
    if (path === '/api/admin/deployments/clients') return fulfillJson(route, [{
      id: 1, code: 'BANK-TEST', legalName: 'Banque de recette', countryCode: 'MA', status: 'ACTIVE',
    }]);
    if (path === '/api/admin/deployments/environments' && method === 'GET') return fulfillJson(route, []);
    if (path === '/api/admin/deployments/licenses') return fulfillJson(route, []);
    if (path === '/api/admin/deployments/executions') return fulfillJson(route, []);
    return fulfillJson(route, { message: `Mock absent: ${method} ${path}` }, 404);
  });
  return state;
}

let state: MockState;
test.beforeEach(async ({ page }) => { state = await installContract(page); });

test('[SW-COM-003] change FR/EN/ES sans bascule produit', async ({ page }) => {
  await page.goto('/product/interfaces');
  const select = page.locator('.lang-picker select');
  for (const lang of ['en', 'es', 'fr']) {
    await select.selectOption(lang);
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
  }
  await expect(page.getByText('FuturPayment Switch', { exact: true })).toBeVisible();
});

test('[SW-COM-004] applique le thème sans perdre la session', async ({ page }) => {
  await page.goto('/product/interfaces');
  await page.locator('.theme-picker select').selectOption('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('sg-token'))).not.toBeNull();
});

test('[SW-COM-005] applique une couleur primaire lisible', async ({ page }) => {
  await page.goto('/product/interfaces');
  await page.locator('.theme-picker input[type="color"]').evaluate((element: HTMLInputElement) => {
    element.value = '#2563eb';
    element.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--sg-color-primary').trim())).toBe('#2563eb');
});

test('[SW-COM-006] déconnecte et supprime la session locale', async ({ page }) => {
  await page.goto('/product/interfaces');
  await page.locator('button.logout').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('sg-token'))).toBeNull();
});

test('[SW-INT-003] affiche une liste vide sans fixture', async ({ page }) => {
  await page.goto('/product/interfaces');
  await expect(page.getByText(/Aucune interface réelle/)).toBeVisible();
  await expect(page.locator('.list article')).toHaveCount(0);
});

test('[SW-INT-004] conserve le formulaire local sans soumission', async ({ page }) => {
  await page.goto('/product/interfaces');
  await page.locator('input[name="code"]').fill('IFACE-UI');
  await page.locator('input[name="name"]').fill('Interface de recette IHM');
  await page.locator('input[name="host"]').fill('member.invalid');
  await expect(page.getByRole('button', { name: /Soumettre au Maker/ })).toBeDisabled();
  await expect(page.locator('input[name="code"]')).toHaveValue('IFACE-UI');
  expect(state.mutationRequests).toBe(0);
});

test('[SW-INT-005] conserve vault et hsm sans résolution navigateur', async ({ page }) => {
  await page.goto('/product/interfaces');
  await page.locator('input[name="certificate"]').fill('vault://certificates/switch-ui-proof');
  await page.locator('input[name="key"]').fill('hsm://keys/switch-ui-proof');
  await expect(page.locator('input[name="certificate"]')).toHaveValue('vault://certificates/switch-ui-proof');
  await expect(page.locator('input[name="key"]')).toHaveValue('hsm://keys/switch-ui-proof');
  expect(state.mutationRequests).toBe(0);
});

test('[SW-ACQ-002] affiche UNKNOWN pour une URL membre non configurée', async ({ page }) => {
  await page.goto('/product/acquiring');
  await expect(page.locator('.status-unknown').first()).toHaveText('UNKNOWN');
  await expect(page.getByText('URL membre non configurée').first()).toBeVisible();
  await expect(page.locator('.status-up')).toHaveCount(0);
});

test('[SW-NET-002] n’appelle aucun module issuer simulé', async ({ page }) => {
  await page.goto('/product/networks');
  await expect(page.getByText('SG_MC_DMAS_MEMBER')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('sg-mc-sms-issuer');
  await expect(page.locator('body')).not.toContainText('sg-swam-issuer');
  await expect(page.locator('body')).not.toContainText('simulateur appelé');
  expect(state.crossProductRequests).toEqual([]);
});

test('[SW-CLR-002] affiche Mastercard Clearing côté acquéreur', async ({ page }) => {
  await page.goto('/product/clearing');
  await expect(page.getByText('SG_DMCS_ACQUIRER')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('sg-dmcs-issuer');
  expect(state.crossProductRequests).toEqual([]);
});

test('[SW-DEP-003] propose les shells compatibles avec WINDOWS et LINUX', async ({ page }) => {
  await page.goto('/administration/deployments/environments');
  await page.locator('.section-head button.btn-primary').click();
  const os = page.locator('select[name="targetOs"]');
  const shell = page.getByTestId('shell-select');
  await os.selectOption('WINDOWS');
  await expect(shell.locator('option')).toHaveText(['POWERSHELL', 'CMD_WINDOWS', 'GIT_BASH']);
  await os.selectOption('LINUX');
  await expect(shell.locator('option')).toHaveText(['BASH_LINUX']);
  await expect(shell).toHaveValue('BASH_LINUX');
});
