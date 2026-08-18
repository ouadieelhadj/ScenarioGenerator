# Contrats stables des moteurs industriels fraude

Ces contrats isolent le métier FuturPayment des produits techniques installés. Un remplacement d'outil ne modifie ni l'API publique ni le modèle universel de transaction.

| Port métier | Entrée minimale | Sortie minimale | Implémentation retenue |
|---|---|---|---|
| Event Stream | événement `RiskAssessmentCompleted.v1`, clé `memberId + transactionReference` | accusé durable et offset | Kafka KRaft via Strimzi et registre Apicurio |
| Online Feature Store | membre, instrument opaque, transaction, horodatage | vecteur versionné et fraîcheur | Feast, stockage en ligne Valkey |
| Model Inference | vecteur versionné | probabilité, type de fraude, version du modèle, explications | KServe, modèles gouvernés dans MLflow |
| Graph Intelligence | membre et références opaques d'entités | taille du groupe, score collectif, chemins explicatifs | JanusGraph, Cassandra, OpenSearch |

Règles obligatoires :

- `memberId` fait partie de toutes les clés, partitions, requêtes et métriques ;
- aucun PAN, PIN, cryptogramme ou clé TPE n'est publié ;
- les références client, compte, instrument, device, IP et bénéficiaire sont tokenisées ou hachées ;
- les appels temps réel ont un timeout borné et une politique de repli explicite ;
- le résultat persisté conserve les versions des features, du modèle et du schéma ;
- l'outbox PostgreSQL est la source transactionnelle avant publication Kafka et empêche la perte ou le double événement métier.

Le branchement natif des clients Kafka, Feast, KServe et JanusGraph sera activé après installation des outils et recette de leurs versions. Les frontières métier et l'événement versionné sont déjà figés.
