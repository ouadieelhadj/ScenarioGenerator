# Reprise du chantier frontend global

## État au 2 août 2026

Le cadrage final est `documents/design/frontend/CADRAGE_FRONTEND_FINAL_V1.md`.
Le manuel opérateur est `tests/frontend/MANUEL_TEST_FRONTEND_GLOBAL.md`.

## Travail terminé

- audit Angular, navigation dynamique, RBAC, Utilisateurs et SQL Maker/Checker ;
- menu commun permanent et menus métier hiérarchiques ;
- modules ServerPOS, Acquisition, Issuing, SWAM Membre, DMAS Membre, DMCS
  Membre et Simulateurs ;
- sites marchands national/international exclusivement sous Simulateurs ;
- registre `componentKey` fail-closed et garde de route dynamique ;
- routes Administration Utilisateurs/Rôles ;
- API backend de consultation des rôles et permissions ;
- écrans Mes opérations/Mes validations, fermés si les API workflow manquent ;
- catalogue SQL idempotent `sql/19_frontend_global_catalog.sql` ;
- traductions FR/EN/ES ;
- retrait des fixtures PAN/PIN/clé codées en dur dans le frontend ;
- Playwright contractuel et scripts Git Bash.

## Validation exacte

- `npm.cmd run build` : succès ;
- `npx.cmd playwright test frontend-global-shell.spec.ts` sur le serveur isolé :
  3 tests, 3 réussis, code retour 0 ;
- Maven embarqué, `-pl sg-generator-orchestrator -am test` : BUILD SUCCESS ;
- `sg-common` : 69 tests, 0 échec ;
- orchestrateur : 44 sources compilées, aucun test propre au module.

## Premier travail non terminé

1. appliquer les migrations SQL 18 puis 19 sur une base de recette ;
2. démarrer l'orchestrateur et le frontend ;
3. exécuter `tests/frontend/gitbash/run-connected-playwright.sh` avec les
   identifiants fournis par l'environnement ;
4. implémenter ensuite les API Maker/Checker réelles avant d'autoriser les
   actions de soumission, approbation ou rejet ;
5. raccorder progressivement chaque écran générique à son API métier.

Ne pas inventer de demandes workflow, de secrets ou de données monétiques pour
faire passer le test connecté.

## Processus actifs

Aucun serveur de test démarré par cette session n'est encore actif. Un ancien
serveur Angular sur le port 4200 a été observé et volontairement conservé car il
n'appartient pas à cette session.
