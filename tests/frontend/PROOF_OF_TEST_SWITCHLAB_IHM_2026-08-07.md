# Proof of Test — SwitchLab IHM

Date d'exécution : 7 août 2026
Branche : `codex/AddingVisaOnlineAndClearing`
Verdict : **RÉUSSI — 26/26 tests**

## Périmètre prouvé

Cette campagne vérifie uniquement les 26 cas marqués `TESTABLE` du cahier
`RECETTE_FRONTEND_SWITCHLAB.md`. Elle exécute le frontend SwitchLab réel dans
Chromium sur `http://127.0.0.1:4210` et contrôle les échanges HTTP au moyen de
réponses de contrat locales, déterministes et assainies.

Elle ne constitue pas une recette connectée du BFF, une validation ISO 8583,
une action monétique, une certification réseau ou un test avec des secrets et
cartes réels. Les cas `CONDITIONNEL`, `LECTURE`, `BLOQUÉ` et `NON EXPOSÉ` sont
hors de cette exécution.

## Environnement observé à la fin

| Composant | Port | PID | Rôle dans cette preuve |
|---|---:|---:|---|
| Frontend SwitchLab | 4210 | 13952 | Application Angular réellement pilotée par Chromium |
| BFF SwitchLab REST | 8090 | 23416 | Actif, mais appels du navigateur interceptés pour cette campagne IHM |
| Backend identité REST | 8080 | 1676 | Actif, mais authentification remplacée par une session de test locale |
| PostgreSQL | 5432 | 22724 | Actif, sans mutation attendue par cette campagne |

Aucun listener ISO n'a été utilisé. L'ancien frontend global sur le port 4200
avait été arrêté avant la campagne.

## Résultats

| Domaine | Cas réussis | Résultat |
|---|---|---:|
| Commun | `SL-COM-003`, `004`, `005`, `006` | 4/4 |
| Tableau de bord | `SL-DASH-004` | 1/1 |
| TPE & POS IHM | `SL-POS-001`, `004`, `011` | 3/3 |
| Test Center | `SL-TC-001`, `002`, `003`, `005`, `006`, `009`, `010`, `011`, `012`, `013`, `014`, `015` | 12/12 |
| Clearing IHM | `SL-CLR-003` | 1/1 |
| Industrialisation | `SL-IND-002`, `003` | 2/2 |
| Campagnes | `SL-CAMP-002`, `003`, `008` | 3/3 |
| **Total** | **26 cas** | **26/26** |

Les contrôles couvrent notamment la navigation, les préférences visuelles, la
déconnexion locale, l'état `UNKNOWN`, la validation JSON, l'absence de PAN/PIN
en clair dans l'historique, les rapports PDF/XLSX, les manifestes, les preuves
externes, le rejet d'une extension clearing interdite, la sauvegarde assainie
et l'arrêt du rafraîchissement visuel sans requête d'annulation backend.

## Commande et résultat final

Depuis `D:\MoneyCore\ScenarioGenerator\sg-frontend` :

```powershell
$env:E2E_BASE_URL='http://127.0.0.1:4210'
node ./node_modules/@playwright/test/cli.js test e2e/switchlab-ihm-contract.spec.ts --timeout 15000
```

Résultat exact : `26 passed (33.0s)`, code retour `0`, Chromium, un worker.

Le rapport HTML a été régénéré dans son dossier dédié après séparation des
rapports par produit : `26 passed (1.4m)`, code retour `0`. Cette régénération
n'a exécuté aucun test transverse.

La première exécution par lots a identifié deux libellés de harnais qui ne
correspondaient pas au DOM réel (`Ajouter un palier` et `Rafraîchir`). Les
sélecteurs ont été corrigés, les reprises ciblées ont réussi, puis la campagne
complète ci-dessus a été relancée avec succès. Il ne s'agissait pas de défauts
fonctionnels du produit.

## Éléments de preuve

- scénario automatisé : `sg-frontend/e2e/switchlab-ihm-contract.spec.ts` ;
- rapport HTML Playwright généré : `sg-frontend/playwright-report-switchlab/index.html` ;
- cahier source : `tests/frontend/RECETTE_FRONTEND_SWITCHLAB.md` ;
- preuve d'architecture préalable : `TR-ARC-001` et `TR-ARC-002` réussis dans
  `tests/frontend/RECETTE_FRONTEND_TRANSVERSE.md`.

## Anomalie d'environnement corrigée avant la campagne

Le backend d'identité ne démarrait plus avec l'artefact reconstruit, car
`PermissionRepository` manquait dans la liste JPA explicite. La liste a été
corrigée dans
`sg-common/src/main/java/com/staging/sg/common/persistence/OrchestratorPersistenceConfiguration.java`.
Le package Maven de l'orchestrateur a ensuite terminé avec `BUILD SUCCESS` en
38,968 s et le service a démarré sur le port 8080 avec 19 repositories JPA.

## Conclusion

Le périmètre **SwitchLab IHM TESTABLE** est validé à 100 % dans cet
environnement : **26 réussites, 0 échec**. La recette connectée et les actions
monétiques restent volontairement non prouvées.
