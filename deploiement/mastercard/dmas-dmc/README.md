# Livraison portable Mastercard DMAS + DMC

Cette livraison couvre les deux systèmes indépendants :

- `sg-mc-dmas-member` : autorisation DMAS côté membre ;
- `sg-mc-dmas-mastercard` : autorisation DMAS côté Mastercard ;
- `sg-dmcs-acquirer` : clearing DMC côté membre ;
- `sg-dmcs-issuer` : clearing DMC côté Mastercard.

Elle ne tue jamais tous les processus Java. L'arrêt utilise les PID enregistrés,
vérifie la ligne de commande du processus et ne cible que les quatre modules de
cette livraison. Un processus étranger qui occupe un port est signalé, jamais
arrêté automatiquement.

## 1. Références normatives

- Autorisation : `documents/specifications/mastercard/dmas/m_DMAS_en-us-2025-11-04.pdf`
- Clearing : `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.pdf`
- Version texte : `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.txt`
- Matrice : `conceptions/mastercard/clearing/MATRICE_CONFORMITE_DMAS_DMC.md`

## 2. Configuration du poste

Depuis Git Bash :

```bash
cd /f/MoneyCore/ScenarioGenerator
bash deploiement/common/runtime/detect-env.sh --write platform.env
source deploiement/common/runtime/platform-env.sh
```

Adapter `platform.env` avec les chemins réels du poste. Ne jamais y placer de
mot de passe, de PIN ou de clé claire.

Variables sensibles à exporter dans le terminal de test :

```bash
export DB_PASSWORD="<mot-de-passe-administrateur-postgresql>"
export DMAS_ADMIN_PASSWORD="<mot-de-passe-administrateur-dmas>"
export DMAS_KEK_CLEAR="<kek-claire-synthetique-de-test>"
export DMAS_MDK_CLEAR="<mdk-claire-synthetique-de-test>"
export DMAS_TEST_PIN="<pin-carte-synthetique-de-test>"
```

Les clés claires ne sont autorisées que dans le laboratoire ou la recette
synthétique. Elles ne doivent pas être commitées, journalisées ou utilisées en
production.

Variables locales non sensibles facultatives :

```bash
export DMAS_TEST_PAN="<pan-synthetique>"
export DMAS_TEST_AMOUNT="000000000100"
export DMC_BUSINESS_DATE="2026-07-29"
```

Sans `DMAS_TEST_PAN`, le script choisit une carte active de `mc_dmas_cards`.

## 3. Exécution pas à pas

```bash
bash deploiement/mastercard/dmas-dmc/00-install-database.sh
bash deploiement/mastercard/dmas-dmc/01-start-mastercard.sh
bash deploiement/mastercard/dmas-dmc/02-start-member.sh
bash deploiement/mastercard/dmas-dmc/03a-bootstrap-mastercard.sh
bash deploiement/mastercard/dmas-dmc/03b-bootstrap-member.sh
bash deploiement/mastercard/dmas-dmc/03c-signon-and-key-exchange.sh
bash deploiement/mastercard/dmas-dmc/04-test-pin.sh
bash deploiement/mastercard/dmas-dmc/04a-test-advice-reversal.sh
bash deploiement/mastercard/dmas-dmc/05-test-emv-arqc-arpc.sh
bash deploiement/mastercard/dmas-dmc/06-start-dmcs.sh
bash deploiement/mastercard/dmas-dmc/07-run-dmc-eod.sh
bash deploiement/mastercard/dmas-dmc/08-stop-dmas-dmc.sh
```

Le bootstrap est volontairement séparé :

- `03a` ne paramètre que Mastercard ;
- `03b` ne paramètre que le membre ;
- `03c` établit la session permanente et réalise l'échange dynamique de PEK.

## 4. Scénario automatique

```bash
bash deploiement/mastercard/dmas-dmc/dmas-dmc-e2e.sh
```

Le scénario vérifie PostgreSQL, applique V6/V7, compile et teste, démarre les
quatre applications, forme séparément les clés, teste PIN, reversal/advice puis
ARQC/ARPC, exécute les deux EOD indépendants et arrête uniquement ses propres
services.

Pour conserver les services actifs après le test :

```bash
export DMAS_DMC_KEEP_RUNNING=true
bash deploiement/mastercard/dmas-dmc/dmas-dmc-e2e.sh
```

## 5. Limite volontaire du premier incrément

Le codec jPOS produit et relit l'enveloppe IPM/RDW
`1644/697 - 1240/200 - 1644/695`. L'EOD alimente les tables propriétaires
DMCS à partir des journaux propriétaires DMAS.

La génération d'un First Presentment réel à partir de la table clearing reste
bloquée tant que la règle de construction de DE31/ARN n'est pas approuvée.
Le scénario n'invente donc aucun ARN. Chargeback, seconde présentation,
reconciliation et settlement restent des incréments suivants.

## 6. Fichiers et diagnostics

Tous les artefacts locaux sont sous :

```text
runtime/dmas-dmc/
├── logs/
├── pids/
└── dmcs/
    ├── acquirer/
    └── issuer/
```

Les chemins sont dérivés de `ROOT`. Aucune lettre de lecteur n'est codée en dur.
Le script d'installation reporte aussi les chemins de logs portables dans
`mc_dmas_interface`, car les applications DMAS lisent cette table au démarrage.
