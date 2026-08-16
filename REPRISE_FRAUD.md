# Reprise - FuturPayment Fraud Monitoring

## État au 15 août 2026

Le développement local est consolidé dans `D:\MoneyCore\ScenarioGenerator`. La documentation produit reste dans `D:\LanaCash\OpenWay\installationOCI\cloud-solution-simulateurs-switch-membre\07-fraud-monitoring`.

## Modules et noms produits

- `sg-fraud-platform` : moteur central, nom produit **sg-fraud-CloudInhouse** ;
- `sg-fraud-bank-gateway` : gateway ISO/API, déployable comme **sg-fraud-BanqueSide** ;
- espace Switch : `/product/fraud-monitoring` ;
- espace SwitchLab : `/lab/fraud-monitoring`.

## Fonctions livrées

- PostgreSQL et isolation par `member_id` issu du JWT ;
- Card Monitoring Enrollment par référence tokenisée et hachée ;
- scoring explicable 0–1000, raisons et version du modèle ;
- mode obligatoire `ALERT_ONLY`, sans blocage de transaction ;
- alertes, dossiers, feedback analyste et signaux de veille ;
- backtest de contrôles candidats et batch laboratoire ;
- API REST `/api/fraud/v1/**` ;
- liaison ISO 8583 TCP permanente en mode serveur ou client ;
- sign-on, echo périodique, reconnexion et test de maintien de session ;
- échange TAK sous ZMK : référence HSM, enveloppe chiffrée, KCV, import et accusé ;
- proxies BFF Switch/SwitchLab et vues réelles alertes/dossiers ;
- absence de capacité fictive si la plateforme n’est pas configurée.

## Fichiers principaux

- migration : `sql/22_fraud_platform.sql` ;
- service : `sg-fraud-platform` ;
- connecteur : `sg-fraud-bank-gateway` ;
- frontend partagé : `sg-frontend/src/app/features/fraud-workspace/fraud-workspace.component.ts` ;
- preuve finale : `tests/fraud/PROOF_OF_TEST_FRAUD_MONITORING_2026-08-15.md`.

## Validation exacte

- campagne Maven consolidée : 93 tests réussis, zéro échec et `BUILD SUCCESS` ;
- 7 tests `sg-fraud-platform`, tous réussis ;
- 7 tests `sg-fraud-bank-gateway`, tous réussis ;
- 2 tests BFF, tous réussis ;
- build Angular Switch réussi ;
- build Angular SwitchLab réussi ;
- PostgreSQL 18 réel et isolé sur loopback : 7 tables, validation JPA, santé `UP` ;
- jPOS réel : sign-on et echo sur la même connexion TCP ;
- `git diff --check` sans erreur, hors avertissements LF/CRLF connus.

## Sécurité et limites

- aucun PAN, PIN, CVV, mot de passe ou clé claire ne doit être stocké ou journalisé ;
- le PAN brut est refusé par l’API et le mapping ISO ;
- le port HSM ne manipule que des références et des enveloppes chiffrées ;
- l’adaptateur du HSM réel du client n’est pas inventé : l’implémentation échoue explicitement tant qu’il n’est pas branché ;
- l’adaptateur de repli est conditionnel et cède automatiquement la place à l’adaptateur HSM du client ;
- les services SWAM/DMAS de `sg-common` sont réutilisés comme référence d’architecture, mais leur API de simulation à clés claires ne doit pas être branchée telle quelle en production ;
- les codes de fonction, champs privés, packager, TLS/VPN et délais ISO restent configurables et doivent être validés avec le client ;
- aucune campagne à plusieurs millions de transactions et aucune performance IA ne sont revendiquées ;
- le baseline explicable et le pipeline de candidats sont livrés, l’apprentissage industriel doit être calibré sur les données labellisées du POC.

## Statut

GO développement local. NO-GO production jusqu’à validation du profil ISO client, raccordement HSM, certificats/TLS ou VPN, secrets OAuth2 et campagne POC.

## Prochaine action

Obtenir le profil ISO et les paramètres HSM non secrets du premier client pilote, implémenter l’adaptateur constructeur correspondant, puis lancer la recette d’intégration sans données sensibles.

## Adaptation Risk Intelligence lot 1 — 15 août 2026

La plateforme a été adaptée au cadrage commercial validé : snapshots de caractéristiques versionnés, graphe relationnel PostgreSQL, détection collective isolée par membre, score collectif expliqué et politique de décision gouvernée. `ALERT_ONLY` reste le mode initial ; `CHALLENGE`, `HOLD` et `BLOCK` ne deviennent effectifs qu'après activation explicite pour le membre concerné.

La Gateway est maintenue comme frontière obligatoire. Elle accepte le profil ISO permanent et une entrée canonique REST/agent, corrèle les réponses et restitue score, recommandation et décision appliquée sur le canal d'origine. Un générateur laboratoire couvre retrait, TPE, e-commerce, virement mobile et groupe coordonné ; il est désactivé par défaut et n'utilise ni PAN ni routage externe.

Les interfaces Switch et SwitchLab exposent la politique de décision et les scénarios via des BFF séparés vers la plateforme et la Gateway. La campagne consolidée compte **97 tests réussis** : 77 `sg-common`, 9 plateforme, 9 Gateway et 2 BFF. Les builds Angular Switch et SwitchLab réussissent.

La migration étend le schéma de 7 à 10 tables. Elle est validée par Hibernate/H2 et sur PostgreSQL 18 réel : 10 tables et 10 politiques RLS observées dans une transaction ensuite annulée par `ROLLBACK`. Les limites détaillées sont dans `tests/fraud/LIMITES_NON_LIVREES_RISK_INTELLIGENCE_2026-08-15.md`.

Statut : **GO développement local / NO-GO production** jusqu'au POC labellisé, au profil ISO/DE39, au HSM constructeur et à TLS/VPN/OAuth2.

## Document fonctionnel demandé par les validateurs — à produire ultérieurement

Les validateurs demandent un document Markdown fonctionnel, non technique et partageable, fondé uniquement sur les preuves réellement exécutées. Il devra présenter les parcours, rôles, résultats attendus et critères de validation sans divulguer l'architecture interne, les algorithmes de scoring, les règles détaillées, le modèle de données, les contrats API, les champs ISO ni le code.

Le document distinguera obligatoirement :

- les fonctions démontrées par les 97 tests, les builds Angular et la validation PostgreSQL ;
- les fonctions disponibles mais dépendantes d'une configuration client ;
- les fonctions futures non encore prouvées : apprentissage IA industriel, génération autonome de contrôles, campagne de plusieurs millions de transactions, profil ISO/DE39 client, adaptateurs BAL/agents sectoriels, HSM constructeur et sécurité de production TLS/VPN/OAuth2 ;
- les options d'industrialisation non installées : Kafka ou Redpanda, Feature Store spécialisé et base graphe spécialisée.

Aucun détail permettant de reproduire la plateforme ne devra figurer dans ce livrable. Sa rédaction est volontairement reportée à une prochaine demande de l'utilisateur.
