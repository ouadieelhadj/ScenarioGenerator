# Preuve de test - Merchant Portal increments 2 a 5

Date : 10 aout 2026

## Perimetre

- Merchant Onboarding : multi-PDV, produits, demandes TPE/e-commerce, outbox,
  referentiels et tarification.
- FuturPayment Acquiring : provisionnement v2 par objet, OAuth2 et export WAY4.
- WAY4/AURA : bindings versionnes, generation XML deterministe, validation XSD
  et persistance des empreintes. La soumission reelle reste desactivee.

## Environnement et securite

- Profil : `runtime/merchant-portal-e2e/.env` (ignore par Git).
- Source DB : `runtime/platform-e2e/platform-e2e.env`.
- Les valeurs secretes n'ont ete ni affichees, ni dupliquees, ni consignees.
- Sauvegarde avant migration :
  `runtime/merchant-portal-e2e/backups/before-increments-2-5-20260810-171312.dump`
  (632751 octets).

## Tests Java

Resultats des rapports Surefire :

- `sg-common` : 77 tests, 0 echec, 0 erreur ;
- `sg-acquiring` : 28 tests, 0 echec, 0 erreur ;
- `sg-merchant-onboarding` : 27 tests, 0 echec, 0 erreur ;
- `sg-way4-aura-connector` : 2 tests, 0 echec, 0 erreur.

Total : **134 tests, 0 echec, 0 erreur, 0 ignore**.

Les tests du connecteur verifient le determinisme et la validation contre le
XSD reel et ses dependances.

## PostgreSQL reel

La premiere application des migrations a expose l'absence de V4 Onboarding
sur cette instance. La chaine a ete reprise dans l'ordre. Le premier rejeu a
ensuite revele un defaut d'idempotence du seed V4 apres ajout des colonnes
obligatoires V6. Le seed a ete corrige pour ne tenter aucune insertion lorsque
la reference existe deja.

Apres correction, deux passages consecutifs des six fichiers suivants ont
reussi avec `ON_ERROR_STOP=1` et une transaction par fichier : Merchant
Onboarding V4/V5/V6, Acquiring V5/V6 et WAY4/AURA V1.

La sauvegarde a ete restauree dans une base temporaire isolee, puis toutes les
tables `public` ont ete comparees avant/apres :

- tables avant : 135 ;
- tables apres : 153 ;
- tables existantes ayant perdu des lignes : 0 ;
- table existante modifiee : `onboarding_reference_value`, 8 vers 17 ;
- nouvelles tables : 18 ;
- doublons de cle d'idempotence Onboarding : 0 ;
- doublons de cle d'idempotence export WAY4 : 0 ;
- doublons de code PDV actif : 0.

La base temporaire `sg_portal_proof_20260810_171312` a ete supprimee apres la
comparaison. La sauvegarde reste disponible pour restauration.

## Porte metier restante

- commercants actifs : 13 ;
- commercants actifs sans PDV actif : 1 ;
- commercants actifs sans exactement un PDV principal actif : 1 ;
- dossiers Onboarding sans exactement un PDV principal actif : 0 ;
- anomalies Acquiring non reconciliees : 1 ;
- bindings AURA actifs : 0 ;
- autorites MID/TID : `UNDECIDED` ;
- configuration OAuth2 de recette trouvee dans les fichiers runtime : aucune.

Aucune donnee fictive n'a ete creee. Ces absences interdisent une recette
positive Portal -> Acquiring -> WAY4 et un GO formel. Le code et les migrations
sont prets pour une recette ciblee des que les donnees reelles du PDV, les
bindings AURA approuves, les decisions d'autorite MID/TID et l'issuer OAuth2 de
recette sont disponibles.
