# Kafka Strimzi production — Fraud Monitoring

Cette base Kubernetes sépare trois contrôleurs KRaft et trois brokers, impose TLS, utilise OAuth2/OIDC pour la plateforme et désactive la création automatique des topics.

## Préconditions

- Kubernetes réparti sur au moins trois zones et une StorageClass bloc ;
- Strimzi Cluster Operator 1.1.0 installé avec les CRD `kafka.strimzi.io/v1` ;
- fournisseur OAuth2/OIDC, clients de service et ACL Kafka validés ;
- gestionnaire de secrets approuvé ; aucun secret ne doit être ajouté à ces fichiers ;
- namespace applicatif portant le label `fraud-kafka-access=allowed`.

## Paramètres à rendre avant application

- `FRAUD_KAFKA_STORAGE_CLASS` : StorageClass bloc de production ;
- `FRAUD_KAFKA_BROKER_VOLUME_SIZE` : capacité validée, par exemple `500Gi` ;
- `FRAUD_OAUTH_ISSUER_URI` : issuer exact du tenant OAuth2 ;
- `FRAUD_OAUTH_JWKS_URI` : endpoint JWKS HTTPS du même issuer.

Le rendu doit échouer si une expression `${...}` subsiste. Appliquer ensuite les fichiers `00` à `03` dans l’ordre. Le fichier `04` est un modèle et le fichier `05` un patch du Deployment applicatif.

## Provisionnement d’un flux métier

1. Copier `04-risk-topic.example.yaml` et remplacer le membre et le secteur normalisés.
2. Créer le `KafkaTopic` avec réplication 3 et `min.insync.replicas=2`.
3. Accorder au principal OAuth2 de la plateforme uniquement `WRITE` et `DESCRIBE` ; limiter les consommateurs à `READ` et `DESCRIBE` sur leur périmètre.
4. Créer la route via `PUT /api/fraud/v1/admin/event-routes/{sectorId}/{eventType}` avec le scope `fraud.admin`.
5. Vérifier le préfixe `fraud.<membre>.<secteur>.`. La plateforme refuse tout routage sortant de cet espace.

Exemple de corps non sensible :

```json
{
  "topicTemplate": "fraud.{memberId}.{sectorId}.risk-assessment-completed.v1",
  "schemaVersion": "v1",
  "retentionClass": "STANDARD",
  "enabled": true,
  "priority": 100
}
```

## Secrets et certificats

Le patch applicatif attend deux Secrets créés hors Git :

- `fraud-kafka-oauth-client` : `client-id`, `client-secret`, `token-endpoint-uri` ;
- `fraud-kafka-client-truststore` : `truststore.p12`, `password`.

Le truststore contient la CA courante publiée par Strimzi dans `fraud-kafka-cluster-ca-cert`. Sa rotation doit être automatisée par le mécanisme de déploiement de la banque, puis suivie d’un redémarrage contrôlé. Si l’IdP est signé par une CA privée, cette CA doit également être approuvée côté brokers et client avant activation.

## Contrôles avant GO production

- aucun placeholder ni secret dans le rendu Git ;
- six pods répartis sur trois zones, PVC attachés et Cruise Control prêt ;
- certificat serveur vérifié, hostname checking actif et échec avec une CA inconnue ;
- jeton expiré, mauvais issuer et principal sans ACL tous refusés ;
- publication idempotente avec `acks=all`, puis test de perte d’un broker ;
- route absente/désactivée laissée en reprise Outbox, sans topic de repli en production ;
- sauvegarde/restauration, supervision, quotas et capacité validés par l’exploitation.
