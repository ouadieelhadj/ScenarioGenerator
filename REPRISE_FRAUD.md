# Reprise - FuturPayment Fraud Monitoring

## État au 15 août 2026

Le développement local et sa documentation sont consolidés dans `D:\MoneyCore\ScenarioGenerator`. La documentation produit est dans `D:\MoneyCore\ScenarioGenerator\07-fraud-monitoring`.

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

## Développement lot 2 engagé — 16 août 2026

Le développement a dépassé le lot 1. Sont désormais implémentés : contrat commercial `POST /v1/risk/score` avec score 0–100, modèle universel couvrant six domaines, entrées Gateway REST/ISO 20022/webhook/batch, signaux ATO et comportementaux, Dashboard Operations, Fraud Story et interface Switch associée. La Gateway ISO 8583 permanente reste disponible et obligatoire comme frontière bancaire.

La persistance de production est complétée par `deployment/fraud/postgresql/001_fraud_platform_schema.sql`. Chaque nouvelle décision produit dans la même transaction un événement idempotent `RiskAssessmentCompleted.v1` dans `fraud_event_outbox`. Le contrat JSON et les frontières Kafka, Feature Store, IA et graphe sont dans `deployment/fraud/contracts/`; aucune dépendance technique externe n'est activée par défaut.

Preuves de cette étape : 77 tests `sg-common`, 12 tests plateforme et 12 tests Gateway, tous réussis ; build Angular Switch réussi ; `git diff --check` ciblé sans erreur. Détail : `tests/fraud/PREUVE_DEVELOPPEMENT_LOT2_2026-08-16.md`.

Statut honnête : les fonctions applicatives ci-dessus sont prouvées localement. Kafka/Strimzi/Apicurio, Feast/Valkey, JanusGraph/Cassandra/OpenSearch et MLflow/KServe sont préparés mais non installés et non testés ; ils ne sont pas déclarés opérationnels. La prochaine phase après la chaîne technique est l'apprentissage IA et la proposition automatique de contrôles à partir d'un corpus labellisé, avec backtest, mesure des faux positifs et validation analyste avant activation.

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
- les composants d'industrialisation non installés : Apache Kafka avec KRaft, retenu définitivement pour le lot 2, ainsi que le Feature Store et la base graphe spécialisés ; Redpanda est écarté.

Aucun détail permettant de reproduire la plateforme ne devra figurer dans ce livrable. Sa rédaction est volontairement reportée à une prochaine demande de l'utilisateur.

## Décision streaming du lot 2 — 16 août 2026

L'utilisateur retient définitivement **Apache Kafka avec KRaft** pour le lot 2. Redpanda est exclu. La première installation locale utilisera Kafka afin que les producteurs, consommateurs, schémas d'événements et procédures soient conservés lors du passage en production ; seule la topologie sera renforcée.

## Laboratoire technique léger installé — 16 août 2026

Le laboratoire est disponible dans `D:\MoneyCore\ScenarioGenerator\fraud-tools-lite`. Apache Kafka 4.3.1/KRaft, Feast 0.63.0/SQLite, JanusGraph 1.1.0/BerkeleyJE et MLflow 3.14.0/scikit-learn 1.9.0 y ont été réellement installés et testés. Les preuves sont dans `fraud-tools-lite/evidence/` et l'inventaire professionnel dans `07-fraud-monitoring/livrables/FuturPayment_Fraud_Tools_Lite_Inventaire_Installation_v1.0.docx`.

Un banc à deux processus simule le Gateway Banque sur `127.0.0.2:8090` et la Plateforme sur `127.0.0.3:8089`, avec ISO persistant sur `127.0.0.3:8583`. Il a validé REST, `ALERT_ONLY`, réutilisation d'une connexion ISO persistante, caractéristiques Feast, scoring MLflow et publication/consommation Kafka. Un apprentissage sur 30 000 opérations synthétiques a produit quatre contrôles `PROPOSED` sans activation automatique.

Cette preuve n'intègre pas encore les adaptateurs dans le runtime Java du dépôt et ne vaut pas industrialisation. Valkey, Cassandra, OpenSearch, Strimzi, KServe et Kubernetes restent différés vers Linux. Tous les services de laboratoire ont été arrêtés après la campagne. Premier travail restant : raccorder les endpoints configurables au déploiement Java, rejouer sur deux VM Linux et exécuter le POC labellisé. Statut : **GO laboratoire / NO-GO production**.

## Séparation multibanque Gateway/Platform — 17 août 2026

Le Gateway possède désormais sa propre base PostgreSQL/Flyway, sans schéma ni compte partagé avec la Platform. Ouadie Bank (`MEMBER-OUADIE`), Tresor Bank (`MEMBER-TRESOR`) et Sedik Bank (`MEMBER-SEDIK`) ont chacune les secteurs `MONETIQUE` et `MOBILE_BANKING`, un port ISO jPOS dédié (8601–8603) et un port REST dédié (8701–8703). Un seul processus Gateway démarre les six listeners depuis les profils en base.

Le port REST impose le `member_id` du JWT et un secteur autorisé ; un jeton d'une autre banque est refusé. La plateforme persiste `sector_id` sur décisions, snapshots et Outbox. Feast reçoit membre et secteur ; Kafka utilise `fraud.{memberId}.{sectorId}.risk-assessment-completed.v1`. Les secrets restent hors base et le HSM réel reste reporté en V4.

Preuves : 77 tests `sg-common`, 12 Platform et 18 Gateway, soit **107 réussis, zéro échec**. Le test démarre réellement les six ports et effectue un sign-on ISO par banque. Reste à appliquer la sélection banque/secteur au frontend et au générateur, compléter les politiques sectorielles et exécuter le parcours synthétique complet.

## Baseline, Trust Validation et découverte adaptative — 17 août 2026

Le parcours synthétique multibanque est maintenant exécuté. La campagne sépare explicitement la baseline des contrôles initiaux, l'apprentissage historique avec modèle gelé suivi d'une validation rétrospective en aveugle, puis la découverte adaptative de contrôles candidats.

La preuve finale porte sur **1 000 002 opérations synthétiques**, trois banques et deux secteurs par banque. Chaque banque contient exactement 100 opérations suspectes et 5 fraudes confirmées. Les six fraudes confirmées réservées au jeu aveugle ont été détectées (`6/6`). Douze contrôles candidats ont été générés avec le statut `PROPOSED`, sans aucune activation automatique.

Le validateur contrôle automatiquement les volumes, les libellés par banque, l'intégrité aveugle, le gel du modèle, la détection des fraudes cachées et la gouvernance des propositions. Il est intégré au lanceur global `fraud-tools-lite/scripts/test-all-fraud-tools-lite.ps1`. Preuve détaillée : `tests/fraud/PREUVE_TRUST_VALIDATION_2026-08-17.md`.

Statut honnête : **GO laboratoire synthétique / NO-GO production**. La campagne prouve le protocole et le volume d'exécution, pas une performance sur données bancaires réelles. Le premier travail restant est le POC client sur historique labellisé et période aveugle convenue, après raccordement des endpoints Java et déploiement sur deux VM Linux.

## Routage Kafka dynamique et sécurité Strimzi — 18 août 2026

La table `fraud_event_route` et sa migration `deployment/fraud/postgresql/003_fraud_event_route.sql` sont implémentées. Une route est unique par membre, secteur et type d'événement ; elle porte le modèle de topic, la version de schéma, la classe de rétention, la priorité et son état. L'API d'administration `/api/fraud/v1/admin/event-routes` dérive le membre du JWT et exige le scope `fraud.admin`. Le publisher Outbox résout la route à chaque envoi, ajoute uniquement des en-têtes non sensibles et refuse tout topic sortant de l'espace `fraud.<membre>.<secteur>.`.

Le profil `application-kafka-oauth.yml` active `SASL_SSL/OAUTHBEARER` avec le client OAuth Strimzi 0.17.1, vérification TLS et secrets injectés hors Git. Le mode local conserve un repli compatible ; le profil `kafka-oauth` impose une route active en base et échoue fermé si elle manque.

Les manifests de production sont dans `deployment/fraud/kafka/strimzi/production/` : namespace, trois contrôleurs KRaft, trois brokers persistants, réplication 3, répartition sur zones, listener TLS interne, listener TLS/OAuth2, ACL Kafka, NetworkPolicy, modèle de topic et patch applicatif sans secret. Le README décrit les paramètres, le provisionnement, les Secrets attendus et les contrôles de GO.

Preuves : **18 tests plateforme réussis** et **77 tests `sg-common` réussis**, zéro échec ; le test `StrimziManifestSyntaxTest` parse les six YAML et contrôle les garde-fous de sécurité. Les manifests ne sont pas encore appliqués à un cluster réel : StorageClass, capacité, issuer/JWKS, ACL, certificats, rotation et test de panne doivent être validés avec l'exploitation. Statut : **GO code et base de déploiement / NO-GO production tant que la recette Kubernetes–IdP réelle n'est pas exécutée**.

## Généralisation graphe omnicanal et gouvernance IA — 18 août 2026

Le graphe PostgreSQL n'est plus limité à une carte. Chaque relation porte désormais `member_id`, `sector_id`, `subject_type`, `subject_hash` et `channel`. Le type de sujet est déterminé comme `CARD_TOKEN`, `ACCOUNT`, `WALLET` ou `CUSTOMER`; les références restent pseudonymisées. L'API `/api/fraud/v1/subjects/monitoring-enrollments` permet d'enrôler un sujet non-carte, tandis que l'API carte existante crée aussi son sujet générique. La table `fraud_graph_policy` configure par banque et secteur l'activation, les entités autorisées, l'analyse intersectorielle, la fenêtre d'observation, le nombre minimal d'observations, la taille du groupe et sa contribution au score. L'analyse intersectorielle peut donc corréler monétique et mobile banking ou être désactivée pour un secteur.

La table `fraud_ai_policy` configure par banque et secteur : activation, modes `SHADOW`/`ACTIVE`, Champion, Challenger, pourcentage Shadow, critères de précision/rappel/faux positifs, seuil de dérive, obligation d'explication et seuils de décision. Le Challenger ne décide jamais. Une dérive déclarée, un `driftScore` excessif, une panne, un score invalide ou une explication obligatoire absente déclenchent un retour automatique au moteur déterministe. L'approbation analyste est obligatoire et ne peut pas être désactivée.

Les API sont sous `/api/fraud/v1/admin/governance/graph/{sectorId}` et `/api/fraud/v1/admin/governance/ai/{sectorId}` et exigent le scope `fraud.admin`; le membre provient exclusivement du JWT. La migration d'upgrade est `deployment/fraud/postgresql/004_fraud_graph_ai_governance.sql` et le contrat d'exploitation est `deployment/fraud/contracts/graph-ai-governance.md`.

Preuves : **24 tests plateforme et 77 tests `sg-common` réussis, zéro échec**, incluant l'enrôlement et le scoring d'un compte mobile sans enrôlement carte, le graphe omnicanal, l'isolement sectoriel, les politiques, la sécurité OAuth2, Champion/Challenger et le fallback de dérive. PostgreSQL 18 local répond sur `127.0.0.1:5432`, mais aucun compte de recette n'est injecté dans l'environnement : la migration n'a pas été exécutée sur cette instance et aucun secret n'a été inventé. Le graphe PostgreSQL prouve la corrélation directe à un niveau; `maximumHops` prépare JanusGraph mais ne prouve pas encore un parcours multi-niveaux spécialisé. Statut : **GO code et tests locaux / NO-GO production jusqu'à migration PostgreSQL de recette et POC MLflow/JanusGraph réels**.

## Validation consolidée avant commit — 18 août 2026

La campagne Maven agrégée réussit **126 tests, zéro échec et zéro erreur** : 77 dans `sg-common`, 6 dans `sg-deployment-core`, 1 dans `sg-generator-orchestrator`, 24 dans `sg-fraud-platform` et 18 dans `sg-fraud-bank-gateway`. Le build Angular du frontend Switch réussit également. Le test d'intégration Gateway démarre réellement les trois ports ISO membres et les trois ports REST associés.
