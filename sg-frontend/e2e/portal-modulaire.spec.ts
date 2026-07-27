import { expect, test } from '@playwright/test';

const login = process.env['E2E_LOGIN'] ?? 'admin';
const password = process.env['E2E_PASSWORD'] ?? 'Admin123!';

async function authenticate(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: /identifiant|username|usuario/i }).fill(login);
  await page.getByLabel(/mot de passe|password|contrase/i).fill(password);
  await page.getByRole('button', { name: /se connecter|sign in|conectar/i }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test('un visiteur non authentifie est redirige vers la connexion', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'ScenarioGenerator' })).toBeVisible();
});

test('admin voit les modules autorises renvoyes par le backend', async ({ page }) => {
  await authenticate(page);
  await expect(page.getByRole('button', { name: /SWAM LIS Membre/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /SWAM LIS Switch/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /Monetique DMAS|Monétique DMAS/i })).toBeVisible();
});

test('le meme ecran clearing est charge avec le contexte du module', async ({ page }) => {
  await authenticate(page);

  await page.getByRole('button', { name: /SWAM LIS Membre/i }).click();
  await page.getByRole('link', { name: /Transactions de clearing/i }).click();
  await expect(page).toHaveURL(/\/modules\/SWAM_LIS_MEMBER\/transactions$/);
  await expect(page.getByText('SWAM_LIS_MEMBER')).toBeVisible();

  await page.getByRole('button', { name: /SWAM LIS Switch/i }).click();
  await page.getByRole('link', { name: /Transactions de clearing/i }).click();
  await expect(page).toHaveURL(/\/modules\/SWAM_LIS_SWITCH\/transactions$/);
  await expect(page.getByText('SWAM_LIS_SWITCH')).toBeVisible();
});

test('la deconnexion supprime la session', async ({ page }) => {
  await authenticate(page);
  await page.locator('button.logout').click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});
