# Guide d'intégration - Création et chargement des commerçants et contrats Way4

| Métadonnée | Valeur |
|---|---|
| Domaine | Acquiring |
| Périmètre | Commerçant, contrat compte acquéreur, contrat device POS |
| Version documentaire principale | Way4 03.60.30 |
| Statut | Guide consolidé - paramètres locaux à valider |
| Dernière mise à jour | 2026-07-30 |

## 1. Objectif

Présenter les moyens documentés pour créer dans Way4 :

1. un client représentant le commerçant ;
2. un contrat compte acquéreur ;
3. un contrat device POS et son terminal ;
4. les adresses et paramètres associés ;
5. puis contrôler l'import, le workflow et la création effective des objets.

Ce document complète les guides Desktop Client et Workbench déjà présents dans
`03_Guides\Acquiring`. Il se concentre sur le choix du canal, les
spécifications d'intégration et l'automatisation.

## 2. Synthèse des canaux

| Canal | Confirmé par les sources | Format | Automatisation proposée | Niveau de préparation |
|---|---:|---|---|---|
| Desktop Client / Advanced Applications R2 | Oui | Formulaires | Appium/WinAppDriver ou outil UI Windows | À préparer après relevé des identifiants UI |
| Workbench Web | Partiellement | Formulaires Web | Selenium | Squelette fourni, sélecteurs locaux obligatoires |
| Import XML Applications R2 | Oui | XML UFX, racine `ApplicationFile` | Git Bash : génération, validation et dépôt | Exemple et scripts fournis |
| UFX en ligne | Oui | UFX | Composant UFXGate ou TS SOA UFX | Documentation d'infrastructure absente du corpus |
| Web services | Oui | Non précisé dans le corpus étudié | Appel technique après obtention du contrat de service | URL, authentification et schéma absents |
| REST/JSON | Non confirmé | Inconnu | Lanceur `curl` générique seulement | Ne pas utiliser sans contrat d'API validé |

Le manuel Advanced Applications R2 indique que les demandes sont saisies
manuellement, importées depuis des fichiers, reçues par une interface UFX ou par
des web services, puis enregistrées dans `ADV_APPL`. [ADV-ACQ, p. 8]

## 3. Modèle fonctionnel commun

La structure cible habituelle est :

`Client commerçant > Contrat compte acquéreur > Contrat device POS`

Objets minimaux à préparer :

| Objet | Données principales |
|---|---|
| Client | institution, type, catégorie `Commercial`, nom court, raison sociale, numéro d'enregistrement, identifiant fiscal, adresse |
| Contrat compte | numéro, nom, produit Acquiring, MID, SIC/MCC, compte de règlement, adresse Payment Scheme |
| Contrat device | contrat parent, numéro/TID, produit POS, MID, SIC/MCC |
| Terminal | type de device, emplacement, devise, fuseau, horaires, statut et paramètres de sécurité |

Les produits, sous-types, Account Schemes, Service Packages, types de POS,
codes d'institution, codes d'agence et types d'adresse sont des données de
configuration locales. Ils ne doivent pas être déduits des exemples.

## 4. Création manuelle dans le Desktop Client

Parcours fonctionnel documenté :

`Advanced Applications R2 > Merchant Input & Update by Applications`

La procédure détaillée figure dans :

`GUIDE_CREATION_COMMERCANT_DESKTOP_CLIENT.md`

### Automatisation

Selenium ne pilote pas une application Windows native. Pour automatiser le
Desktop Client, utiliser un outil capable d'exploiter les propriétés
d'accessibilité Windows, par exemple Appium avec un pilote Windows compatible.

Avant de développer le robot :

1. relever les identifiants d'accessibilité des fenêtres, champs et boutons ;
2. stabiliser le type et le schéma de demande ;
3. préparer un compte de test avec le rôle minimal ;
4. exécuter en environnement de recette ;
5. laisser l'action finale `Approve` ou `Accept` désactivée jusqu'à validation.

Aucun script Desktop exécutable n'est fourni, car les identifiants UI et la
version du client installé ne figurent pas dans les sources.

## 5. Création dans le Workbench Web

Le Workbench utilise les mêmes objets fonctionnels, mais les sources ne
fournissent pas une arborescence Web universelle ni les sélecteurs HTML de
l'instance.

Le répertoire d'exemples contient :

- `run_workbench_selenium.sh` ;
- `workbench_create_merchant.py` ;
- `workbench-selectors.env.example`.

Le robot :

- refuse de démarrer sans URL et sélecteurs explicites ;
- lit les données et secrets depuis l'environnement ;
- remplit uniquement les champs configurés ;
- ne clique sur le bouton final que si
  `WAY4_UI_CONFIRM_SUBMIT=YES`.

Il faut remplacer tous les sélecteurs d'exemple après inspection autorisée du
Workbench de recette.

## 6. Import XML Applications R2

### 6.1 Capacités confirmées

Le fichier entrant peut contenir des demandes pour créer de nouveaux clients
et contrats ou modifier leurs propriétés. Il utilise XML, avec
`ApplicationFile` comme élément racine. [XML-APPL, p. 58]

Le fichier contient :

```text
ApplicationFile
├── FileHeader
└── ApplicationsList
    └── Application (1..n)
```

Une application peut contenir `SubApplList` afin de former un arbre. Un parent
de type `Client` peut avoir une sous-application `Contract`; un parent
`Contract` peut également avoir une sous-application `Contract`.
[XML-APPL, pp. 60-62 ; schéma `xml_appl_schema.html`]

### 6.2 En-tête du fichier

| Élément | Règle |
|---|---|
| `FormatVersion` | Version du format, par exemple `2.0` dans l'exemple officiel |
| `Sender` | Code de l'émetteur configuré dans la BIN Table, sans son préfixe de trois caractères |
| `CreationDate` | Date de création |
| `CreationTime` | Heure de création |
| `Number` | Numéro séquentiel du fichier pour la date |
| `Institution` | Branch Code de l'institution financière |

Le nom étendu suit la structure :

`XADVAPL<SENDER_6>_<NUMERO_5>.<JOUR_JJJ>`

[XML-APPL, pp. 58-60]

### 6.3 Application

| Élément | Usage |
|---|---|
| `RegNumber` | Numéro unique de demande, maximum 64 caractères |
| `Institution` | Institution cible, facultative si héritée/configurée |
| `OrderDprt` | Code de l'agence ou département recevant la demande |
| `ObjectType` | Notamment `Client`, `Contract` ou `ClientContract` |
| `ActionType` | Notamment `Add`, `AddOrUpdate` ou `Update` |
| `ProductCategory` | `Acquiring` |
| `ObjectFor` | Identifiant de l'objet existant, principalement pour une mise à jour |
| `Data` | Données de l'objet |
| `SubApplList` | Sous-applications formant l'arbre client/contrats |

Pour une création contrôlée, préférer `Add`. N'utiliser `AddOrUpdate` qu'après
analyse de l'idempotence et de la règle de recherche des objets.

### 6.4 Identification et contraintes

- Le MID est limité à 15 caractères ASCII imprimables dans le manuel
  Acquiring. [ACQ, pp. 13-15]
- Le TID POS contient normalement 8 caractères. [ACQ, pp. 18-20]
- Le SIC/MCC du schéma XML est limité à 4 caractères.
- Les codes de devise importés doivent être alphabétiques. [XML-APPL, p. 58]
- Pour vérifier qu'un contrat appartient à un client, Way4 peut utiliser
  `ShortName`, `ClientNumber`, `RegNumber` ou `SocialNumber`, avec priorité au
  `ShortName`. [XML-APPL, pp. 64-65]
- L'unicité des demandes `Add` portant sur `Client` et `Contract` est
  contrôlée à certaines étapes lorsque `APPL_CHECK_UNIQUE_OBJECT=Y`.
  [ADV-ACQ, p. 117]

### 6.5 Exemple fourni

Le fichier `merchant-contract.example.xml` illustre :

- un client commerçant parent ;
- un contrat compte acquéreur ;
- une adresse Payment Scheme ;
- un contrat device POS ;
- les paramètres principaux du terminal.

Toutes les valeurs écrites sous forme `LOCAL_*` doivent être remplacées par des
codes existant dans l'instance. L'exemple est un gabarit de travail, pas un
fichier prêt à importer.

Le script `generate_merchant_application_xml.sh` produit un fichier équivalent
à partir de variables d'environnement et échappe les caractères XML.

### 6.6 Validation

Le manuel demande de valider le fichier contre `WAY4ApplFile.xsd`; le chemin
est configuré par `XML_SCHEMA_URL` et la validation peut être activée par
`VALIDATE_FILE`. [XML-APPL, pp. 8-10 et 58]

Le corpus contient la représentation
`xml_appl_schema.html`, mais pas le fichier XSD. La validation complète devra
donc utiliser le XSD livré avec l'installation Way4.

Le générateur :

- contrôle les variables indispensables ;
- produit du XML bien formé ;
- appelle `xmllint --schema` si `WAY4_XSD` est fourni.

## 7. Dépôt et lancement de l'import XML

Les fichiers doivent être placés dans les répertoires standards d'échange.
[ADV-ACQ, p. 85 ; XML-APPL, p. 98]

Menus :

- Workflow :
  `Advanced Applications R2 > Application Processing > Acquiring XML Application Import`
- Sans workflow :
  `Advanced Applications R2 > Applications No Workflow > Acquiring Application Import (No WF)`
- Module Acquiring :
  `Acquiring > Applications Batch Interface > Acquiring Application Import (No WF)`

Le script `stage_xml_import.sh` copie un fichier validé dans le répertoire
indiqué par `WAY4_XML_INBOX`. Par sécurité, il ne déclenche aucun pipe ni menu
Way4. Une commande de lancement ne pourra être ajoutée qu'après identification
du mécanisme d'exploitation local.

Après import :

1. consulter les applications XML Acquiring ;
2. vérifier les statuts et erreurs ;
3. traiter le workflow ou l'acceptation ;
4. contrôler le fichier de réponse sortant ;
5. vérifier le client et les contrats dans le module Acquiring.

Way4 rejette un fichier déjà importé. Un fichier partiellement reçu peut être
supprimé et réimporté seulement si aucune de ses applications n'a été traitée.
[ADV-ACQ, pp. 86 et 88-89]

## 8. UFX en ligne et web services

Les sources confirment :

- UFXGate ou une solution TS SOA UFX pour les demandes UFX ;
- un canal web services ;
- des écrans distincts de supervision UFX et Web Services.

[ADV-ACQ, pp. 87, 113 et 117]

Le corpus étudié ne contient pas :

- l'URL du service ;
- le protocole précis du service ;
- le mode d'authentification ;
- les en-têtes HTTP ;
- un WSDL ou une spécification OpenAPI ;
- un schéma JSON ;
- un exemple de requête REST de création d'un commerçant.

Il serait donc incorrect de présenter une structure JSON inventée comme une
API Way4.

Le script `invoke_rest_template.sh` est uniquement un transport générique
`curl`. Il exige :

- `WAY4_REST_URL` ;
- `WAY4_REST_TOKEN` ou un mécanisme d'authentification adapté ;
- un fichier JSON fourni et validé par le propriétaire de l'API ;
- `WAY4_REST_CONFIRMED=YES`.

Sans ces éléments, il s'arrête sans envoyer de requête.

## 9. Choix recommandé

| Besoin | Canal recommandé |
|---|---|
| Création ponctuelle contrôlée | Advanced Applications R2 |
| Création Web assistée | Workbench, après validation de l'instance |
| Migration ou chargement en masse | Import XML Applications R2 |
| Intégration synchrone avec un SI externe | UFX/web service après obtention du contrat d'interface |
| Prototype REST/JSON | À différer jusqu'à réception de la spécification officielle |

Pour commencer l'automatisation, utiliser l'import XML dans une recette isolée,
avec un seul commerçant de test, un MID/TID réservés et un workflow contrôlé.

## 10. Checklist avant premier chargement

- [ ] Institution, agence, type et catégorie client validés.
- [ ] Produits compte et device existants.
- [ ] Sous-types, Account Schemes et Service Packages validés.
- [ ] Codes MID, TID et MCC réservés.
- [ ] Type d'adresse Payment Scheme confirmé.
- [ ] Type de POS et devise confirmés.
- [ ] Schéma de demande et workflow identifiés.
- [ ] XSD `WAY4ApplFile.xsd` obtenu.
- [ ] Répertoires d'échange entrant/sortant identifiés.
- [ ] Sauvegarde et procédure de retour arrière définies.
- [ ] Test effectué avec `Add` et une seule arborescence.
- [ ] Fichier de réponse et Process Log vérifiés.
- [ ] Objets opérationnels contrôlés après acceptation.

## 11. Références

- **[ADV-ACQ]** OpenWay, *Advanced Applications R2 Acquiring - Operation
  Manual*, version 03.60.30, 11.08.2025, notamment pp. 8, 82, 85-89, 113,
  117 et 119.
- **[XML-APPL]** OpenWay, *XML Applications (R2) - Installation and
  Configuration Manual*, version 03.60.30, 11.08.2025, notamment pp. 7-10,
  58-65 et 94-100.
- **[XML-SCHEMA]** OpenWay, `xml_appl_schema.html`, schéma graphique des
  agrégats `ApplicationFile`, `Application`, `Client`, `Contract` et
  `DeviceInfo`.
- **[ACQ]** OpenWay, *Acquiring Module - Operation Manual*, version 03.58.30,
  03.04.2024, notamment pp. 7-24 et 30-34.
