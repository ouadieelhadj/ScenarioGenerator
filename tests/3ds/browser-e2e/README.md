# Test navigateur marchand et 3DS, composant par composant

Ce parcours complète les tests REST existants. Il reproduit le parcours d'un
client depuis le catalogue marchand jusqu'au reçu financier, avec redirection
vers l'ACS 3DS et saisie de l'OTP sandbox.

Les valeurs sensibles viennent uniquement du `.env` local ignoré. Aucun
script ne les affiche.

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
bash tests/3ds/browser-e2e/provision.sh
bash tests/3ds/browser-e2e/start-component.sh merchant-site
bash tests/3ds/browser-e2e/status.sh
```

Ouvrir ensuite `http://127.0.0.1:8551/` et suivre le parcours client :

1. choisir un article dans le catalogue ;
2. consulter sa fiche et l'ajouter au panier ;
3. valider le panier ;
4. choisir le paiement par carte ;
5. saisir le PAN et l'expiration de la carte de test autorisée ;
6. saisir l'OTP sur la page ACS ;
7. vérifier le reçu marchand.

Le client ne choisit aucune route technique. Acquisition résout le BIN de
manière autoritative, le Directory Server désigne l'ACS, puis la route
financière est recalculée lors de l'autorisation.

La page ACS affiche l'OTP uniquement parce que le profil `connected-e2e` est
actif. Après validation, le navigateur revient au marchand et doit afficher
une commande confirmée avec `APPROVED`, `00` et `AUTHENTICATED`.

## Journaux et arrêt

Dans un autre terminal :

```bash
bash tests/3ds/browser-e2e/tail-logs.sh
```

Quitter le `tail -F` avec `Ctrl+C`, puis arrêter les services depuis le même
Git Bash administrateur qui les a lancés :

```bash
bash tests/3ds/browser-e2e/stop.sh
```

Le script traite également les PID Windows `java` qui peuvent survivre au PID
POSIX de `nohup`.

## Contrôle automatique facultatif

Une fois les six composants actifs et le provisionnement terminé :

```bash
bash tests/3ds/browser-e2e/run-playwright.sh
```

Le résultat attendu est `1 passed`. Les captures sont écrites sous
`runtime/acquiring-ecommerce-e2e/ui-evidence/`.

Le PAN et l'expiration du test manuel sont les valeurs `ISSUING_E2E_PAN` et
`ISSUING_E2E_EXPIRY` du fichier `.env` local. L'OTP est affiché par la page
ACS sandbox et ne doit pas être affiché dans le terminal.
