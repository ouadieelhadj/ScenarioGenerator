import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const merchantUrl = process.env.MERCHANT_SITE_BROWSER_URL ?? 'http://127.0.0.1:8551';
const pan = process.env.ISSUING_E2E_PAN;
const expiry = process.env.ISSUING_E2E_EXPIRY;
const otp = process.env.THREE_DS_SANDBOX_CHALLENGE_OTP;
const evidenceDir = path.resolve('..', 'runtime', 'acquiring-ecommerce-e2e', 'ui-evidence');

test('achat marchand manuel avec challenge OTP ACS membre', async ({ page }) => {
  test.setTimeout(90_000);
  test.skip(!pan || !expiry || !otp, 'Les valeurs de test doivent venir du .env local');
  fs.mkdirSync(evidenceDir, { recursive: true });

  await page.goto(merchantUrl);
  await expect(page.getByText('Profil marchand actif')).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDir, '00-merchant-checkout.png'), fullPage: true });

  await page.getByLabel('Numéro de carte').fill(pan!);
  await page.getByLabel('Expiration MM/AA').fill(expiry!.slice(0, 2) + '/' + expiry!.slice(2));
  await page.getByLabel('Montant (MAD)').fill('10.00');
  await page.getByText('Paramètres du scénario sandbox').click();
  await page.getByLabel('Route attendue').selectOption('LOCAL_ISSUING');
  await page.getByLabel('Type de site').selectOption('NATIONAL');
  await page.getByLabel('Programme 3DS').selectOption('MASTERCARD');
  await page.getByLabel('Parcours 3DS').selectOption('CHALLENGE');
  await page.getByLabel('ACS émetteur').selectOption('MEMBER');

  await page.getByRole('button', { name: 'Payer maintenant' }).click();
  await page.waitForURL('http://127.0.0.1:8560/acs/challenge.html**');
  await expect(page.getByRole('heading', { name: 'Confirmez votre paiement' })).toBeVisible();
  await expect(page.locator('#sandbox-otp')).toHaveText(otp!);
  await page.screenshot({ path: path.join(evidenceDir, '01-acs-challenge.png'), fullPage: true });

  await page.getByLabel('Code de vérification').fill(otp!);
  await page.getByRole('button', { name: 'Confirmer' }).click();
  await page.waitForURL(`${merchantUrl}/?checkoutId=**`);

  await expect(page.getByRole('heading', { name: 'Paiement approuvé' }))
    .toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#result-details')).toContainText('00');
  await expect(page.locator('#result-details')).toContainText('LOCAL_ISSUING');
  await expect(page.locator('#result-details')).toContainText('AUTHENTICATED');
  await page.screenshot({ path: path.join(evidenceDir, '02-payment-approved.png'), fullPage: true });
});
