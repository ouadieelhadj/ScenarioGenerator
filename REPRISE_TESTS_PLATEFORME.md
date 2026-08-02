# Reprise - recettes E2E globales de la plateforme

## Situation au 2 aout 2026

Le chantier d'orchestration Git Bash sous `tests/platform-e2e/` est implemente
et valide. Il reutilise les harnais fonctionnels existants sans dupliquer les
codecs, les regles cryptographiques ou la gestion des PID.

Domaines couverts :

1. Issuing local ;
2. Acquisition TPE/ServerPOS et e-commerce ;
3. 3DS sandbox ;
4. Mastercard DMAS et DMCS ;
5. SWAM SID et LIS ;
6. Visa Online et Base II ;
7. integration globale sequentielle.

Chaque domaine expose les scripts `00` a `07`, un `run-all.sh` et un guide de
test schematise. Le manuel operateur RECETTE est
`tests/platform-e2e/INSTRUCTIONS_LIVRAISON_RECETTE.md`.

## Configuration et cles de test

Le modele versionne est
`tests/platform-e2e/platform-e2e.env.example`. Les valeurs sont chargees dans
l'ordre suivant :

1. `runtime/issuing-connected-e2e/connected-e2e.env`, s'il existe ;
2. `runtime/platform-e2e/platform-e2e.env`, s'il existe ;
3. `runtime/platform-e2e/<domaine>.env`, s'il existe.

Les fichiers runtime restent ignores par Git. Aucun mot de passe, PAN/PIN de
recette ou cle claire n'est ajoute aux fichiers versionnes.

Materiel autorise disponible :

- WayPos : TAMK et TPMK de test triple longueur, KCV respectifs `51C71D` et
  `95B446` ;
- DMAS : KEK et MDK de LAB chargees en memoire pendant la campagne, KCV
  `2D617C` et `944A44` ;
- SWAM : ZMK de la ceremonie de test disponible localement, KCV `F6EE59` ;
- Visa : jeu de cles CEMEA VIP/VCMS double longueur disponible dans le PDF
  confidentiel local, Encryption BIN `434179`.

Ces valeurs font partie du contrat du `.env` local de livraison de test. Elles
ne sont jamais recopiees dans Git. Le sandbox Visa execute actuellement
autorisation, idempotence et Base II sans consommer encore le profil
cryptographique Visa ; cette limite reste explicite.

## Corrections revelees par la campagne

La premiere campagne globale n'a pas ete maquillee en succes. Elle a revele et
permis de corriger :

- les scripts DMAS utilisaient l'ancienne colonne carte `active` au lieu de
  `status='ACTIVE'` ;
- le simulateur Mastercard externe approuvait un DE55 sans valider l'ARQC : il
  valide maintenant l'ARQC, refuse un cryptogramme invalide et retourne l'ARPC
  dans le tag 91 ;
- la sonde DMCS appelait un endpoint Actuator absent : elle verifie maintenant
  la disponibilite HTTP de la route DMCS avant l'EOD authentifie ;
- les cartes sandbox SWAM n'etaient pas reprovisionnees a chaque campagne :
  le bootstrap SQL est desormais idempotent.

## Validations executees

### Compilation et tests cibles

- Maven `-pl sg-mc-dmas-mastercard -am test` : `BUILD SUCCESS` en 61 s ;
  `sg-common` 69 tests et `sg-mc-dmas-mastercard` 2 tests, aucun echec ;
- packaging du JAR DMAS corrige : `BUILD SUCCESS` en 15 s ;
- syntaxe Bash des scripts DMCS corriges : succes.

### Campagnes isolees finales

- DMAS/DMCS : `PASSED` en 119,6 s avec achat PIN, advice, reversal,
  ARQC/ARPC, EOD acquereur/issuer, idempotence et tables clearing alimentees ;
- SWAM/LIS : `PASSED` en 123 s avec dix achats bilateraux et
  `PASSED (30 controles)` pour SID, LIS, chargeback, representation et
  comptabilite.

### Campagne globale finale

Commande fonctionnelle executee avec les JAR valides et les cles de test
chargees uniquement en memoire :

```bash
PLATFORM_E2E_SKIP_BUILD=true \
  bash tests/platform-e2e/global/run-all.sh
```

Resultat exact en 694,9 secondes :

```text
issuing                 PASSED
acquiring               PASSED
three-ds                PASSED
mastercard-dmas-dmcs    PASSED
swam                    PASSED
visa                    PASSED
```

Preuves notables :

- Issuing : achat local `RC=00` ;
- Acquisition : achat TPE et achat e-commerce local `RC=00` ;
- 3DS : trois scenarios `AUTHENTICATED` et `RC=00` ;
- DMAS/DMCS : PIN, ARQC/ARPC et EOD idempotent des deux cotes ;
- SWAM : cinq achats dans chaque sens et 30 controles clearing passes ;
- Visa : Online `RC=00`, rejeu idempotent, Base II accepte avec cinq records.

Le bilan est dans `runtime/platform-e2e/global/summary.tsv`. La phase finale a
appele les scripts d'arret de tous les domaines.

## Limites preservees

- aucun vecteur PIN/ARQC WayPos reel n'a ete invente ;
- DE31/ARN DMCS reel reste bloque par la specification manquante ;
- le MAC avec le switch SWAM reel n'est pas assimile au sandbox local ;
- le transport Visa certifie, VROL et le clearing Visa complet restent hors du
  premier increment.

## Premier travail non termine

1. Sur la machine RECETTE, creer le `.env` local de livraison par canal
   securise et verifier les KCV avant execution.
2. Executer une premiere campagne sans `PLATFORM_E2E_SKIP_BUILD` afin de
   valider les JAR reconstruits sur la machine cible.
3. Archiver le bilan et les journaux sans inclure le `.env`, les LMK, les cles
   ou les donnees carte sensibles.

## Etat Git et processus

- branche : `codex/AddingVisaOnlineAndClearing` ;
- le commit de ce snapshot contient uniquement l'orchestrateur, les guides et
  les correctifs reveles par la campagne ; son identifiant est a consulter
  dans l'historique Git de la branche ;
- `keys/dmas-lmk.lmk`, `ZMK SWAM.txt`, les specifications reseau et les autres
  travaux historiques restent volontairement hors commit ;
- aucun port utilise par la campagne globale n'est reste en ecoute apres son
  arret ; deux processus Java preexistants ecoutent encore sur `8080` et
  `18081`, hors ports et hors PID de cette campagne, et n'ont pas ete touches.
