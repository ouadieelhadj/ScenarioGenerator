# Preuve de test — découplage Portal / Acquiring / WAY4

Date : 11 août 2026
Branche : `codex/AddingFrontendMerchantPortal`
Répertoire : `D:\MoneyCore\ScenarioGenerator`

## Architecture vérifiée

Deux parcours indépendants sont conservés :

1. `Portal Commerçant -> FuturPayment Acquiring` ;
2. `Portal Commerçant -> Connecteur WAY4/AURA -> XML -> WAY4 Acquiring`.

Le code d'export WAY4 a été retiré de `sg-acquiring`. L'approbation d'un
dossier v2 juridiquement complet crée deux événements d'outbox distincts,
avec des types et clés d'idempotence distincts. Un rejet WAY4 ne modifie pas
l'état du provisionnement Acquiring. Les dossiers v1, dépourvus du profil
juridique v2, restent sur leur parcours Acquiring historique et ne sont pas
complétés par des données inventées.

Le contrat Portal vers Connecteur transporte les données métier du
commerçant, du règlement, des PDV et des demandes TPE, avec un
`Application/RegNumber` stable. Il ne transporte ni identifiant client WAY4,
ni numéro de contrat commerçant, ni numéro de contrat TPE/TID.

Le XML produit contient un `ContractIDT` sans `ContractNumber`. Le TID doit
donc provenir du fichier retour WAY4. Le MID est alloué par le connecteur, mais
le mécanisme reste désactivé par défaut tant que les plages, préfixes et règles
AURA réels ne sont pas approuvés.

## Tests Java

Maven embarqué :
`D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd`.

Cache : `D:\MoneyCore\.m2\repository`.

Résultat consolidé des classes de test présentes dans les sources :

- `sg-common` : 77 tests ;
- `sg-acquiring` : 26 tests ;
- `sg-merchant-onboarding` : 30 tests ;
- `sg-way4-aura-connector` : 2 tests ;
- total : **135 tests réussis, 0 échec, 0 erreur, 0 ignoré**.

Les tests ajoutés vérifient notamment :

- la séparation des événements Acquiring et WAY4 ;
- le routage direct Portal vers Connecteur ;
- l'absence d'impact d'un rejet WAY4 définitif sur le dossier Acquiring ;
- la compatibilité des dossiers v1 sans profil juridique fictif ;
- la déterminisme du XML et l'absence de `ContractNumber` prérempli ;
- la validation du XML contre le XSD WAY4 réel.

La dernière compilation après alignement du scope OAuth2
`SCOPE_way4.generate` s'est terminée par `BUILD SUCCESS`.

XSD principal :
`D:\LanaCash\OpenWay\installationOCI\chargementxmlway4\schemas\xsd\xsd\offline\WAY4ApplFile.xsd`.

SHA-256 :
`F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`.

## PostgreSQL réel

PostgreSQL 18 local a été contrôlé sur le port 5432. Une sauvegarde a été
prise avant migration :
`runtime/merchant-portal-e2e/backups/before-portal-direct-way4-20260811-104811.dump`
(687985 octets).

Migrations appliquées dans des transactions avec arrêt sur erreur :

- `sql/merchant-onboarding/V7__direct_portal_way4_export_state.sql` ;
- `sql/way4-aura/V2__portal_direct_mid_and_return_preparation.sql`.

Résultats :

- première application : OK ;
- second passage/rejeu : OK ;
- dossiers commerçants avant/après : 16 / 16 ;
- événements d'outbox avant/après : 0 / 0 ;
- états WAY4 créés artificiellement : 0 ;
- allocations MID créées artificiellement : 0 ;
- doublons de `RegNumber` : 0 ;
- doublons MID : 0 ;
- états orphelins : 0 ;
- doublons de clé d'idempotence WAY4 : 0.

## Limites et décision de recette

Aucun import WAY4 et aucune recette E2E positive n'ont été lancés. La lecture
du fichier retour et le rappel sécurisé vers le Portal restent soumis au
format/canal de retour WAY4 réel et aux habilitations OAuth2 approuvées. La
table Portal est prête à stocker l'identifiant client WAY4, le numéro de
contrat commerçant, le MID, les TID et le nom du fichier retour.

Les blocages déjà signalés restent applicables : réconciliation du commerçant
actif sans PDV, bindings AURA réels, règles MID approuvées, identifiants OAuth2
de recette, canal d'import et mécanisme de retour WAY4. Cette preuve valide le
développement et la préparation technique ; elle ne constitue pas un GO
formel de recette ou de production.
