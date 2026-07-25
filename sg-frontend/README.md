# sg-frontend — IHM Angular de la plateforme ScenarioGenerator

Interface web de la plateforme **ScenarioGenerator** : génération, orchestration et supervision de campagnes de tests monétiques (DMAS / SWAM).

---

## Stack technique

| Technologie | Version | Rôle |
|---|---|---|
| **Angular** | 18.2 | Framework principal (standalone components, Signals) |
| **TypeScript** | 5.5 | Langage |
| **PrimeNG** | 18 + thème Aura | Composants UI |
| **@ngx-translate** | 18 | Internationalisation (FR / EN / ES) |
| **RxJS** | 7.8 | Réactivité asynchrone |
| **Angular Router** | 18 | Navigation lazy-loaded |

---

## Architecture

```
sg-frontend/
├── src/
│   ├── app/
│   │   ├── core/
│   │   │   ├── auth/           AuthService (JWT, Signals)
│   │   │   ├── config/         api.config.ts (URLs + ports dynamiques)
│   │   │   ├── guards/         authGuard, permissionGuard
│   │   │   ├── interceptors/   authInterceptor (JWT), errorInterceptor (401)
│   │   │   ├── i18n/           LanguageService (fr/en/es)
│   │   │   ├── models/         auth, admin, campaign, dmas
│   │   │   ├── services/       campaign, dmas, network, user, portConfig
│   │   │   └── theme/          ThemeService (clair/sombre, tokens CSS)
│   │   ├── features/
│   │   │   ├── login/          Page de connexion + page Accès refusé
│   │   │   ├── dashboard/      Tableau de bord (placeholder)
│   │   │   ├── campaign-generation/    CRUD campagnes
│   │   │   ├── campaign-orchestration/ Lancement + suivi temps réel
│   │   │   ├── execution-view/         Historique des exécutions
│   │   │   ├── dmas/           Panel DMAS (cartes, clés, auth 0100)
│   │   │   ├── admin/          Gestion utilisateurs
│   │   │   ├── config/         Configuration des ports backend
│   │   │   ├── help/           Aide contextuelle détaillée
│   │   │   └── profile/        Profil utilisateur (placeholder)
│   │   ├── layout/             MainLayout (sidebar, thème, langue)
│   │   └── shared/             Directive hasPermission
│   ├── assets/i18n/            fr.json / en.json / es.json
│   └── environments/           environment.ts (dev) / environment.prod.ts
└── package.json
```

---

## Pages et état

| Route | Écran | Permissions | État |
|---|---|---|---|
| `/login` | Connexion | — | ✅ Fait |
| `/dashboard` | Tableau de bord | — | ⏳ Placeholder |
| `/campaign-generation` | CRUD campagnes | CAMPAIGN_CREATE | ✅ Fait |
| `/campaign-orchestration` | Lancement + suivi | TPS_RUN | ✅ Fait |
| `/executions` | Historique exécutions | EXECUTION_VIEW | ✅ Fait |
| `/dmas` | DMAS (cartes, clés, auth) | CARD_PROVISION | ✅ Fait |
| `/admin` | Gestion utilisateurs | USER_MANAGE | ✅ Fait |
| `/config` | Configuration ports | USER_MANAGE | ✅ Fait |
| `/help` | Aide détaillée | — | ✅ Fait |
| `/profile` | Profil utilisateur | — | ⏳ Placeholder |

---

## Backend requis

Le frontend consomme trois services Spring Boot :

| Service | Port dev | Rôle |
|---|---|---|
| Orchestrateur | `8080` | Campagnes, exécutions, users, auth JWT |
| DMAS Acquéreur | `8084` | KEK, PEK, transactions DMAS |
| DMAS Émetteur | `8501` | Cartes, sign-on |

Les ports sont configurables à chaud depuis l'écran **Configuration** (sans redémarrer le frontend).

---

## Prérequis

- **Node.js ≥ 18** (Angular 18 l'exige) — télécharger sur https://nodejs.org (LTS)
- **npm** (inclus avec Node.js)

Vérifier :
```bash
node --version   # v18.x / v20.x / v22.x
npm --version
```

---

## Lancer en développement

```bash
# 1. Cloner le repo
git clone https://github.com/ouadieelhadj/ScenarioGenerator.git
cd ScenarioGenerator/sg-frontend

# 2. Installer les dépendances
npm install

# 3. Lancer le serveur de développement
npm start
# -> http://localhost:4200
```

Le frontend se connecte aux backends sur les ports par défaut (8080 / 8084 / 8501).  
Assurez-vous que les services Spring Boot sont démarrés avant de naviguer.

---

## Build production

```bash
npm run build
# Sortie dans dist/sg-frontend/
```

---

## Fonctionnalités clés

- **Authentification JWT** : connexion → token stocké → rechargement de session automatique
- **Contrôle d'accès par permission** : chaque route et bouton sont conditionnés aux permissions du JWT
- **Thème clair / sombre** : switchable à chaud, persisté en localStorage
- **Internationalisation** : français (défaut), anglais, espagnol
- **Ports dynamiques** : les URLs backend sont reconfigurables depuis l'IHM sans redémarrage
- **Suivi temps réel** : l'orchestration poll le statut d'exécution toutes les 2 secondes

---

## Rôles utilisateurs

| Rôle | Permissions |
|---|---|
| `ADMIN` | Tout (users, campagnes, config, exécutions, DMAS) |
| `EXPLOITATION` | Campagnes, orchestration, exécutions, DMAS |
| `OBSERVATEUR` | Consultation exécutions uniquement |

---

## À implémenter

- Écran **Dashboard** (KPIs temps réel, graphiques)
- Écran **Profil** (changement mot de passe)
- Écran **SWAM** (panel équivalent DMAS pour le switch SWAM)
