# Reprise de la session SWAM LIS

## État du développement

- Branche : `codex/swam-lis-clearing`
- Commit poussé : `9ef5980` (`Finaliser le clearing SWAM LIS bilateral`)
- Dépôt distant : `origin/codex/swam-lis-clearing`
- Modules : `sg-swam-lis-common`, `sg-swam-lis-member`, `sg-swam-lis-switch`
- Le SID supporte les transactions dans les deux sens sur une liaison permanente unique.
- Le scénario couvre les EOD membre et switch, les LIS outgoing, les imports croisés,
  le rapprochement, les chargebacks, la représentation et les écritures comptables.

## Validation déjà effectuée

- Reactor Maven complet : succès.
- E2E complet : `RESULTAT : PASSED (36 controles)`.
- Comptabilités membre et switch équilibrées.
- Les modifications préexistantes sans rapport (`NetworkRef.java`, traces, documents,
  `runtime/`, `tmp/`, etc.) sont restées locales et hors du commit.

## Commande Git Bash

```bash
cd /d/MoneyCore/ScenarioGenerator
export SWAM_E2E_KEK_CLEAR="0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
bash deploiement/swam-lis-e2e.sh
```

La clé ci-dessus est exclusivement une clé de test.

## Dernier état observé chez l'utilisateur

```text
[OK] Migration journaux SID pour clearing
[OK] SWAM switch issuer UP
```

Les nombreux messages PostgreSQL `NOTICE` indiquant que les colonnes existent déjà
sont normaux. Ils ne constituent pas des erreurs. Il faut laisser le script continuer
jusqu'au résultat final ou diagnostiquer le prochain service si aucune nouvelle ligne
n'apparaît pendant environ deux minutes.
