# Proof of Test — Switch IHM

Date d'exécution : 7 août 2026
Branche : `codex/AddingVisaOnlineAndClearing`
Verdict : **RÉUSSI — 11/11 tests**

## Périmètre prouvé

Cette campagne vérifie uniquement les 11 lignes marquées `TESTABLE` du cahier
`RECETTE_FRONTEND_SWITCH.md`. Elle pilote le frontend Switch réel dans Chromium
sur `http://127.0.0.1:4220`. Les réponses HTTP du BFF sont remplacées dans le
navigateur par des contrats locaux, déterministes et assainis.

Cette preuve ne valide pas le BFF Switch, les modules membre, une transaction
ISO 8583, une action monétique ou une certification réseau. Les tests
transverses ne sont pas exécutés : ils sont réservés à la prochaine session
commune avec l'utilisateur.

## Environnement observé à la fin

| Composant | Port | PID | État dans cette campagne |
|---|---:|---:|---|
| Frontend Switch | 4220 | 14052 | Actif et réellement piloté par Chromium |
| BFF Switch REST | 8091 | — | Non démarré ; contrats HTTP interceptés côté navigateur |
| Frontend SwitchLab | 4210 | 13952 | Actif mais jamais appelé par les tests Switch |
| BFF SwitchLab REST | 8090 | 23416 | Actif mais jamais appelé par les tests Switch |
| Backend identité REST | 8080 | 1676 | Actif mais session remplacée par un jeton local de test |
| PostgreSQL | 5432 | 22724 | Actif, sans mutation attendue |

Aucun listener ISO n'a été utilisé.

## Résultats

| Domaine | Cas réussis | Résultat |
|---|---|---:|
| Commun | `SW-COM-003`, `004`, `005`, `006` | 4/4 |
| Registre d'interfaces | `SW-INT-003`, `004`, `005` | 3/3 |
| Acquisition | `SW-ACQ-002` | 1/1 |
| Réseaux membre | `SW-NET-002` | 1/1 |
| Clearing membre | `SW-CLR-002` | 1/1 |
| Déploiement | `SW-DEP-003` | 1/1 |
| **Total** | **11 cas** | **11/11** |

Les contrôles vérifient notamment la langue, le thème, la déconnexion, une
liste d'interfaces réellement vide, le maintien local du formulaire lorsque
Maker/Checker est indisponible, la non-résolution navigateur des références
`vault://` et `hsm://`, l'état `UNKNOWN`, l'absence d'appel aux modules issuer
simulés, le module `SG_DMCS_ACQUIRER` et la cohérence OS/shell.

## Commande et résultat final

Depuis `D:\MoneyCore\ScenarioGenerator\sg-frontend` :

```powershell
$env:E2E_BASE_URL='http://127.0.0.1:4220'
node ./node_modules/@playwright/test/cli.js test e2e/switch-ihm-contract.spec.ts --timeout 15000
```

Résultat exact : `11 passed (52.4s)`, code retour `0`, Chromium, un worker.

## Anomalie IHM corrigée

La première reprise ciblée a montré que les cartes de service affichaient le
libellé mais pas le code technique retourné par le contrat. Il était donc
impossible de distinguer visuellement `SG_DMCS_ACQUIRER` d'un module issuer.
Les composants Acquisition et Domaines membre affichent désormais le code du
service sous son libellé. Les cas ciblés ont ensuite réussi, puis les 11 cas ont
été relancés ensemble avec succès.

## Sécurité et frontières observées

- aucune requête navigateur vers le port 8090, `/api/switchlab/` ou le
  simulateur WayPOS n'a été observée ;
- aucune requête de mutation n'est émise par les formulaires bloqués ;
- aucune résolution de référence `vault://` ou `hsm://` n'est tentée ;
- aucun PAN, PIN, CVC, clé ou secret réel n'a été utilisé.

## Éléments de preuve

- scénario : `sg-frontend/e2e/switch-ihm-contract.spec.ts` ;
- rapport HTML : `sg-frontend/playwright-report-switch/index.html` ;
- cahier source : `tests/frontend/RECETTE_FRONTEND_SWITCH.md` ;
- configuration de séparation des rapports : `sg-frontend/playwright.config.ts`.

## Conclusion

Le périmètre **Switch IHM TESTABLE** est validé : **11 réussites, 0 échec**.
Les 72 autres cas du cahier et les tests transverses restent volontairement
hors de cette preuve.
