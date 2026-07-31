# Reprise DMCS / DMAS

Dernière mise à jour : 29 juillet 2026

## 1. Objectif

Finaliser une livraison portable et testable sur une autre machine pour :

- `sg-mc-dmas-member` : autorisation DMAS côté membre ;
- `sg-mc-dmas-mastercard` : autorisation DMAS côté Mastercard ;
- `sg-dmcs-acquirer` : clearing DMC côté membre/acquéreur ;
- `sg-dmcs-issuer` : clearing DMC côté Mastercard/issuer simulé.

Références normatives :

- DMAS/CIS :
  `documents/specifications/mastercard/dmas/m_DMAS_en-us-2025-11-04.pdf` ;
- DMC :
  `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.pdf` ;
- texte DMC :
  `documents/specifications/mastercard/dmc/m_DMC_guide_en-us.txt`.

Traçabilité cible :

```text
règle/champ DMAS
  -> journal d'autorisation propriétaire
  -> batch EOD propriétaire
  -> table clearing propriétaire
  -> mapping DMC
  -> champ ISO/IPM ou PDS
```

## 2. État confirmé

- Branche : `codex/portail-rbac-maker-checker`.
- Premier jalon publié : commit `4d8336d`, tag
  `ValidationDmcsDmasFirst`, branche poussée sur `origin`.
- Le socle DMC séparé est développé :
  - module `sg-dmcs-common` ;
  - packager jPOS ISO 8583:1993 ;
  - données et préfixes de longueur EBCDIC ;
  - bitmap binaire ;
  - codec RDW/VBS ;
  - enveloppe `1644/697 -> 1240/200 -> 1644/695` ;
  - PDS 0105, 0122, 0301 et 0306 ;
  - validation DE71, File ID, checksum et nombre de messages.
- Les journaux d'autorisation sont séparés :
  - `mc_dmas_member_transactions` ;
  - `mc_dmas_issuer_transactions`.
- Les tables de clearing sont séparées :
  - `dmcs_acquirer_clearing_transactions` ;
  - `dmcs_issuer_clearing_transactions`.
- DE52/PIN n'est pas persisté dans les journaux de clearing.
- Les deux batchs EOD sont conçus pour être idempotents.
- Les anciens traitements DMCS texte sont désactivés par défaut.
- La matrice de conformité existe dans :
  `conceptions/mastercard/clearing/MATRICE_CONFORMITE_DMAS_DMC.md`.
- La livraison portable existe dans :
  `deploiement/mastercard/dmas-dmc/`.
- Validation du 29 juillet 2026 :
  - syntaxe des 14 scripts Bash : OK ;
  - `git diff --check` : OK ;
  - build Maven ciblé des 7 modules : `BUILD SUCCESS` ;
  - après l'incrément reversal/advice : 19 tests réussis, 0 échec ;
  - après la factory de litige sortant : 23 tests réussis, 0 échec ;
  - après les endpoints et fichiers de litige complets : build ciblé réussi,
    26 tests réussis, 0 échec.

## 3. Scripts de livraison déjà présents

```text
deploiement/mastercard/dmas-dmc/
├── 00-install-database.sh
├── 01-start-mastercard.sh
├── 02-start-member.sh
├── 03a-bootstrap-mastercard.sh
├── 03b-bootstrap-member.sh
├── 03c-signon-and-key-exchange.sh
├── 04-test-pin.sh
├── 05-test-emv-arqc-arpc.sh
├── 06-start-dmcs.sh
├── 07-run-dmc-eod.sh
├── 08-stop-dmas-dmc.sh
├── dmas-dmc-e2e.sh
├── lib-dmas-dmc.sh
└── README.md
```

Ces scripts ne doivent jamais tuer tous les processus Java. Ils doivent arrêter
uniquement les quatre modules identifiés par PID, ligne de commande et port.

## 4. Tâches de reprise, dans l'ordre

- [x] Retrouver l'état Git et les changements interrompus.
- [x] Vérifier la syntaxe Bash de la livraison portable.
- [x] Relancer le build et les tests ciblés.
- [ ] Finaliser les reversals et advices :
  - [x] confirmer que les endpoints et services DMAS historiques existent ;
  - [ ] vérifier les MTI, DE39, DE60 et DE90 avec la spécification DMAS ;
  - [x] alimenter correctement les deux journaux DMAS propriétaires ;
  - [x] annuler l'éligibilité clearing de l'autorisation d'origine ;
  - [x] rendre l'advice et le reversal idempotents dans le code ;
  - [x] ajouter les tests unitaires et le script E2E ;
  - [ ] exécuter le script E2E avec les secrets synthétiques de LAB/DEV.
- [ ] Finaliser les chargebacks et secondes présentations sortants :
  - [x] factory First Chargeback `1442/450` et `1442/453` ;
  - [x] factory Second Presentment `1240/205` et `1240/282` ;
  - [x] modèle enrichi pour DE5, DE9, DE25, DE30, DE50 et DE95 ;
  - [x] lien parent prévu par `parent_transaction_id` ;
  - [x] contrôles de cycle, montant, motif, DE31, DE95 et PDS 0148/0149 ;
  - [x] services propriétaires et endpoints sortants ;
  - [x] création des fichiers avec header/trailer et numérotation DE71 ;
  - [ ] tests E2E bilatéraux.
- [ ] Finaliser reconciliation, frais, change et settlement :
  - [ ] messages de reconciliation `1644` ;
  - [ ] Fee Collection `1740` ;
  - [ ] devises, taux et montants de reconciliation ;
  - [ ] positions et dates de settlement ;
  - [ ] contrôles de totaux et écritures propriétaires.
- [ ] Durcir puis exécuter l'E2E réel :
  - [ ] PIN et DE52 uniquement en ligne ;
  - [ ] ARQC recalculé et validé ;
  - [ ] ARPC présent dans le tag 91 de DE55 ;
  - [ ] reversals/advices ;
  - [ ] EOD acquirer et issuer ;
  - [ ] idempotence et séparation propriétaire.
- [ ] Terminer le contrôle visuel des pages normatives DMAS et DMC citées dans
      la matrice.
- [ ] Vérifier chaque endpoint utilisé par les scripts de livraison.
- [ ] Renforcer les tests E2E pour contrôler les messages et les données en
      base, pas uniquement des chaînes présentes dans les logs.
- [ ] Vérifier la portabilité sur Git Bash/Windows :
      chemins, PID, ports, logs, JAR et LMK.
- [ ] Vérifier que les migrations V6/V7 sont idempotentes et que les droits des
      quatre rôles PostgreSQL respectent la séparation propriétaire.
- [ ] Exécuter les scripts `00` à `08` un par un sur LAB/DEV.
- [ ] Exécuter `dmas-dmc-e2e.sh`.
- [ ] Tester PIN :
      DE52 présent uniquement en ligne et absent des journaux.
- [ ] Tester EMV :
      ARQC recalculé/validé et ARPC retourné dans le tag 91 de DE55.
- [ ] Tester l'EOD des deux côtés :
      tables propriétaires alimentées et second passage sans doublon.
- [ ] Corriger les anomalies sans contourner les contrôles.
- [ ] Mettre à jour la matrice, le README et cette note avec les résultats.
- [ ] Relancer le build global du projet et les non-régressions pertinentes.
- [ ] Contrôler le périmètre exact avant tout `git add`.
- [ ] Commit et push uniquement après validation explicite du périmètre.

## 5. Limites connues à ne pas masquer

- DE31/ARN reste volontairement différé. Aucune valeur fictive ne doit être
  créée pour faire passer un test.
- Un First Presentment outgoing complet reste donc bloqué tant que la règle
  DE31 n'est pas validée.
- Restent aussi ouverts :
  - PDS conditionnels EMV/e-commerce/produit ;
  - reversals et advices complets ;
  - chargeback sortant ;
  - seconde présentation sortante ;
  - erreurs et rejets ;
  - reconciliation ;
  - frais, change, settlement et comptabilisation ;
  - golden files et E2E IPM bilatéral complet.

Le premier incrément ne doit pas être présenté comme une conformité Mastercard
complète tant que ces éléments restent ouverts.

## 5.1 Ordre de développement validé le 29 juillet 2026

L'ordre suivant est demandé et validé :

1. reversals et advices ;
2. chargeback et seconde présentation sortants ;
3. reconciliation, frais, change et settlement ;
4. E2E réel PIN-ARQC-ARPC-EOD ;
5. DE31/ARN conservé en attente, sans valeur fictive.

Constat au démarrage de cet incrément :

- des contrôleurs et services historiques DMAS d'advice et de reversal existent
  déjà ;
- le packager DMAS reconnaît notamment les MTI d'advice et de reversal ;
- les nouveaux journaux propriétaires possèdent déjà les colonnes
  `reversed` et `reversed_at` ;
- il reste à prouver puis compléter le lien entre les messages
  reversal/advice, les journaux propriétaires et l'éligibilité au clearing ;
- aucun résultat E2E reversal/advice n'est encore validé dans cette reprise.

Résultat du premier incrément reversal/advice :

- le DE90 est parsé strictement comme une valeur numérique de 42 positions :
  MTI(4), STAN(6), DE7(10), DE32(11), DE33(11) ;
- un `0400/0410` ou `0420/0430` approuvé marque l'autorisation originale
  `reversed=true` et `clearing_eligible=false` dans les deux journaux ;
- un second reversal accepté ne recrédite pas une seconde fois et conserve le
  même état de journal ;
- un advice `0120/0130` approuvé est journalisé des deux côtés ;
- une completion `0120/0130` est éligible au clearing, contrairement à la
  préautorisation `0100` d'origine ;
- le nouveau script
  `deploiement/mastercard/dmas-dmc/04a-test-advice-reversal.sh`
  contrôle les deux journaux et le double reversal ;
- validation locale du code :
  - syntaxe de tous les scripts Bash : OK ;
  - build Maven ciblé : OK ;
  - 19 tests réussis, 0 échec ;
- l'E2E connecté reste à exécuter avec les secrets synthétiques saisis
  uniquement dans le terminal.

Résultat du premier incrément chargeback/seconde présentation :

- contrôle visuel effectué sur les pages DMC 158-159 et 164-165 ;
- ces pages confirment :
  - First Chargeback : MTI `1442`, DE24 `450` ou `453` ;
  - Second Presentment : MTI `1240`, DE24 `205` ou `282` ;
  - DE25 contient le motif ;
  - DE30, DE31, DE43, DE48 et DE95 sont obligatoires ;
- ajout de `DmcDisputeMessageFactory` ;
- le mode Full exige DE4 égal au montant original de DE30 ;
- le mode Partial exige DE4 strictement inférieur au montant original ;
- PDS 0148 et 0149 sont exigés quand DE30 est présent ;
- une génération sans DE31 réel ou sans DE95 réel est rejetée ;
- la migration V7 est rétrocompatible avec les tables déjà créées grâce aux
  `ADD COLUMN IF NOT EXISTS` ;
- endpoint issuer
  `POST /api/dmcs/disputes/first-chargebacks` ajouté pour `1442/450|453` ;
- endpoint acquirer
  `POST /api/dmcs/disputes/second-presentments` ajouté pour `1240/205|282` ;
- chaque service exige une transaction parente du bon cycle, persiste le lien
  `parent_transaction_id` et construit un fichier RDW complet
  `1644/697 -> litige -> 1644/695` avec DE71 et totaux contrôlés ;
- DE31 provient obligatoirement du parent réel et DE95 est fourni ou hérité :
  aucune référence n'est synthétisée ;
- build Maven ciblé des quatre modules : OK, 26 tests réussis, 0 échec ;
- l'E2E bilatéral connecté de ce cycle reste à exécuter.

## 6. Secrets et données sensibles

Ne jamais écrire dans Git, un rapport ou une conversation :

- `DB_PASSWORD` ;
- `DMAS_ADMIN_PASSWORD` ;
- `DMAS_KEK_CLEAR` ;
- `DMAS_MDK_CLEAR` ;
- `DMAS_TEST_PIN` ;
- LMK, ZMK/KEK ou clés réelles ;
- données de carte réelles.

Les clés claires sont autorisées uniquement si elles sont synthétiques et
dédiées à LAB/DEV ou RECETTE.

## 7. Commandes de reprise

Depuis PowerShell :

```powershell
cd D:\MoneyCore\ScenarioGenerator
git status --short --branch
git diff --check
```

Validation de la syntaxe depuis PowerShell avec Git Bash :

```powershell
& 'D:\Program Files\Git\bin\bash.exe' -n `
  deploiement/mastercard/dmas-dmc/*.sh
```

Build ciblé :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -f pom.xml `
  -pl sg-common,sg-dmcs-common,sg-mc-dmas-member,sg-mc-dmas-mastercard,sg-dmcs-acquirer,sg-dmcs-issuer `
  -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Exécution pas à pas depuis Git Bash, après configuration locale :

```bash
source deploiement/common/runtime/platform-env.sh
bash deploiement/mastercard/dmas-dmc/00-install-database.sh
bash deploiement/mastercard/dmas-dmc/01-start-mastercard.sh
bash deploiement/mastercard/dmas-dmc/02-start-member.sh
bash deploiement/mastercard/dmas-dmc/03a-bootstrap-mastercard.sh
bash deploiement/mastercard/dmas-dmc/03b-bootstrap-member.sh
bash deploiement/mastercard/dmas-dmc/03c-signon-and-key-exchange.sh
bash deploiement/mastercard/dmas-dmc/04-test-pin.sh
bash deploiement/mastercard/dmas-dmc/05-test-emv-arqc-arpc.sh
bash deploiement/mastercard/dmas-dmc/06-start-dmcs.sh
bash deploiement/mastercard/dmas-dmc/07-run-dmc-eod.sh
bash deploiement/mastercard/dmas-dmc/08-stop-dmas-dmc.sh
```

Scénario complet :

```bash
bash deploiement/mastercard/dmas-dmc/dmas-dmc-e2e.sh
```

## 8. Première action de toute nouvelle session

Lire ce fichier, puis :

1. exécuter `git status --short --branch` ;
2. ne supprimer ni écraser aucun changement local ;
3. vérifier si un processus de build ou un module DMAS/DMCS tourne encore ;
4. reprendre à la première case non cochée de la section 4 ;
5. mettre à jour ce fichier avant de terminer la session.

## Raccordement Issuing du 2026-07-31

- `sg-mc-dmas-mastercard` délègue maintenant la décision d'autorisation au
  cœur `sg-card-issuing`.
- La cible REST est l'endpoint actif `DMAS` du registre Issuing en base.
- Le protocole EBCDIC/ISO, les contrôles de session/MAC et le journal DMAS
  restent propriétaires du module DMAS.
- Un test d'adaptateur DMAS passe sans échec. Les advices/reversals et l'E2E
  bilatéral connecté restent non terminés.
