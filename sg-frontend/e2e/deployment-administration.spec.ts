import { expect, test, type Page, type Route } from '@playwright/test';

const permissions = [
  'DEPLOYMENT_VIEW', 'DEPLOYMENT_PREPARE', 'DEPLOYMENT_APPROVE',
  'DEPLOYMENT_EXECUTE', 'DEPLOYMENT_ROLLBACK',
];

const client = {
  id: 1, code: 'LOCAL_TEST_BANK', legalName: 'Banque locale de test',
  commercialName: 'ScenarioGenerator', countryCode: 'MA', currencyCode: '504', status: 'ACTIVE',
};

const environment = {
  id: 10, clientId: 1, code: 'LOCAL', environmentType: 'LOCAL', targetOs: 'WINDOWS',
  shellType: 'GIT_BASH', shellExecutable: 'D:\\Program Files\\Git\\bin\\bash.exe',
  deploymentRoot: 'D:\\MoneyCore\\ScenarioGenerator\\runtime\\deployment\\local',
  javaExecutable: 'D:\\MoneyCore\\idea\\jbr\\bin\\java.exe', databaseType: 'NONE',
  memberModules: [], simulatorModules: ['THREE_DS_NETWORK_SIMULATOR'], variableReferences: {},
  simulatorsBundlePath: 'scenario-simulators-bundle.jar', licensePath: 'license.json.sig',
  licensePublicKeyPath: 'license-public.pem',
};

const catalog = {
  targetOperatingSystems: ['WINDOWS', 'LINUX'],
  compatibleShells: {
    WINDOWS: ['GIT_BASH', 'POWERSHELL', 'CMD_WINDOWS'],
    LINUX: ['BASH_LINUX'],
  },
  databaseTypes: ['NONE', 'POSTGRESQL', 'ORACLE'],
  modules: [{
    code: 'THREE_DS_NETWORK_SIMULATOR', label: 'Simulateur réseau 3DS', side: 'SIMULATOR',
    artifactId: 'sg-3ds-network-simulator',
    mainClass: 'com.staging.sg.threeds.network.ThreeDsNetworkSimulatorApplication',
    defaultPort: 8561, requiredVariables: [],
  }],
};

function token(): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: 'deployment-admin', role: 'ADMIN', permissions,
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.test`;
}

async function deploymentApi(route: Route) {
  const request = route.request();
  const path = new URL(request.url()).pathname;
  const method = request.method();
  if (path.endsWith('/catalog')) return route.fulfill({ json: catalog });
  if (path.endsWith('/clients')) return route.fulfill({ json: [client] });
  if (path.endsWith('/environments') && method === 'GET') return route.fulfill({ json: [environment] });
  if (path.endsWith('/environments/10/preflight')) return route.fulfill({ json: {
    executionId: 'preflight-e2e', checkedAt: '2026-08-02T08:00:00Z', verdict: 'READY',
    checks: [
      { code: 'JAVA', status: 'OK', detail: 'Java 25 disponible (minimum 21)' },
      { code: 'LICENSE', status: 'OK', detail: 'Signature et modules vérifiés' },
    ],
  } });
  if (path.endsWith('/licenses') && method === 'GET') return route.fulfill({ json: [{
    id: 'license-1', clientId: 1, environmentId: 10, status: 'PENDING',
    validFrom: '2026-08-02', validUntil: '2026-12-31', memberModules: [],
    simulatorModules: ['THREE_DS_NETWORK_SIMULATOR'], bundleVersion: '1.0.0-SNAPSHOT',
    preparedBy: 'maker-local', createdAt: '2026-08-02T08:00:00Z',
  }] });
  if (path.endsWith('/licenses/license-1/approve')) return route.fulfill({ json: {
    id: 'license-1', clientId: 1, environmentId: 10, status: 'ACTIVE',
    validFrom: '2026-08-02', validUntil: '2026-12-31', memberModules: [],
    simulatorModules: ['THREE_DS_NETWORK_SIMULATOR'], bundleVersion: '1.0.0-SNAPSHOT',
    preparedBy: 'maker-local', approvedBy: 'checker-local', createdAt: '2026-08-02T08:00:00Z',
    approvedAt: '2026-08-02T08:01:00Z',
  } });
  if (path.endsWith('/executions')) return route.fulfill({ json: [] });
  return route.fulfill({ status: 404, json: { message: `Mock absent: ${method} ${path}` } });
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(value => localStorage.setItem('sg-token', value), token());
  await page.route('**/api/me/navigation', route => route.fulfill({ json: { legacyFallback: false, modules: [] } }));
  await page.route('**/api/admin/deployments/**', deploymentApi);
});

test('valide les prérequis et filtre les shells selon le système cible', async ({ page }) => {
  await page.goto('/administration/deployments/environments');
  await expect(page.getByRole('heading', { name: 'Déploiements' })).toBeVisible();
  await expect(page.getByText('GIT_BASH')).toBeVisible();

  await page.getByTestId('preflight-button').click();
  await expect(page.getByText('READY')).toBeVisible();
  await expect(page.getByText('Java 25 disponible (minimum 21)')).toBeVisible();

  await page.getByRole('button', { name: /Nouvel environnement/i }).click();
  await page.locator('select[name="targetOs"]').selectOption('LINUX');
  await expect(page.getByTestId('shell-select')).toHaveValue('BASH_LINUX');
  await expect(page.getByTestId('shell-select').locator('option')).toHaveCount(1);
});

test('applique le maker-checker à la licence technique', async ({ page }) => {
  await page.goto('/administration/deployments/licenses');
  await expect(page.getByText('PENDING')).toBeVisible();
  await page.getByRole('button', { name: /Approuver/i }).click();
  await expect(page.getByText('ACTIVE')).toBeVisible();
  await expect(page.getByText('maker-local / checker-local')).toBeVisible();
});
