# Exemples d'automatisation

Ces fichiers sont des gabarits pour une instance Way4 de recette.

## Import XML

1. Copier `merchant-contract.env.example` vers un fichier local non versionné.
2. Remplacer tous les codes `LOCAL_*`.
3. Charger les variables :

```bash
set -a
source ./merchant-contract.env
set +a
```

4. Générer le fichier :

```bash
./generate_merchant_application_xml.sh
```

5. Avec le XSD officiel :

```bash
export WAY4_XSD=/chemin/WAY4ApplFile.xsd
./generate_merchant_application_xml.sh
```

6. Déposer le fichier dans l'inbox :

```bash
export WAY4_XML_INBOX=/chemin/echange/in
./stage_xml_import.sh ./out/XADVAPL000100_00001.211
```

Le dépôt ne déclenche pas le pipe Way4.

## Workbench Selenium

Installer Selenium dans un environnement Python dédié, renseigner l'URL, les
données de test et les sélecteurs de l'instance, puis :

```bash
set -a
source ./workbench-selectors.env
source ./merchant-contract.env
set +a
./run_workbench_selenium.sh
```

Le clic final est désactivé par défaut.

## REST

`invoke_rest_template.sh` ne contient aucune structure JSON Way4 inventée. Il
envoie uniquement un fichier JSON validé par le propriétaire du service, après
activation explicite.
