# Preuve — Baseline, Trust Validation et apprentissage adaptatif

**Date :** 17 août 2026
**Périmètre :** laboratoire synthétique multibanque, sans données bancaires réelles
**Statut :** `TRUST_VALIDATION_LAB_OK`

## Résultat exécuté

La campagne a traité **1 000 002 opérations synthétiques** réparties entre trois banques et deux secteurs par banque : Monétique et Mobile Banking. Chaque banque contient exactement **100 opérations suspectes** et **5 fraudes confirmées**.

Le protocole exécute trois phases distinctes :

1. baseline des contrôles initiaux génériques ;
2. apprentissage sur l'historique, gel du modèle, puis Trust Validation rétrospective en aveugle ;
3. découverte adaptative de contrôles candidats soumis à validation humaine.

Les étiquettes du jeu aveugle ne sont jamais utilisées pendant l'apprentissage. Le modèle est gelé avant son évaluation. Une fraude confirmée par secteur est volontairement placée dans ce jeu, soit six fraudes cachées au total.

## Résultats par banque et secteur

| Banque | Secteur | Opérations | Rappel baseline | Rappel Trust | Fraude cachée détectée | Propositions |
|---|---|---:|---:|---:|---:|---:|
| Ouadie Bank | Monétique | 166 667 | 0,182 | 1,000 | 1/1 | 2 |
| Ouadie Bank | Mobile Banking | 166 667 | 0,273 | 1,000 | 1/1 | 2 |
| Tresor Bank | Monétique | 166 667 | 0,143 | 1,000 | 1/1 | 2 |
| Tresor Bank | Mobile Banking | 166 667 | 0,167 | 0,667 | 1/1 | 2 |
| Sedik Bank | Monétique | 166 667 | 0,000 | 1,000 | 1/1 | 2 |
| Sedik Bank | Mobile Banking | 166 667 | 0,375 | 0,750 | 1/1 | 2 |

Résultat de la porte automatique : **6/6 fraudes confirmées cachées détectées**. Douze contrôles candidats ont été produits avec le statut `PROPOSED`. Aucun contrôle n'a été activé automatiquement.

## Contrôles automatiques

Le lanceur vérifie notamment :

- trois banques et deux secteurs par banque ;
- le volume total attendu ;
- 100 cas suspects et 5 fraudes confirmées par banque ;
- séparation du jeu aveugle et gel du modèle avant évaluation ;
- présence et détection des six fraudes cachées ;
- mesure séparée de la baseline et de la Trust Validation ;
- deux propositions par segment et absence d'activation automatique.

La campagne est maintenant intégrée à `fraud-tools-lite/scripts/test-all-fraud-tools-lite.ps1`.

Commande finale exécutée :

```powershell
$env:FRAUD_TRUST_ROWS_PER_BANK = '333334'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\MoneyCore\ScenarioGenerator\fraud-tools-lite\scripts\test-trust-validation-campaign.ps1
```

Sortie finale :

```text
TRUST_VALIDATION_CAMPAIGN_OK Banks=3 Sectors=2 Rows=1000002 HiddenFraud=6/6
```

## Artefacts et intégrité

- rapport JSON : `fraud-tools-lite/evidence/trust-validation-campaign.json` — SHA-256 `38BD16901A00EE102702F2FB1D1C7A2D1E0FB8E5205F70F4B8D28B257A4B1B58` ;
- synthèse Markdown : `fraud-tools-lite/evidence/trust-validation-campaign.md` — SHA-256 `FB8690361B3B1B5E8FAB9006A1A9989ED09233768A9B8EC7BA4D41FBD1F5AA3B` ;
- générateur : `fraud-tools-lite/ai-lab/trust_validation_campaign.py` — SHA-256 `4FA8E3FB891EEF3576B523179EC4FC5E83EAE23E29FD591F807F59EF86AC0755` ;
- validateur : `fraud-tools-lite/scripts/test-trust-validation-campaign.ps1` — SHA-256 `1893E4864BD4D1CD41786F599CE330C563A831ABC97AFBA6D1E0D95E1B1F43AD`.

## Limite de la preuve

Cette campagne démontre le protocole et son exécution à grand volume sur des données synthétiques. Elle ne constitue pas une mesure de performance bancaire ni un GO production. La validation client exigera un corpus historique labellisé, une période aveugle convenue avec la banque et des seuils métier approuvés.
