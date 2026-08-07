import { expect, Page, Route, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';

type MockState = {
  fieldMapRequests: number;
  clearingUploads: number;
  executionReads: number;
  cancelRequests: number;
  importedSources: string[];
  lastCampaign?: Record<string, unknown>;
};

const now = '2026-08-07T12:30:00+01:00';
const environment = { id: 'local', code: 'LOCAL', label: 'Recette locale', type: 'LOCAL', active: true };
const catalog = [
  { code: 'MCD01', label: 'Mastercard disponibilité', moduleCode: 'MC_DMAS', network: 'MASTERCARD', type: 'HEALTH', executionMode: 'AUTOMATED', executable: true, requiredDataReferences: ['CARD_DATA'] },
  { code: 'VIS01', label: 'Visa disponibilité', moduleCode: 'VISA_ONLINE', network: 'VISA', type: 'HEALTH', executionMode: 'AUTOMATED', executable: true, requiredDataReferences: [] },
];
const report = {
  executionId: 'exec-001', campaignId: 'camp-001', environmentId: 'local', status: 'COMPLETED', verdict: 'PASSED',
  actualAvailabilityPercent: 100, expectedAvailabilityPercent: 100, elapsedMillis: 42, sampleCount: 2,
  errorRatePercent: 0, p95ResponseTimeMs: 12, correlationId: 'corr-safe-001', startedAt: now, completedAt: now,
  results: [{ testCode: 'MCD01', moduleCode: 'MC_DMAS', expected: 'UP', actual: 'UP', verdict: 'PASSED', elapsedMillis: 10, sampleCount: 1, successCount: 1, errorCount: 0, p95ResponseTimeMs: 10 }],
};
const legacyCampaign = {
  id: 1, name: 'Campagne IHM', description: 'Contrat UI', network: 'DMAS', initiator: 'ACQUIRER',
  category: 'AUTHORIZATION', config: '{}', expectedDe039: '00', active: true, createdByLogin: 'ui-test',
  slaP95MaxMs: 500, slaErrorRateMax: 10, slaApprovalMin: 90, stopOnErrorRate: 20,
  loadSteps: [{ stepOrder: 1, startSeconds: 0, endSeconds: 8, tpsValue: 5 }],
};

function token(): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    sub: 'switchlab-ui-proof', role: 'TESTER',
    permissions: ['CAMPAIGN_CREATE', 'CAMPAIGN_GENERATE', 'CAMPAIGN_VIEW', 'CAMPAIGN_REPLAY', 'TPS_RUN', 'EXECUTION_VIEW', 'USER_MANAGE', 'ROLE_MANAGE', 'CATALOG_MANAGE', 'DEPLOYMENT_VIEW'],
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.contract`;
}

async function fulfillJson(route: Route, json: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(json) });
}

async function installContract(page: Page): Promise<MockState> {
  const state: MockState = { fieldMapRequests: 0, clearingUploads: 0, executionReads: 0, cancelRequests: 0, importedSources: [] };
  await page.addInitScript(value => {
    localStorage.clear();
    localStorage.setItem('sg-token', value);
    localStorage.setItem('sg-lang', 'fr');
  }, token());

  await page.route('http://localhost:8090/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === '/api/me/navigation') return fulfillJson(route, {
      legacyFallback: false,
      modules: [{ code: 'LAB_SIMULATORS', labelKey: 'modules.simulators', icon: 'pi pi-bolt', children: [] }],
    });
    if (path === '/api/switchlab/v1/environments') return fulfillJson(route, [environment]);
    if (path === '/api/switchlab/v1/overview') return fulfillJson(route, {
      schemaVersion: '1.0', environment, overallStatus: 'UNKNOWN', availableComponents: 0, degradedComponents: 0,
      unavailableComponents: 1, checkedAt: now, correlationId: 'corr-safe-dashboard',
      components: [{ code: 'WAYPOS_SIMULATOR', status: 'UNKNOWN', checkedAt: now, capabilities: [], actions: [] }],
    });
    if (path === '/api/switchlab/v1/pos/catalog') return fulfillJson(route, [{ code: 'MCD01', label: 'Sentinelle UI', objective: 'Contrat IHM', classification: 'INTERACTIVE', requiresCertificationCard: true, expectedResults: ['Aucune donnée sensible'] }]);
    if (path === '/api/switchlab/v1/pos/history') return fulfillJson(route, [{
      executionId: 'pos-safe-001', operation: 'HISTORY', status: 'COMPLETED', verdict: 'PASSED', correlationId: 'corr-safe-pos',
      startedAt: now, completedAt: now, elapsedMillis: 5, requestSummary: { maskedPan: '555555******4444' },
      response: { responseCode: '00', maskedPan: '555555******4444' }, expectedResult: 'PAN masqué et aucun PIN',
    }]);
    if (path === '/api/switchlab/v1/pos/field-map') {
      state.fieldMapRequests++;
      return fulfillJson(route, { code: 'UNEXPECTED_REQUEST' }, 500);
    }

    if (path === '/api/switchlab/v1/test-center/catalog') return fulfillJson(route, catalog);
    if (path === '/api/switchlab/v1/test-center/profiles') return fulfillJson(route, [{ code: 'FUNCTIONAL', label: 'Fonctionnel', supported: true }]);
    if (path === '/api/switchlab/v1/test-center/campaigns' && method === 'GET') return fulfillJson(route, []);
    if (path === '/api/switchlab/v1/test-center/campaigns' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, any>;
      state.lastCampaign = body;
      const refs = Object.values(body['dataReferences'] ?? {}).map(String);
      if (refs.some(value => !/^(secret|vault|env|artifact):\/\//.test(value))) return fulfillJson(route, { message: 'Référence refusée' }, 400);
      return fulfillJson(route, { ...body, id: 'camp-created', status: 'CREATED', createdAt: now }, 201);
    }
    if (path === '/api/switchlab/v1/test-center/reports') return fulfillJson(route, [report]);
    if (/\/api\/switchlab\/v1\/test-center\/reports\/[^/]+\/export/.test(path)) {
      const format = url.searchParams.get('format') ?? 'PDF';
      const body = format === 'PDF' ? '%PDF-1.4\nSwitchLab proof\n' : 'SwitchLab XLSX proof';
      return route.fulfill({ status: 200, contentType: format === 'PDF' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body });
    }
    if (path === '/api/switchlab/v1/test-center/evidence' && method === 'GET') return fulfillJson(route, []);
    if (path === '/api/switchlab/v1/test-center/evidence' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, any>;
      state.importedSources.push(String(body['sourceType']));
      const verdict = Number(body['failed']) === 0 && Number(body['passed']) === Number(body['total']) ? 'PASSED' : 'FAILED';
      return fulfillJson(route, { ...body, id: `evidence-${state.importedSources.length}`, verdict, correlationId: 'corr-safe-evidence', importedAt: now }, 201);
    }
    if (path === '/api/switchlab/v1/test-center/certification/analyze') {
      const raw = request.postData() ?? '';
      if (/"(pan|pin|key|secret)"\s*:/i.test(raw)) return fulfillJson(route, { message: 'Sensitive content rejected' }, 400);
      return fulfillJson(route, { id: 'cert-001', sourceType: 'CERTIFICATION', name: 'Network certification', sourceReference: 'artifact://certification/manifest', total: 1, passed: 1, failed: 0, verdict: 'PASSED', correlationId: 'corr-safe-cert', importedAt: now });
    }

    if (path === '/api/switchlab/v1/clearing/networks') return fulfillJson(route, [{ code: 'SWAM_LIS', label: 'SWAM LIS', moduleCode: 'SWAM_LIS', status: 'UNKNOWN', uploadSupported: true, eodSupported: false, disputesSupported: false, limitations: ['Adaptateur non connecté'] }]);
    if (path === '/api/switchlab/v1/clearing/artifacts') return fulfillJson(route, []);
    if (/\/api\/switchlab\/v1\/clearing\/networks\/[^/]+\/files/.test(path)) {
      state.clearingUploads++;
      return fulfillJson(route, { message: 'Extension interdite' }, 400);
    }
    if (path === '/api/switchlab/v1/industrialization/readiness') return fulfillJson(route, [{ code: 'BACKUP', label: 'Sauvegarde', status: 'READY', evidence: 'Configuration non sensible', limitation: null }]);
    if (path === '/api/switchlab/v1/industrialization/backup') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ schemaVersion: '1.0', containsSecrets: false, configuration: { product: 'SWITCHLAB' } }) });

    if (path === '/api/campaigns' && method === 'GET') return fulfillJson(route, [legacyCampaign]);
    if (path === '/api/networks') return fulfillJson(route, [{ code: 'DMAS', name: 'DMAS', active: true }]);
    if (path === '/api/admin/message-types') return fulfillJson(route, [{ code: '0200', name: 'Authorization', category: 'AUTHORIZATION', network: 'DMAS', direction: 'BOTH' }]);
    if (path === '/api/campaigns/1/run') return fulfillJson(route, { campaignExecutionId: 101 });
    if (path === '/api/campaigns/executions/101') {
      state.executionReads++;
      return fulfillJson(route, { campaignExecutionId: 101, status: 'RUNNING', txTotal: 0, txApproved: 0, txDeclined: 0, verdict: '', verdictDetail: '', tpsActualAvg: 0, responseTimeAvg: 0 });
    }
    if (/cancel|stop/i.test(path) && method !== 'GET') state.cancelRequests++;
    return fulfillJson(route, { message: `Mock absent: ${method} ${path}` }, 404);
  });
  return state;
}

let state: MockState;
test.beforeEach(async ({ page }) => { state = await installContract(page); });

test('[SL-COM-003] change FR/EN/ES sans changer de produit', async ({ page }) => {
  await page.goto('/dashboard');
  const select = page.locator('.lang-picker select');
  for (const lang of ['en', 'es', 'fr']) { await select.selectOption(lang); await expect(page.locator('html')).toHaveAttribute('lang', lang); }
  await expect(page.getByText('FuturPayment SwitchLab', { exact: true })).toBeVisible();
});

test('[SL-COM-004] applique le thème sans perdre la session', async ({ page }) => {
  await page.goto('/dashboard');
  await page.locator('.theme-picker select').selectOption('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('sg-token'))).not.toBeNull();
});

test('[SL-COM-005] applique une couleur primaire lisible', async ({ page }) => {
  await page.goto('/dashboard');
  await page.locator('.theme-picker input[type="color"]').evaluate((element: HTMLInputElement) => { element.value = '#7c3aed'; element.dispatchEvent(new Event('input', { bubbles: true })); });
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--sg-color-primary').trim())).toBe('#7c3aed');
});

test('[SL-COM-006] déconnecte et supprime la session locale', async ({ page }) => {
  await page.goto('/dashboard');
  await page.locator('button.logout').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('sg-token'))).toBeNull();
});

test('[SL-DASH-004] affiche UNKNOWN pour une sonde non configurée', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page.locator('.component-card .status-unknown')).toHaveText('UNKNOWN');
  await expect(page.locator('.component-card .status-up')).toHaveCount(0);
});

test('[SL-POS-001] navigue entre les six onglets POS', async ({ page }) => {
  await page.goto('/lab/pos');
  const cases: Array<[string, string]> = [['Transaction', 'Nouvelle transaction'], ['Field-map', 'Transaction field-map'], ['Repeat', 'Repeat de la dernière transaction'], ['RKI', 'Assistant RKI'], ['MTIP sentinelle', 'Paramètres de certification'], ['Historique', 'Historique POS']];
  for (const [tab, heading] of cases) { await page.locator('nav.tabs button').filter({ hasText: tab }).click(); await expect(page.getByRole('heading', { name: heading })).toBeVisible(); }
});

test('[SL-POS-004] rejette un field-map JSON invalide sans appel', async ({ page }) => {
  await page.goto('/lab/pos');
  await page.locator('nav.tabs button').filter({ hasText: 'Field-map' }).click();
  await page.getByLabel('Champs texte JSON').fill('{invalid');
  await page.getByRole('button', { name: /Envoyer le field-map/ }).click();
  await expect(page.getByText('Le JSON du field-map est invalide.')).toBeVisible();
  expect(state.fieldMapRequests).toBe(0);
});

test('[SL-POS-011] actualise un historique sans PAN/PIN clair', async ({ page }) => {
  await page.goto('/lab/pos');
  await page.locator('nav.tabs button').filter({ hasText: 'Historique' }).click();
  await page.getByRole('button', { name: /Actualiser/ }).click();
  await page.locator('.history button').first().click();
  await expect(page.getByText('555555******4444')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('5555555555554444');
  await expect(page.locator('body')).not.toContainText('"pin"');
});

test('[SL-TC-001] navigue dans les espaces du Test Center', async ({ page }) => {
  await page.goto('/lab/test-center');
  for (const name of ['Catalogue', 'Campagnes', 'SLA', 'Rapports', 'externes']) { const tab = page.locator('nav.tabs button').filter({ hasText: name }); await tab.click(); await expect(tab).toHaveClass(/active/); }
});

test('[SL-TC-002] filtre le catalogue par réseau', async ({ page }) => {
  await page.goto('/lab/test-center');
  await page.getByLabel(/seau/).selectOption('MASTERCARD');
  await expect(page.getByText('Mastercard disponibilité')).toBeVisible();
  await expect(page.getByText('Visa disponibilité')).toHaveCount(0);
});

test('[SL-TC-003] crée une campagne FUNCTIONAL', async ({ page }) => {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'Campagnes' }).click();
  await page.getByLabel('Nom').fill('Campagne UI FUNCTIONAL');
  await page.locator('fieldset input[type="checkbox"]').first().check();
  await page.getByRole('button', { name: 'Enregistrer la campagne' }).click();
  await expect(page.getByRole('heading', { name: 'Campagne UI FUNCTIONAL' })).toBeVisible();
});

test('[SL-TC-005] accepte une référence opaque autorisée', async ({ page }) => {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'Campagnes' }).click();
  await page.getByLabel('Nom').fill('Campagne référence sûre');
  await page.getByLabel(/rences de donn/).fill('CARD_DATA=secret://vault/certification-card');
  await page.locator('fieldset input[type="checkbox"]').first().check();
  await page.getByRole('button', { name: 'Enregistrer la campagne' }).click();
  await expect(page.getByRole('heading', { name: 'Campagne référence sûre' })).toBeVisible();
  expect((state.lastCampaign?.['dataReferences'] as Record<string, string>)['CARD_DATA']).toMatch(/^secret:\/\//);
});

test('[SL-TC-006] rejette une valeur sensible en clair', async ({ page }) => {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'Campagnes' }).click();
  await page.getByLabel('Nom').fill('Campagne refusée');
  await page.getByLabel(/rences de donn/).fill('CARD_DATA=valeur-claire-interdite');
  await page.getByRole('button', { name: 'Enregistrer la campagne' }).click();
  await expect(page.getByText(/La campagne n.*a pas pu/)).toBeVisible();
});

async function downloadReport(page: Page, format: 'PDF' | 'XLSX'): Promise<void> {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'Rapports' }).click();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: format, exact: true }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(`switchlab-report-exec-001.${format.toLowerCase()}`);
}

test('[SL-TC-009] télécharge le rapport PDF', async ({ page }) => { await downloadReport(page, 'PDF'); });
test('[SL-TC-010] télécharge le rapport XLSX', async ({ page }) => { await downloadReport(page, 'XLSX'); });

test('[SL-TC-011] analyse un manifeste valide sans commande locale', async ({ page }) => {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'externes' }).click();
  await page.getByRole('button', { name: 'Analyser sans exécuter' }).click();
  await expect(page.getByRole('heading', { name: 'Network certification' })).toBeVisible();
});

test('[SL-TC-012] rejette un manifeste contenant un PAN', async ({ page }) => {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'externes' }).click();
  await page.getByLabel(/Manifeste JSON/).fill('{"name":"bad","network":"MC","schemaVersion":"1","sourceReference":"artifact://bad","tests":[],"pan":"interdit"}');
  await page.getByRole('button', { name: 'Analyser sans exécuter' }).click();
  await expect(page.getByText(/Analyse refus/)).toBeVisible();
});

async function importEvidence(page: Page, source: string, name: string): Promise<void> {
  await page.goto('/lab/test-center'); await page.locator('nav.tabs button').filter({ hasText: 'externes' }).click();
  await page.getByLabel('Source').selectOption(source);
  await page.getByLabel('Nom').last().fill(name);
  await page.getByLabel(/rence contr/).fill(`artifact://proof/${source.toLowerCase()}/001`);
  await page.getByLabel('Total').fill('2'); await page.getByLabel(/ussis/).fill('2'); await page.getByLabel(/chou/).fill('0');
  await page.getByRole('button', { name: 'Importer les métadonnées' }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

test('[SL-TC-013] importe des métadonnées JUnit', async ({ page }) => { await importEvidence(page, 'JUNIT', 'Preuve JUnit'); expect(state.importedSources).toContain('JUNIT'); });
test('[SL-TC-014] importe des métadonnées Playwright sans exécution', async ({ page }) => { await importEvidence(page, 'PLAYWRIGHT', 'Preuve Playwright'); expect(state.importedSources).toContain('PLAYWRIGHT'); });
test('[SL-TC-015] importe une preuve de certification', async ({ page }) => { await importEvidence(page, 'CERTIFICATION', 'Preuve Certification'); await expect(page.getByText('PASSED')).toBeVisible(); });

test('[SL-CLR-003] rejette une extension clearing interdite', async ({ page }) => {
  await page.goto('/lab/clearing');
  await page.locator('input[type="file"]').setInputFiles({ name: 'preuve-interdite.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('not-a-clearing-file') });
  await expect(page.getByText(/Import refus/)).toBeVisible();
  expect(state.clearingUploads).toBe(1);
});

async function downloadBackup(page: Page): Promise<Record<string, any>> {
  await page.goto('/lab/industrialization');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: /Sauvegarder la configuration/ }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('switchlab-configuration-backup.json');
  const path = await download.path();
  return JSON.parse(await readFile(path!, 'utf8')) as Record<string, any>;
}

test('[SL-IND-002] télécharge une sauvegarde sans secrets', async ({ page }) => { const backup = await downloadBackup(page); expect(backup['containsSecrets']).toBe(false); });
test('[SL-IND-003] vérifie le contenu assaini de la sauvegarde', async ({ page }) => { const backup = await downloadBackup(page); const raw = JSON.stringify(backup); expect(raw).not.toMatch(/"(pan|pin|password|privateKey|secret)"\s*:/i); });

test('[SL-CAMP-002] ouvre le formulaire de création', async ({ page }) => {
  await page.goto('/campaign-generation');
  await page.getByRole('button', { name: /Nouvelle campagne/ }).click();
  await expect(page.locator('.dialog')).toBeVisible();
});

test('[SL-CAMP-003] ajoute et supprime une étape de charge', async ({ page }) => {
  await page.goto('/campaign-generation'); await page.getByRole('button', { name: /Nouvelle campagne/ }).click();
  await expect(page.locator('.steps-table tbody tr')).toHaveCount(1);
  await page.getByRole('button', { name: /Ajouter un palier/ }).click();
  await expect(page.locator('.steps-table tbody tr')).toHaveCount(2);
  await page.locator('.steps-table tbody tr').last().locator('button').click();
  await expect(page.locator('.steps-table tbody tr')).toHaveCount(1);
});

test('[SL-CAMP-008] arrête le suivi visuel sans annuler le backend', async ({ page }) => {
  await page.goto('/campaign-orchestration');
  await page.getByRole('button', { name: /Lancer/ }).click();
  await expect(page.getByText(/chissement auto/)).toBeVisible();
  await page.getByRole('button', { name: /Arrêter le suivi/ }).click();
  const reads = state.executionReads;
  await page.waitForTimeout(2300);
  expect(state.executionReads).toBe(reads);
  expect(state.cancelRequests).toBe(0);
  await expect(page.getByRole('button', { name: /Rafra/ })).toBeVisible();
});
