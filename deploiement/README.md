# Déploiement — Initialisation de la base `generatorscenario`

Procédure d'initialisation complète de la plateforme ScenarioGenerator sur une base PostgreSQL neuve, suivie du démarrage des services et d'un test de bout en bout.

## Principe

La base est créée **entièrement par script SQL** (structure extraite du modèle Hibernate), puis les services démarrent en `ddl-auto=validate` (ils valident le schéma sans le modifier). Cette approche est reproductible et évite les conflits liés à la création de schéma par plusieurs services.

## Prérequis

- PostgreSQL 18 installé (`D:\MoneyCore\PostgreSQL\18\bin`)
- JDK 21 (`D:\MoneyCore\jdk-21.0.11`)
- Les 3 fat JAR compilés dans `D:\MoneyCore\ScenarioGenerator\<module>\target\`
  - `sg-dmas-acquirer-1.0.0-SNAPSHOT.jar`
  - `sg-dmas-issuer-1.0.0-SNAPSHOT.jar`
  - `sg-generator-orchestrator-1.0.0-SNAPSHOT.jar`
- Superutilisateur PostgreSQL `postgres` / `postgres123`

Pour compiler les JAR si nécessaire :
```
mvn clean package -DskipTests -pl sg-common,sg-dmas-issuer,sg-dmas-acquirer,sg-generator-orchestrator -am
```

## Fichiers

| Fichier | Rôle |
|---------|------|
| `structure_tables.sql` | 35 tables + contraintes FK + répartition de la propriété + droits croisés |
| `donnees_reference.sql` | Données de référence (rôles, permissions, utilisateurs, cartes, clés, catalogues) |
| `1_create-data_base.bat` | Crée la base, les users, exécute les 2 scripts SQL |
| `2_start-services.bat` | Démarre les 3 services en `validate` |
| `3_scenario-e2e.bat` | Test de bout en bout du flux monétique |

## Procédure

Lancer les 3 scripts dans l'ordre, depuis le dossier `deploiement`.

### 1. Créer la base

```
1_create-data_base.bat structure_tables.sql donnees_reference.sql
```

Ce script :
- crée les 3 users applicatifs s'ils n'existent pas (`scenario_user`, `dmas_acquirer_user`, `dmas_issuer_user`)
- supprime puis recrée la base `generatorscenario`
- exécute `structure_tables.sql` (35 tables + propriété + droits)
- exécute `donnees_reference.sql` (données de référence)
- affiche un contrôle final (nombre de tables, users, répartition des propriétaires)

Résultat attendu : 35 tables, users=3, roles=3, dmas_cards=7, et la répartition de propriété : `scenario_user`=29, `dmas_acquirer_user`=2, `dmas_issuer_user`=4.

### 2. Démarrer les services

```
2_start-services.bat
```

Démarre les 3 services en arrière-plan (logs dans `%TEMP%\sg_logs\`) et attend qu'ils répondent.

Résultat attendu : `Services UP : acquereur=403 issuer=403 orchestrateur=200` (403 = service actif et sécurisé, 200 = ouvert).

### 3. Tester la plateforme

```
3_scenario-e2e.bat
```

Déroule le scénario complet : login → bootstrap KEK → sign-on issuer → key exchange PEK → achat unitaire → création de campagne → exécution TPS → suivi jusqu'à COMPLETED.

Résultat attendu : `COMPLETED / PASSED`, transactions approuvées, tous les critères SLA respectés.

## Modèle de propriété des tables

Chaque table appartient au user du service qui l'utilise principalement :

- **`scenario_user`** (orchestrateur) : 29 tables (campagnes, exécutions, RBAC, catalogues, IPM, autorisations)
- **`dmas_acquirer_user`** : `dmas_acq_keys`, `dmas_kek`
- **`dmas_issuer_user`** : `dmas_cards`, `dmas_iss_keys`, `dmas_transactions`, `key_store`

Droits croisés sur les tables partagées :
- `users` : accessible aux 3 services (authentification)
- `dmas_kek` : accessible à l'issuer (propriétaire acquéreur)
- `dmas_cards` : accessible à l'orchestrateur (lecture des cartes lors des campagnes)

## Comptes de test

| Login | Mot de passe | Rôle |
|-------|--------------|------|
| admin | Admin123! | ADMIN |
| obs1 | Test123! | OBSERVATEUR |
| mohamed | Test123! | EXPLOITATION |

## Dépannage

- **Un service ne démarre pas (erreur de validation de schéma)** : une table ou colonne ne correspond pas. Regénérer `structure_tables.sql` depuis un modèle Hibernate à jour.
- **`droit refusé pour la table X`** : il manque un droit croisé. Ajouter le `GRANT` correspondant dans `structure_tables.sql` (section droits croisés).
- **`Unable to create tempDir`** : ne pas définir la variable d'environnement `TMP` (réservée par Windows pour `java.io.tmpdir`).
- **Ports occupés** : arrêter les process java (`taskkill /IM java.exe /F`) avant de relancer.

## Notes

- Ports utilisés : 8080 (orchestrateur REST), 8084 (acquéreur REST), 8600 (acquéreur jPOS), 8501 (issuer REST), 8500 (issuer jPOS).
- Les scripts contiennent des chemins et mots de passe adaptés à l'environnement de test local. À externaliser pour un déploiement en recette/production.
