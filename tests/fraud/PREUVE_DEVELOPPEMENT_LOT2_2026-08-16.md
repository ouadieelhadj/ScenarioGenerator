# Preuve de développement Fraud Monitoring — 16 août 2026

## Résultat

Le socle applicatif du lot 2 a été étendu sans installation des composants industriels externes. Les fonctions ci-dessous sont développées et testées localement ; les raccordements Kafka, Feast, JanusGraph et KServe restent désactivés tant que leurs outils ne sont pas installés et recettés.

## Fonctions prouvées

- contrat public `POST /v1/risk/score`, score 0–100, décision, type de fraude, confiance, raisons et version ;
- isolation du contrat public par JWT, audience, scope et `member_id` ;
- signaux ATO, comportement, nouveauté commerçant et anomalie IA injectables dans le score explicable ;
- modèle universel pour six domaines : carte, paiement mobile, internet banking, mobile banking, virement et 3DS ;
- entrées Gateway REST universelle, ISO 20022, webhook et batch, plus le chemin ISO 8583 permanent existant ;
- rejet des documents XML contenant DTD/XXE ;
- Dashboard Operations et Fraud Story cloisonnés par membre ;
- frontend Switch raccordé aux volumes, répartitions, entités opaques, décisions et Fraud Story ;
- migration PostgreSQL idempotente couvrant les entités fraude et l'outbox ;
- événement `RiskAssessmentCompleted.v1` écrit dans l'outbox dans la même transaction que la décision, sans doublon lors d'un rejeu.

## Validations exécutées

- `sg-common` : 77 tests, zéro échec ;
- `sg-fraud-platform` : 12 tests, zéro échec ;
- `sg-fraud-bank-gateway` : 12 tests, zéro échec ;
- build Angular `futurpayment-switch` : succès ;
- `git diff --check` ciblé : aucune erreur, seulement les avertissements LF/CRLF connus.

## Préparé mais non encore prouvé avec les outils réels

- schéma JSON de l'événement Kafka et clé métier idempotente ;
- contrats de ports Feature Store, inférence IA et intelligence graphe ;
- variables de configuration non sensibles et endpoint de readiness ;
- cible Kafka/Strimzi/Apicurio, Feast/Valkey, JanusGraph/Cassandra/OpenSearch et MLflow/KServe.

Ces intégrations conservent le statut `CONFIGURED_NOT_PROBED` après configuration et ne deviennent `PROUVÉ` qu'après installation, tests de connectivité, tests fonctionnels et reprise sur incident.

## Prochaine phase

Après finalisation de la chaîne technique, cadrer et construire l'apprentissage : constitution d'un corpus labellisé, séparation entraînement/validation/test, génération automatique de contrôles candidats, backtesting, mesure précision/rappel/faux positifs, explication, validation analyste, activation réversible et suivi de dérive.
