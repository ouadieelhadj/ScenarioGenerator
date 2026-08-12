# Preuve ciblee - generation de lot XML WAY4

Date : 12 aout 2026

## Perimetre valide

- selection manuelle de plusieurs commercants en attente dans le Portal ;
- generation d'un seul `ApplicationFile` WAY4 contenant plusieurs commercants ;
- validation contre le XSD AURA reel ;
- ecriture atomique et rejeu idempotent sur disque ;
- recyclage des erreurs de generation ;
- affichage et relance des echecs finaux FuturPayment par l'outbox existante.

L'import et le traitement du fichier retour WAY4, la soumission dans WAY4 et
la recette E2E reelle sont hors de ce perimetre.

## Artefact XML

Fichier :
`D:\MoneyCore\ScenarioGenerator\tests\merchant-onboarding\artifacts\way4-batch-generation\FP_WAY4_0000000007_TECHNICAL_PROOF.xml`

- taille : 8 478 octets ;
- SHA-256 : `93FCFA23BA689155268813D6FC3B1F1F68074128E14B636180316E07235B3FDD` ;
- applications client de premier niveau : 2 ;
- RegNumber techniques : `PORTAL-FIRST`, `PORTAL-SECOND` ;
- `ContractNumber` absent, conformement au parcours cible ;
- validation XSD : reussie.

Cet artefact utilise exclusivement des donnees techniques de test explicites.
Il prouve le format multi-commercants et ne doit pas etre importe comme un lot
de recette ou de production.

## Resultats techniques

- test cible de materialisation XML : 1 test, 0 echec, 0 erreur ;
- suite cible connecteur : 5 tests, 0 echec, 0 erreur ;
- suite cible Portal/orchestration : 6 tests, 0 echec, 0 erreur ;
- build Angular `merchant-portal-web` : reussi.

Le XSD principal utilise est
`D:\LanaCash\OpenWay\installationOCI\chargementxmlway4\schemas\xsd\xsd\offline\WAY4ApplFile.xsd`,
empreinte attendue
`F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`.

## TPE et initialisation cryptographique

Le XML contient deux contrats de terminal avec `DeviceInfo`, `DeviceRecord` et
`DeviceType=POS`. Il couvre donc la demande de creation des objets TPE par
WAY4, sous reserve de la validite des mappings AURA de l'environnement cible.

Le fichier ne contient aucune valeur ou reference TAMK, TPMK, TAK ou TPK. Le
XSD inspecte propose une structure generique optionnelle `DeviceKeys`, absente
de l'artefact, mais aucun champ explicitement nomme TAMK ou TPMK. La creation
du TPE ne prouve donc pas l'affectation des cles maitres terminales. La methode
reelle doit etre confirmee par l'expert WAY4 : profil de cles gere par WAY4/HSM
ou procedure RKI separee. Aucune cle claire ne doit transiter dans ce XML.
