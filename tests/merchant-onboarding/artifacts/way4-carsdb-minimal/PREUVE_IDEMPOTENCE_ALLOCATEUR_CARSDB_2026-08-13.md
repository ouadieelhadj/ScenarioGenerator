# Preuve d'idempotence — allocateur PostgreSQL CARSDB

## Dispositif contrôlé

- Base réellement connectée : `CARSDB`.
- Activation explicite requise : `WAY4_AURA_EXTERNAL_ALLOCATION_ENABLED=true`.
- Environnement explicite requis : `WAY4_AURA_EXTERNAL_ALLOCATION_ENVIRONMENT=CARSDB`.
- Tout profil contenant `prod` est refusé.
- La base PostgreSQL courante doit réellement s'appeler `CARSDB`.
- Verrou transactionnel : `pg_advisory_xact_lock` sur le type et la clé métier.
- Unicité SQL : `(allocation_type, business_key)` et `(allocation_type, allocated_value)`.

## Premier passage et rejeu

| Type | Clé métier | Premier passage | Rejeu |
|---|---|---:|---:|
| Contrat commerçant | `ONB-198B8A1C` | `LCAR00000001` | `LCAR00000001` |
| MID | PDV `f8f61ca4-3bf0-3c43-9ec4-b941a6442693` | `990001000000001` | `990001000000001` |
| TID | TPE `14904900-9615-3bad-a19b-41a26d241348:1` | `99000001` | `99000001` |

Après le rejeu : 3 allocations persistées, toujours une seule ligne par clé métier. Les compteurs sont restés à `990001000000001`, `99000001` et `1` : aucune séquence n'a progressé lors du rejeu.

Le service de génération complet a ensuite été rejoué avec la même clé d'idempotence. Le nom du fichier et son SHA-256 sont restés identiques.

Test PostgreSQL réel : `Way4ExternalIdentifierAllocatorPostgresqlTest`, 2 tests réussis, aucun échec ni erreur.
