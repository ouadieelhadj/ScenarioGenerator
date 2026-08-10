# Proof of Test - Portal Commercant - Increment 1

Date : 10 aout 2026
Branche : `codex/AddingFrontendMerchantPortal`
Session Codex : `019fd736-217b-7560-8fe4-44efbe97bcda`

## Perimetre

- modele juridique `PP`, `PM`, `AE`, association et fondation ;
- profil legal, representant et beneficiaires effectifs ;
- adresse structuree du siege ;
- PDV structures, multi-PDV et exactement un principal actif ;
- adaptation v1 sans duplication du PDV historique ;
- API publique v2 en lecture/ecriture avec maintien du contrat v1 ;
- referentiels minimaux pays, MCC et regles de champs ;
- migrations additives Merchant Onboarding V3 et Acquiring V4.

L'increment 2 (produits par PDV, demandes TPE et boutiques e-commerce) n'a pas
ete commence.

## Preflight

| Prerequis | Resultat |
|---|---|
| Maven embarque | Disponible |
| Cache Maven `D:\MoneyCore\.m2\repository` | Disponible |
| Runtime Java | Disponible |
| Services 8550/8570 pour les tests unitaires | Non requis, ports libres |
| Secrets pour les tests unitaires/H2 | Non requis |
| PostgreSQL local, port 5432 | Disponible, PostgreSQL 18 demarre par `pg_ctl` |
| Installation PostgreSQL | `D:\MoneyCore\PostgreSQL\18` |
| Base de recette | `scenariogenerator`, acces valide |
| Sauvegarde avant migration | `before-v3-v4.dump`, 607683 octets |
| Secrets | Lus en memoire depuis la configuration locale, jamais consignes dans la preuve |

## Resultats executes

### Porte ciblee increment 1

```text
mvn.cmd -o -nsu -f pom.xml -pl sg-acquiring,sg-merchant-onboarding -am test
-Dtest=MerchantLegalModelIncrement1Test,MerchantOnboardingIncrement1Test,
MerchantOnboardingApiCompatibilityTest,Increment1MigrationContractTest
-Dsurefire.failIfNoSpecifiedTests=false
-Dmaven.repo.local=D:\MoneyCore\.m2\repository

BUILD SUCCESS
15 tests, 0 echec, 0 erreur, 0 ignore
```

### Non-regression agregee finale

```text
mvn.cmd -o -nsu -f pom.xml -pl sg-acquiring,sg-merchant-onboarding -am test
-Dmaven.repo.local=D:\MoneyCore\.m2\repository

BUILD SUCCESS
sg-common: 77 tests, 0 echec, 0 erreur, 0 ignore
sg-acquiring: 21 tests, 0 echec, 0 erreur, 0 ignore
sg-merchant-onboarding: 23 tests, 0 echec, 0 erreur, 0 ignore
TOTAL: 121 tests, 0 echec, 0 erreur, 0 ignore
```

Un premier passage agrege a rencontre un timeout local de deux secondes dans
`HttpServerPosProvisioningAdapterTest`. Le test a ete rejoue seul avec succes
(3/3), puis la campagne agregee finale l'a rejoue avec succes. Il ne s'agissait
pas d'un echec fonctionnel du nouvel increment.

### Validation PostgreSQL V3/V4

Les deux scripts ont ete executes avec `psql -X -w -1 -v ON_ERROR_STOP=1` :
chaque fichier est applique dans une transaction unique et toute erreur est
bloquante.

Requetes de controle reproductibles :
`tests/merchant-onboarding/sql/increment1-postgresql-gate.sql`.

| Operation | Resultat |
|---|---|
| Sauvegarde `pg_dump -Fc` avant modification | SUCCESS |
| Premiere application Merchant Onboarding V3 | SUCCESS |
| Premiere application Acquiring V4 | SUCCESS |
| Rejeu Merchant Onboarding V3 | SUCCESS |
| Rejeu Acquiring V4 | SUCCESS |

Comptages et empreintes anonymisees :

| Objet | Avant | Apres premiere application | Apres rejeu |
|---|---:|---:|---:|
| `merchant_onboarding_case` | 16 | 16 | 16 |
| Empreinte `merchant_onboarding_case` | `ea7321e6112c2ee8ebccce41d221cfb9` | identique | identique |
| `merchant` | 13 | 13 | 13 |
| Empreinte `merchant` | `4ff8599f3f4a71e6c03132447fe7be66` | identique | identique |
| `merchant_outlet` | 12 | 12 | 12 |
| Empreinte `merchant_outlet` | `f32153b757dd80f015b866efa4518881` | identique | identique |
| `onboarding_outlet` | absent | 16 | 16 |
| `legacy_outlet_migration` | absent | 16 | 16 |
| Nouvelles tables increment 1 | 0 | 9 | 9 |

Resultats des requetes d'integrite :

| Requete de controle | Premiere application | Rejeu |
|---|---:|---:|
| UUID de mapping distincts | 16/16 | 16/16 |
| Copie historique differente de la source | 0 | 0 |
| Doublons `(case_id, outlet_code)` | 0 | 0 |
| Dossiers Onboarding sans exactement un principal actif | 0 | 0 |
| Commercants Acquiring actifs sans exactement un principal actif | 1 | 1 |
| Commercants Acquiring actifs sans aucun PDV | 1 | 1 |

Le premier `migration_run` contient `source=16`, `created=16`, `ignored=0`,
`errors=0`, statut `COMPLETED`. Le rejeu contient `source=16`, `created=0`,
`ignored=16`, `errors=0`, statut `COMPLETED`. Il n'a cree aucun doublon.

Anomalie bloquante detectee par la validation : le commercant
`885d1af8-2f05-465a-832a-6a91ae613da3` est `ACTIVE`, possede un contrat et une
boutique e-commerce, mais aucun PDV. Aucun dossier Onboarding ni recu de
provisioning ne fournit une adresse et des contacts permettant un backfill
deterministe. Aucun PDV fictif n'a ete cree.

## Corrections de reprise

La reprise a detecte et corrige deux pertes silencieuses :

- le RIB etait persiste mais absent de la reponse API v2 ;
- la date de naissance et les informations d'identite du responsable de PDV
  etaient acceptees par l'API mais non persistees/restituees.

Le format d'erreur enrichi v2 a aussi ete isole des controleurs v1 : v1
conserve exactement `{ "error": ... }`, tandis que v2 expose en plus un code
d'exigence et un chemin de champ stables.

## Matrice de tracabilite de l'increment 1

| Exigence | Migration / persistance | Code / API | Test | Resultat |
|---|---|---|---|---|
| MER-001 | V3 `merchant_type`, `organization_legal_nature`; V4 equivalents | `MerchantType`, `OrganizationLegalNature`, API v2 | cinq variantes juridiques | VALIDE LOCAL |
| MER-002 | `onboarding_field_rule` | validation dynamique par type | regle obligatoire absente refusee | VALIDE LOCAL |
| MER-003 | V3/V4 `tax_identifier`, `ice` | profil legal distinct du RC | profil PM et Acquiring | VALIDE LOCAL |
| MER-004 | V3/V4 representant structure | dossier, entite Acquiring et API v2 | persistance/restitution representant | VALIDE LOCAL |
| MER-005 | tables beneficiaires Portal/Acquiring | collection historisee par activation | creation et mise a jour | VALIDE LOCAL |
| MER-006 | V3/V4 forme, activite, objet, contacts | validations par type | PP/PM/AE/association/fondation | VALIDE LOCAL |
| MER-007 | V3/V4 `rib` distinct | normalisation sans regle bancaire inventee | stockage et restitution v2 | VALIDE LOCAL |
| ADR-001 | colonnes d'adresse structuree | siege et PDV, pays reference | round-trip multi-PDV | VALIDE LOCAL |
| PDV-001 | table `onboarding_outlet` | collection 1..n | deux PDV persistants | VALIDE LOCAL |
| PDV-002 | index unique principal actif | controle service exactement un | Onboarding 0 anomalie; Acquiring 1 commercant actif sans PDV | BLOQUE DONNEE REELLE |
| PDV-003 | UUID, code unique, statut, adresse | creation, modification, desactivation logique | historique conserve | VALIDE LOCAL |
| PDV-004 | contacts et responsable structures | persistance et restitution sans perte | round-trip responsable | VALIDE LOCAL |
| REF-001 | tables referentiels et regles | API `/v2/references` | regle inactive/absente refusee | VALIDE LOCAL |
| REF-002 | categorie MCC | recherche code/libelle et controle actif | MCC inconnu refuse | VALIDE LOCAL |
| MIG-001 | `migration_run`, `legacy_outlet_migration` | UUID persiste, backfill additif | application + rejeu PostgreSQL | VALIDE, 0 DOUBLON |
| MIG-002 | V3 + adaptateur v1 | `/v1` conserve, `/v2` ajoute | contrats payload/erreurs et non-duplication v1 | VALIDE LOCAL |

## Porte restante

La premiere application, le rejeu, les comptages et l'absence de perte ou de
doublon sont prouves. La porte restante est la reconciliation du commercant
Acquiring actif sans PDV a partir d'une donnee metier reelle validee. Il est
interdit d'inventer son adresse, ses contacts ou un PDV technique.

Verdict actuel : **NO-GO increment 2 : une anomalie PDV Acquiring reelle reste
a reconcilier.**

## Ecart de processus et action corrective

Ecart constate : les 121 tests Java ont ete communiques comme une validation
de l'increment avant l'execution de la porte PostgreSQL reelle. Ils validaient
le code et les contrats, mais ne pouvaient pas prouver la qualite des donnees
existantes ni l'invariance metier sur la base de recette.

Action corrective permanente : aucun increment contenant une migration de
donnees ne sera declare valide sur la seule base des tests Java. La preuve
complete devra inclure PostgreSQL reel, application initiale, rejeu,
comptages/empreintes, absence de perte et de doublon, invariants metier, puis
un verdict explicite. Une campagne complete sera rejouee apres reconciliation
du PDV reel ; jusque-la, le resultat demeure `NO-GO`.
