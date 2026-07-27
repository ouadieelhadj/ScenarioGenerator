# Déploiement commun

- `database/` : création de base, données communes et migrations transverses.
- `runtime/platform-env.sh` : variables globales et détection de la copie locale.
- `runtime/start-platform.sh` : build, démarrage, arrêt et statut des modules.
- `runtime/` : scripts Windows historiques et scénario E2E transverse.

Les fichiers spécifiques à SWAM et au frontend se trouvent dans leurs répertoires
de module respectifs.
