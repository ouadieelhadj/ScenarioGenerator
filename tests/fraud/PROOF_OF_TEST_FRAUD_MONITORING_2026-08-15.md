# Preuve de développement — FuturPayment Fraud Monitoring

Date : 15 août 2026

## Périmètre livré

- `sg-fraud-platform` : PostgreSQL, isolation par `member_id`, Card Monitoring Enrollment tokenisé, scoring explicable 0–1000, alertes, dossiers, feedback, signaux de veille, backtest de contrôles candidats et batch laboratoire ;
- `sg-fraud-bank-gateway` : API REST et ISO 8583, mode serveur ou client TCP permanent, sign-on, echo, reconnexion et échange TAK sous ZMK par port HSM ;
- BFF Switch et SwitchLab : contrôle de session puis proxy vers la plateforme ;
- frontends Switch et SwitchLab : sous-module partagé, capacités réelles, alertes et dossiers sans données fictives ;
- migration `sql/22_fraud_platform.sql` : sept tables PostgreSQL et politiques RLS préparées.

## Résultats vérifiés

- campagne Maven consolidée : **93 tests réussis**, zéro échec et `BUILD SUCCESS` (`sg-common` 77, plateforme 7, gateway 7, BFF 2) ;
- PostgreSQL 18 isolé sur `127.0.0.1` : migration appliquée, 7 tables détectées, validation JPA réussie, santé `UP`, mode `ALERT_ONLY` ;
- test TCP jPOS réel : sign-on puis echo traités sur la même connexion permanente ;
- tests plateforme : 7 réussis, aucun échec ;
- tests gateway : 7 réussis, aucun échec ;
- tests BFF : 2 réussis, aucun échec ;
- builds Angular Switch et SwitchLab : réussis, composant lazy `fraud-workspace-component` présent dans les deux produits ;
- contrôle Git : aucune erreur d’espace ou de patch, uniquement les avertissements de conversion LF/CRLF déjà connus.

## Garanties couvertes

- aucune décision de refus n’est imposée par la fraude : `enforcedAction=NO_BLOCK_ALERT_ONLY` ;
- aucune API métier n’accepte `memberId` comme autorité dans le payload ; il provient du jeton ;
- rejeu d’enrôlement et de scoring idempotent ;
- séparation BANK_A/BANK_B vérifiée ;
- PAN brut refusé ; aucune clé claire n’est transportée ou journalisée ;
- la TAK est échangée uniquement sous forme d’enveloppe chiffrée sous une ZMK référencée dans le HSM, avec KCV et accusé d’import ;
- l’implémentation de repli HSM est conditionnelle : un adaptateur constructeur peut la remplacer sans conflit de beans ;
- le DE39 transactionnel fourni au wrapper REST est conservé ; sur la liaison de monitoring, DE39=00 accuse la prise en compte et le score est porté par un champ privé configurable.

## Limites honnêtes

- l’adaptateur HSM constructeur reste à brancher sur le HSM du client ; le code échoue explicitement si aucun adaptateur n’est configuré ;
- SWAM/DMAS possèdent déjà dans `sg-common` les mécanismes jPOS et les opérations de clés de travail utilisés comme référence. Leur implémentation de simulation accepte ou retourne toutefois des clés en clair et journalise certaines enveloppes : elle ne doit pas être injectée telle quelle dans Fraud Monitoring en production. L’adaptation doit conserver le port HSM sans clé claire ;
- les codes de fonction, champs privés, packager, TLS/VPN, délais et règles ZMK/TAK doivent être validés dans le profil ISO du client avant production ;
- aucune campagne à plusieurs millions de transactions n’a été exécutée ; aucune performance IA n’est revendiquée ;
- le moteur livré est le baseline explicable et le pipeline de candidats/backtest. L’entraînement IA industriel nécessite les données labellisées du POC et une validation gouvernée.

Verdict technique local : **GO développement / NO-GO production tant que le profil client, le HSM, les certificats et la campagne POC ne sont pas validés**.

## Mise à niveau Risk Intelligence du 15 août 2026

- plateforme : snapshots `features-v1`, modèle `risk-intelligence-lot1-v1`, graphe relationnel et politique par membre pour `ALERT_ONLY`, `CHALLENGE`, `HOLD` ou `BLOCK` ;
- Gateway : événements REST/agent normalisés, retour corrélé par canal et cinq scénarios laboratoire désactivés par défaut ;
- IHM/BFF : politiques et scénarios raccordés sans données fictives ;
- tests : **97 réussis, zéro échec** (`sg-common` 77, plateforme 9, Gateway 9, BFF 2) ;
- nouveaux cas prouvés : groupe coordonné de 100 instruments, isolation interbanque, activation de décision par membre et chemin laboratoire via Gateway ;
- Angular : builds Switch et SwitchLab réussis ;
- base : migration étendue validée par Hibernate/H2 et PostgreSQL 18 réel ; 10 tables et 10 politiques RLS ont été observées dans une transaction annulée par `ROLLBACK`.

Les codes DE39 restent `00` par défaut jusqu'à validation du profil client. Les actions actives ne sont donc pas autorisées sur une liaison ISO de production à ce stade.
