# Périmètres JPA par module

Les entités de `sg-common` sont partagées au niveau du code, mais elles ne
doivent jamais être scannées globalement par une application.

Chaque application déclare `sg.persistence.module`. Cette valeur active une
configuration de `com.staging.sg.common.persistence` qui fournit :

1. la liste explicite des entités JPA gérées ;
2. la liste explicite des repositories Spring Data ;
3. le périmètre de validation Hibernate du module.

| Valeur | Application | Propriétaire métier |
|---|---|---|
| `SWAM_ISSUER` | `sg-swam-issuer` | switch SWAM / `swam_issuer_user` |
| `SWAM_ACQUIRER` | `sg-swam-acquirer` | membre SWAM / `swam_acquirer_user` |
| `SWAM_LIS_MEMBER` | `sg-swam-lis-member` | clearing membre / `swam_lis_member_user` |
| `SWAM_LIS_SWITCH` | `sg-swam-lis-switch` | clearing switch / `swam_lis_switch_user` |
| `MC_DMAS_MEMBER` | `sg-mc-dmas-member` | membre DMAS / `mc_dmas_member` |
| `MC_DMAS_MASTERCARD` | `sg-mc-dmas-mastercard` | Mastercard DMAS / `mc_dmas_mastercard` |
| `MC_SMS_ACQUIRER` | `sg-mc-sms-acquirer` | acquéreur Mastercard SMS |
| `MC_SMS_ISSUER` | `sg-mc-sms-issuer` | émetteur Mastercard SMS |
| `DMCS_ACQUIRER` | `sg-dmcs-acquirer` | acquéreur DMCS |
| `DMCS_ISSUER` | `sg-dmcs-issuer` | émetteur DMCS |
| `ORCHESTRATOR` | `sg-generator-orchestrator` | orchestrateur / `scenario_user` |

## Cartes SWAM

Les cartes ne sont pas un référentiel partagé entre le membre et le switch :

| Application | Entité | Table | Propriétaire SQL |
|---|---|---|---|
| `sg-swam-issuer` | `SwamIssuerCard` | `issuer_swam_cards` | `swam_issuer_user` |
| `sg-swam-acquirer` | `SwamAcquirerCard` | `acquirer_swam_cards` | `swam_acquirer_user` |

Chaque rôle dispose des droits de lecture/écriture uniquement sur sa table. Un
achat membre vers switch débite `issuer_swam_cards`. Un achat switch vers
membre débite `acquirer_swam_cards`.

## Règles

- Une application ne doit pas ajouter `@EntityScan("com.staging.sg.common.entity")`.
- Une application ne doit pas ajouter
  `@EnableJpaRepositories("com.staging.sg.common.repository")`.
- Une nouvelle entité doit être ajoutée uniquement à la configuration de son
  propriétaire métier.
- Une table étrangère au module peut être absente sans bloquer son démarrage.
- Une table appartenant au périmètre déclaré reste contrôlée par
  `spring.jpa.hibernate.ddl-auto=validate`.
- Les tests `PersistenceOwnershipConfigurationTest` empêchent les principaux
  croisements SWAM/DMAS et membre/switch.
