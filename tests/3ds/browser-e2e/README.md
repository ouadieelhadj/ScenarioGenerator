# Test navigateur marchand et 3DS, composant par composant

Ce parcours complète les tests REST existants. Il permet à l'opérateur de
saisir la carte dans une boutique locale, d'être redirigé vers l'ACS, de saisir
l'OTP sandbox affiché, puis de revenir au résultat financier.

Les valeurs sensibles viennent uniquement du `.env` local ignore. Aucun script
ne les affiche.

## Démarrage détaillé depuis Git Bash

Depuis la racine du dépôt, exécuter une commande puis vérifier sa sortie avant
de passer à la suivante :

```bash
bash tests/3ds/browser-e2e/stop.sh
bash tests/3ds/browser-e2e/build.sh
bash tests/3ds/browser-e2e/start-component.sh issuing
bash tests/3ds/browser-e2e/start-component.sh gateway
bash tests/3ds/browser-e2e/start-component.sh acquiring
bash tests/3ds/browser-e2e/start-component.sh 3ds-member
bash tests/3ds/browser-e2e/start-component.sh 3ds-network
bash tests/3ds/browser-e2e/start-component.sh merchant-site
bash tests/3ds/browser-e2e/provision.sh
bash tests/3ds/browser-e2e/status.sh
```

Ouvrir ensuite `http://127.0.0.1:8551/` dans le navigateur. Saisir le PAN et
l'expiration de la carte de test autorisée. Conserver `LOCAL_ISSUING`,
`NATIONAL`, `MASTERCARD`, `CHALLENGE` et `ACS LanaCash` pour le premier test.

Le site redirige vers l'URL ACS renvoyée par le Directory Server. La page ACS
affiche l'OTP uniquement parce que le profil `connected-e2e` est actif. Après
validation, le navigateur revient au marchand et doit afficher `APPROVED`,
`00` et `AUTHENTICATED`.

Pour suivre les journaux dans un autre terminal :

```bash
bash tests/3ds/browser-e2e/tail-logs.sh
```

Quitter le `tail -F` avec `Ctrl+C`, puis arrêter les services :

```bash
bash tests/3ds/browser-e2e/stop.sh
```

## Controle automatique facultatif

Une fois les six composants actifs et le provisionnement termine, le meme
parcours peut etre rejoue automatiquement avec Chromium :

```bash
bash tests/3ds/browser-e2e/run-playwright.sh
```

Le resultat attendu est `1 passed`. Les captures sont ecrites sous
`runtime/acquiring-ecommerce-e2e/ui-evidence/`.

Le PAN et l'expiration du test manuel sont les valeurs `ISSUING_E2E_PAN` et
`ISSUING_E2E_EXPIRY` du fichier `.env` local. L'OTP est affiche par la page
ACS sandbox et ne doit pas etre affiche dans le terminal.
