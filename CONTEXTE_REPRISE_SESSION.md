# Contexte de reprise — Way4 Knowledge Base

## À lire en premier

Ce fichier sert de passation à toute nouvelle session Codex/ChatGPT travaillant sur ce projet.

Répertoire racine :

`E:\Way4-Knowledge-Base`

Au début d'une nouvelle session :

1. Lire ce fichier entièrement.
2. Inventorier les guides déjà présents dans `03_Guides`.
3. Consulter les documents de `01_Source_Documents` uniquement en lecture.
4. Ne jamais recréer un guide existant : le relire et l'enrichir.
5. Ne jamais inventer une information absente des sources.

## Mission donnée par l'utilisateur

Construire progressivement une base de connaissances Way4 complète, cohérente, maintenable et opérationnelle pour les équipes :

- Administration ;
- Exploitation ;
- Support ;
- Développement ;
- Intégration ;
- Architecture ;
- Monétique.

Il ne s'agit pas de résumer les manuels un par un. Les informations relatives à une même procédure doivent être recoupées entre plusieurs sources afin de produire un guide unique, consolidé et exploitable.

Toute information absente, ambiguë ou contradictoire doit être signalée explicitement. Chaque procédure doit citer les documents sources et, lorsque cela est possible, les pages utilisées.

## Sources documentaires

Répertoire des documents sources :

`E:\Way4-Knowledge-Base\01_Source_Documents`

Répertoire des manuels anglais utilisé lors des premiers travaux :

`E:\Way4-Knowledge-Base\01_Source_Documents\manuals\english`

Les documents sources doivent rester en lecture seule et ne doivent jamais être modifiés.

## Principes de rédaction demandés

La documentation doit être organisée par domaines, notamment :

- Architecture ;
- Administration ;
- Exploitation ;
- Acquiring ;
- Issuing ;
- Clearing ;
- Settlement ;
- Accounting ;
- API ;
- ISO 8583 ;
- Batch ;
- Reporting ;
- Sécurité ;
- FAQ ;
- Troubleshooting ;
- Bonnes pratiques.

Les interfaces Way4 doivent toujours être distinguées :

- Desktop Client, client riche Windows ;
- Workbench(s) Web.

Pour chaque procédure, préciser l'interface, les menus, les écrans, les champs et les différences entre Desktop Client et Workbench. Ne jamais supposer que les deux parcours sont identiques.

Un guide doit couvrir autant que possible :

- objectif et contexte ;
- prérequis ;
- interface utilisée ;
- navigation dans les menus ;
- écrans, champs et paramètres ;
- étapes détaillées ;
- vérifications ;
- dépendances et cas particuliers ;
- erreurs fréquentes et troubleshooting ;
- bonnes pratiques ;
- références documentaires.

## Travaux réalisés le 29 juillet 2026

### Acquiring

Deux guides ont été créés à partir des manuels Acquiring, Desktop Client et des sources complémentaires Product Management :

- `03_Guides\Acquiring\GUIDE_CREATION_COMMERCANT_DESKTOP_CLIENT.md`
- `03_Guides\Acquiring\GUIDE_CREATION_COMMERCANT_WORKBENCH.md`

Ils couvrent notamment :

- création du commerçant ;
- contrat compte acquéreur ;
- hiérarchie des contrats ;
- contrat device POS ;
- configuration du terminal ;
- MID, TID et SIC/MCC ;
- adresse Payment Scheme ;
- contrôle, approbation et acceptation ;
- vérifications finales et troubleshooting.

La navigation Web exacte du Workbench a été signalée comme dépendante de l'instance et reste à valider lorsque les informations d'environnement seront disponibles.

Principales sources retenues :

- `acquiring\Acquiring_Module.pdf` ;
- `desktop_client\Desktop_Client_Manual.pdf` ;
- documents pertinents de `product_management`, notamment les produits, sous-types et packages de services.

### Exploitation

Deux documents ont été créés :

- `03_Guides\Exploitation\GUIDE_UTILISATION_SCHEDULER.md`
- `03_Guides\Exploitation\GUIDE_UTILISATION_HEALTH_MONITORING.md`

Le guide Scheduler couvre la configuration, la planification, les jobs simples et batchs, le démarrage, l'arrêt sûr, la supervision, la relance, les erreurs et les journaux.

Le guide Health Monitoring est volontairement incomplet et marqué comme bloqué, car les deux sources suivantes étaient absentes :

- `Health Monitoring Gen2 Functional Specification` ;
- `Administering Health Monitoring Gen2`.

Ne pas compléter les procédures Health Monitoring sans disposer de ces sources ou d'une autre documentation fiable.

### Accounting

Un guide consolidé a été créé :

- `03_Guides\Accounting\GUIDE_COMPTABILITE_PARAMETRAGE_ET_EXPORT.md`

Il couvre notamment :

- plan comptable et types de comptes ;
- comptes GL et auxiliaires ;
- Account Schemes et modèles de comptes ;
- correspondances comptables et libellés ;
- génération et traçabilité des écritures ;
- clôture quotidienne ;
- export UFX XML via `GL Transfers Export` ;
- export texte plat via `CBS. GL Transfers Export` ;
- paramétrage des pipes ;
- contrôles des fichiers et rapprochement avec le Core Banking ;
- réexport, risque de doublon, erreurs et reprise d'une clôture incomplète.

Le choix du format d'export dépend de l'interface Core Banking cible.

## État actuel vérifié

Les répertoires présents à la racine sont :

- `01_Source_Documents` ;
- `03_Guides`.

Les cinq guides indiqués ci-dessus existent sur le disque.

Le dossier `00_Project`, prévu dans le brief initial, n'était pas présent lors de la création de ce fichier. Les fichiers permanents initialement envisagés (`PROJECT_STATUS.md`, `SESSION_LOG.md` et `ROADMAP.md`) n'ont donc pas été confirmés comme existants.

## Dernier point de l'ancienne session

La session historique s'appelait :

`Construire base de connaissances Way`

Elle n'exécute plus de tâche. Sa dernière activité, le 29 juillet 2026, concernait une demande d'accès à Outlook afin de récupérer et résumer un historique d'e-mails. L'installation du connecteur Outlook n'avait pas été finalisée.

Ce sujet Outlook est distinct de la documentation Way4 et ne doit être repris que sur demande explicite de l'utilisateur.

## Priorités possibles pour la prochaine session

Avant toute nouvelle rédaction :

1. demander à l'utilisateur le prochain module ou guide souhaité ;
2. vérifier si un guide correspondant existe déjà ;
3. sélectionner et faire valider les manuels utiles ;
4. recouper les versions et les interfaces ;
5. produire ou enrichir le guide avec références précises ;
6. signaler clairement les informations non confirmées.

Point ouvert prioritaire : compléter Health Monitoring uniquement après ajout des deux manuels manquants.
