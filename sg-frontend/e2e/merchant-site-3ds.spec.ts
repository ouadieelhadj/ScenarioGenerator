import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const merchantUrl = process.env.MERCHANT_SITE_BROWSER_URL ?? 'http://127.0.0.1:8551';
const pan = process.env.ISSUING_E2E_PAN;
const expiry = process.env.ISSUING_E2E_EXPIRY;
const otp = process.env.THREE_DS_SANDBOX_CHALLENGE_OTP;
const evidenceDir = path.resolve('..', 'runtime', 'acquiring-ecommerce-e2e', 'ui-evidence');

test('catalogue marchand, panier, paiement carte et challenge ACS membre', async ({ page }) => {
  test.setTimeout(120_000);
  test.skip(!pan || !expiry || !otp, 'Les valeurs de test doivent venir du .env local');
  fs.mkdirSync(evidenceDir, { recursive: true });

  await page.goto(merchantUrl);
  await expect(page.getByRole('heading', { name: 'Choisissez votre expérience' })).toBeVisible();
  await expect(page.getByText('Articles disponibles')).toBeVisible();
  await expect(page.getByText('Route attendue')).toHaveCount(0);
  await page.screenshot({ path: path.join(evidenceDir, '00-merchant-catalog.png'), fullPage: true });

  await page.getByRole('button', { name: 'Découvrir →' }).first().click();
  await expect(page.getByRole('heading', { name: 'Pack ScenarioGenerator Lab' })).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDir, '01-product-detail.png'), fullPage: true });
  await page.getByRole('button', { name: 'Ajouter au panier' }).click();
  await expect(page.getByRole('heading', { name: 'Mon panier' })).toBeVisible();
  await expect(page.getByText('10,00 MAD')).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDir, '02-cart.png'), fullPage: true });

  await page.getByRole('button', { name: 'Passer au paiement' }).click();
  await expect(page.getByRole('heading', { name: 'Choisissez votre moyen de paiement' })).toBeVisible();
  await page.getByRole('button', { name: /Carte bancaire/ }).click();
  await expect(page.getByRole('heading', { name: 'Informations de paiement' })).toBeVisible();
  await page.getByLabel('Numéro de carte').fill(pan!);
  await page.getByLabel('Expiration MM/AA').fill(expiry!.slice(0, 2) + '/' + expiry!.slice(2));
  await page.screenshot({ path: path.join(evidenceDir, '03-card-payment.png'), fullPage: true });

  await page.getByRole('button', { name: 'Payer et s’authentifier' }).click();
  await page.waitForURL('http://127.0.0.1:8560/acs/challenge.html**');
  await expect(page.getByRole('heading', { name: 'Confirmez votre paiement' })).toBeVisible();
  await expect(page.locator('#sandbox-otp')).toHaveText(otp!);
  await page.screenshot({ path: path.join(evidenceDir, '04-acs-challenge.png'), fullPage: true });

  await page.getByLabel('Code de vérification').fill(otp!);
  await page.getByRole('button', { name: 'Confirmer' }).click();
  await page.waitForURL(`${merchantUrl}/?checkoutId=**`);

  await expect(page.getByRole('heading', { name: 'Commande confirmée' }))
    .toBeVisible({ timeout: 30_000 });
  await expect(page.locator('#result-details')).toContainText('00');
  await expect(page.locator('#result-details')).toContainText('LOCAL_ISSUING');
  await expect(page.locator('#result-details')).toContainText('AUTHENTICATED');
  await expect(page.getByText(/ATLAS-/)).toBeVisible();
  await page.screenshot({ path: path.join(evidenceDir, '05-order-confirmed.png'), fullPage: true });
});
