import { expect, test } from '@playwright/test';

const navigation = {
  legacyFallback: false,
  modules: [
    {
      code: 'CORE_PORTAL', labelKey: 'menu.core', icon: 'pi pi-home', children: [],
    },
    {
      code: 'ACQUIRING', labelKey: 'modules.acquiring', icon: 'pi pi-shopping-cart',
      children: [{
        id: 10, type: 'MENU', code: 'ACQUIRING_OPERATIONS', labelKey: 'moduleMenus.operations',
        icon: 'pi pi-folder', context: {}, children: [
          { id: 11, type: 'SCREEN', code: 'ACQUIRING_OVERVIEW', labelKey: 'screens.overview', icon: 'pi pi-home', route: '/modules/ACQUIRING/overview', componentKey: 'MODULE_WORKSPACE', context: {}, children: [] },
          { id: 12, type: 'SCREEN', code: 'ACQUIRING_ECOMMERCE', labelKey: 'screens.ecommerceTransactions', icon: 'pi pi-globe', route: '/modules/ACQUIRING/ecommerce-transactions', componentKey: 'MODULE_WORKSPACE', context: {}, children: [] },
        ],
      }],
    },
    {
      code: 'VISA_ONLINE_MEMBER', labelKey: 'modules.visaOnline', icon: 'pi pi-bolt',
      children: [{
        id: 30, type: 'MENU', code: 'VISA_ONLINE_MEMBER_OPERATIONS', labelKey: 'moduleMenus.operations',
        icon: 'pi pi-folder', context: {}, children: [
          { id: 31, type: 'SCREEN', code: 'VISA_ONLINE_TRANSACTIONS', labelKey: 'screens.transactions', icon: 'pi pi-list', route: '/modules/VISA_ONLINE_MEMBER/transactions', componentKey: 'VISA_WORKSPACE', context: {}, children: [] },
        ],
      }],
    },
    {
      code: 'LAB_SIMULATORS', labelKey: 'modules.simulators', icon: 'pi pi-bolt',
      children: [{
        id: 20, type: 'MENU', code: 'LAB_SIMULATORS_CATALOG', labelKey: 'moduleMenus.simulators',
        icon: 'pi pi-bolt', context: {}, children: [
          { id: 21, type: 'SCREEN', code: 'LAB_POS_SIMULATOR', labelKey: 'screens.posSimulator', icon: 'pi pi-desktop', route: '/lab/LAB_SIMULATORS/pos-simulator', componentKey: 'MODULE_WORKSPACE', context: {}, children: [] },
          { id: 22, type: 'SCREEN', code: 'LAB_MERCHANT_LOCAL', labelKey: 'screens.merchantSiteLocal', icon: 'pi pi-shop', route: '/lab/LAB_SIMULATORS/merchant-site-local', componentKey: 'MODULE_WORKSPACE', context: {}, children: [] },
          { id: 23, type: 'SCREEN', code: 'LAB_MERCHANT_INTERNATIONAL', labelKey: 'screens.merchantSiteInternational', icon: 'pi pi-globe', route: '/lab/LAB_SIMULATORS/merchant-site-international', componentKey: 'MODULE_WORKSPACE', context: {}, children: [] },
          { id: 24, type: 'SCREEN', code: 'LAB_VISANET_NETWORK', labelKey: 'screens.visaNetSimulator', icon: 'pi pi-bolt', route: '/lab/LAB_SIMULATORS/visanet-network', componentKey: 'VISA_WORKSPACE', context: {}, children: [] },
        ],
      }],
    },
  ],
};

function token(): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: 'admin', role: 'ADMIN',
    permissions: ['USER_MANAGE', 'ROLE_MANAGE', 'CATALOG_MANAGE', 'CAMPAIGN_CREATE', 'TPS_RUN', 'EXECUTION_VIEW'],
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.test`;
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(value => localStorage.setItem('sg-token', value), token());
  await page.route('**/api/me/navigation', route => route.fulfill({ json: navigation }));
});

test('affiche le domaine Visa et range son simulateur dans le LAB', async ({ page }) => {
  await page.goto('/dashboard');
  await page.locator('[data-module-code="VISA_ONLINE_MEMBER"]').click();
  await page.getByRole('link', { name: 'Transactions' }).click();
  await expect(page).toHaveURL(/\/modules\/VISA_ONLINE_MEMBER\/transactions$/);
  await expect(page.getByText(/Sandbox fonctionnelle/)).toBeVisible();

  await page.locator('[data-module-code="LAB_SIMULATORS"]').click();
  await page.getByRole('link', { name: /VisaNet/ }).click();
  await expect(page).toHaveURL(/\/lab\/LAB_SIMULATORS\/visanet-network$/);
  await expect(page.getByText('VisaNet', { exact: true })).toBeVisible();
});

test('conserve le menu commun et isole les simulateurs', async ({ page }) => {
  await page.goto('/dashboard');

  await expect(page.getByRole('link', { name: 'Tableau de bord' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Utilisateurs' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Rôles' })).toBeVisible();
  await expect(page.locator('[data-module-code="ACQUIRING"]')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Transactions e-commerce' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Site marchand national' })).toHaveCount(0);

  await page.locator('[data-module-code="LAB_SIMULATORS"]').click();
  await expect(page.getByRole('link', { name: 'Site marchand national' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Site marchand international' })).toBeVisible();
  await page.getByRole('link', { name: 'Site marchand national' }).click();
  await expect(page).toHaveURL(/\/lab\/LAB_SIMULATORS\/merchant-site-local$/);
  await expect(page.getByText('Module enregistré dans le portail')).toBeVisible();
});

test('affiche les rôles réels retournés par l API', async ({ page }) => {
  await page.route('**/api/admin/roles', route => route.fulfill({ json: [{
    id: 1, code: 'ADMIN', label: 'Administrateur', description: 'Administration',
    permissions: [{ id: 1, code: 'ROLE_MANAGE', label: 'Gérer les rôles', category: 'ADMIN' }],
  }] }));
  await page.goto('/administration/roles');
  await expect(page.getByRole('heading', { name: 'Rôles' })).toBeVisible();
  await expect(page.getByText('ROLE_MANAGE')).toBeVisible();
});

test('ne fabrique pas de workflow lorsque l API Maker Checker est absente', async ({ page }) => {
  await page.route('**/api/workflow/approvals/mine', route => route.fulfill({ status: 404, json: { message: 'not implemented' } }));
  await page.goto('/workflow/my-approvals');
  await expect(page.getByText('Socle Maker/Checker prêt')).toBeVisible();
  await expect(page.getByText(/Aucune validation fictive/)).toBeVisible();
});
