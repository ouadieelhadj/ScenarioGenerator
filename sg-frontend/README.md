# IHM ScenarioGenerator — Front Angular

Interface web de la plateforme ScenarioGenerator (Angular 18 standalone + PrimeNG), avec authentification JWT, RBAC par permission, et thème configurable (variables CSS centralisées + changement à chaud).

## Prérequis

- Node.js 18.19+ ou 20+
- npm 10+
- Les services back démarrés (orchestrateur 8080, acquéreur 8084, issuer 8501)

## Installation

Ce dossier contient le code source. Pour l'utiliser dans un projet Angular fonctionnel :

```bash
# 1. Se placer dans le dossier
cd sg-frontend

# 2. Installer les dépendances
npm install

# 3. Lancer en développement
npm start
```

L'application démarre sur `http://localhost:4200`.

Si `npm install` échoue sur les versions, vérifier que Node est à jour (`node -v`).

## Connexion

Compte administrateur : `admin` / `Admin123!`

Le front appelle l'API sur `http://localhost:8080` (orchestrateur). Vérifier que le back tourne (voir le dossier `deploiement` du projet Java).

## CORS

Le back doit autoriser l'origine `http://localhost:4200`. Si les requêtes sont bloquées (erreur CORS dans la console), ajouter une configuration CORS côté Spring (à faire côté back).

## Architecture

```
src/app/
├── core/                        Socle technique
│   ├── auth/                    AuthService (login, décodage JWT, permissions)
│   ├── guards/                  authGuard + permissionGuard
│   ├── interceptors/            Bearer token + gestion 401
│   ├── models/                  Types TypeScript (miroir du back)
│   ├── services/                Services API (à compléter)
│   └── theme/                   ThemeService + thèmes + tokens
├── shared/
│   └── directives/              *hasPermission (masque selon permission)
├── layout/                      Sidebar (filtrée par permission) + topbar (thème)
├── features/                    Les 8 écrans
│   ├── login/
│   ├── dashboard/
│   ├── campaign-generation/     Génération de campagne (CAMPAIGN_CREATE)
│   ├── campaign-orchestration/  Orchestration de campagne (TPS_RUN)
│   ├── execution-view/          Consultation des exécutions (EXECUTION_VIEW)
│   ├── dmas/                    Monétique DMAS (CARD_PROVISION)
│   ├── admin/                   Administration (USER_MANAGE...)
│   └── profile/
├── app.config.ts                Providers (router, http, interceptors, PrimeNG)
├── app.routes.ts                Routes + permissions par écran
└── app.component.ts
```

## Système de thème (CSS configurable)

Toute la charte est centralisée dans `src/styles/_tokens.scss` sous forme de variables CSS (`--sg-color-primary`, `--sg-bg-surface`, etc.). Modifier une valeur ici la propage à toute l'application.

Le `ThemeService` (`core/theme/`) permet :
- de basculer entre thèmes prédéfinis (clair / sombre) à chaud
- de surcharger une variable à chaud (ex. couleur principale via le sélecteur de couleur dans la barre du haut)
- de persister le choix de l'utilisateur (localStorage)

Pour ajouter un thème : éditer `src/app/core/theme/themes.ts`.

## RBAC par permission

L'accès aux écrans est contrôlé par **permission** (pas seulement par rôle). Les permissions sont lues dans le token JWT (claim `permissions`), rempli par le back au login.

- **Routes** : chaque route déclare ses permissions dans `data.permissions`, vérifiées par `permissionGuard`.
- **Menu** : la sidebar n'affiche que les écrans autorisés (voir `layout/menu.ts`).
- **Éléments** : masquer un bouton avec la directive `*hasPermission`.

Exemple :
```html
<button *hasPermission="'CAMPAIGN_CREATE'">Créer une campagne</button>
```

Mapping écran → permission :

| Écran | Permission requise |
|-------|--------------------|
| Génération de campagne | CAMPAIGN_CREATE, CAMPAIGN_GENERATE |
| Orchestration de campagne | TPS_RUN, CAMPAIGN_REPLAY |
| Consultation des exécutions | EXECUTION_VIEW, CAMPAIGN_VIEW |
| Monétique DMAS | CARD_PROVISION |
| Administration | USER_MANAGE, ROLE_MANAGE, CATALOG_MANAGE |

## État d'avancement

- ✅ Socle : auth JWT, RBAC, thème configurable, layout, routing
- ✅ Écran login fonctionnel
- ⬜ Les 7 autres écrans sont des placeholders — à implémenter (formulaires campagnes, tableaux d'exécutions, admin users, etc.)

## Prochaines étapes suggérées

1. Implémenter les services API dans `core/services/` (CampaignService, ExecutionService, UserService)
2. Développer les écrans un par un, en commençant par la génération de campagne
3. Ajouter la configuration CORS côté back
