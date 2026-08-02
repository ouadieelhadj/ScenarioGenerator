# Recettes E2E Git Bash de la plateforme

Ce repertoire fournit une convention unique au-dessus des harnais existants.
Il ne remplace ni les scripts metier ni leurs controles de securite.

Le point de reprise et les derniers resultats executes sont consignes dans
`REPRISE_TESTS_PLATEFORME.md` a la racine du depot.

Pour installer et executer ces campagnes sur la machine RECETTE, suivre
`tests/platform-e2e/INSTRUCTIONS_LIVRAISON_RECETTE.md`. Le rapport fonctionnel
global schematise est `tests/platform-e2e/global/GUIDE_TEST.md`.

## Parcours disponibles

| Repertoire | Perimetre |
|---|---|
| `issuing/` | achat local jusqu'a la decision Issuing |
| `acquiring/` | TPE/ServerPOS et e-commerce |
| `three-ds/` | frictionless et challenge 3DS sandbox |
| `mastercard-dmas-dmcs/` | autorisation DMAS et clearing DMCS |
| `swam/` | autorisation SID et clearing LIS |
| `visa/` | Visa Online et clearing Base II |
| `global/` | execution sequentielle et bilan consolide |

Chaque repertoire contient les scripts `00` a `07`, `run-all.sh` et un guide
de test schematise. `06-tail-logs.sh` est interactif et n'est donc pas appele
par `run-all.sh`.

## Configuration locale

Depuis Git Bash, a la racine du depot :

```bash
mkdir -p runtime/platform-e2e
cp tests/platform-e2e/platform-e2e.env.example \
  runtime/platform-e2e/platform-e2e.env
```

Renseigner seulement la copie locale. Les valeurs claires de test ne sont pas
versionnees. Le chargeur reutilise d'abord, s'il existe, le fichier historique
`runtime/issuing-connected-e2e/connected-e2e.env`; il n'est donc pas necessaire
de recopier les variables deja presentes. Un fichier specialise facultatif
peut surcharger la configuration commune, par exemple
`runtime/platform-e2e/swam.env`.

Le modele declare aussi les emplacements de la ZMK SWAM protegee et du jeu de
cles Visa de test. Les valeurs restent dans le `.env` local de livraison,
ignore par Git. Le PDF Visa confidentiel et les composantes claires ne doivent
jamais etre recopies dans ce repertoire versionne.

## Convention d'execution

```bash
bash tests/platform-e2e/<domaine>/00-check-prerequisites.sh
bash tests/platform-e2e/<domaine>/01-build.sh
bash tests/platform-e2e/<domaine>/02-start.sh
bash tests/platform-e2e/<domaine>/03-bootstrap-and-provision.sh
bash tests/platform-e2e/<domaine>/04-run-tests.sh
bash tests/platform-e2e/<domaine>/05-check-results.sh
bash tests/platform-e2e/<domaine>/06-tail-logs.sh
bash tests/platform-e2e/<domaine>/07-stop.sh
```

Ou en une seule commande :

```bash
bash tests/platform-e2e/<domaine>/run-all.sh
```

Apres un premier build valide, il est possible de reutiliser les JAR :

```bash
PLATFORM_E2E_SKIP_BUILD=true \
  bash tests/platform-e2e/<domaine>/run-all.sh
```
