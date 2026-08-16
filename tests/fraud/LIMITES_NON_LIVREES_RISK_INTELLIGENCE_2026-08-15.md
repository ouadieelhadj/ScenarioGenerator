# Limites non livrées — Risk Intelligence

Date : 15 août 2026

| Fonction | État | Condition de finalisation |
|---|---|---|
| Apprentissage IA industriel et génération autonome de contrôles | Non livrés | Historique client labellisé, protocole d'entraînement/validation, seuils acceptés et POC gouverné. |
| Injection de plusieurs millions de transactions | Non exécutée | Infrastructure de performance, jeu synthétique validé et objectifs SLA. |
| Profil ISO 8583 complet et décisions réseau actives | Bloqués | MTI, champs privés, framing, MAC, délais et codes DE39 validés par le client. Les RC restent `00` par défaut. |
| Adaptateur HSM constructeur | Non livré | Modèle HSM, commandes/API et références de clés de recette ; aucune clé claire n'est acceptée. |
| TLS/mTLS, VPN et secrets OAuth2 | Non configurés | Infrastructure et IAM du client. |
| Lecteur BAL/file et agents sectoriels client | Adaptateurs non livrés | Formats BAL, schémas, droits lecture seule, accusés et reprises par secteur. |
| Kafka ou Redpanda | Non installé | Option à retenir uniquement si débit et latence mesurés le justifient. |
| Feature Store spécialisé | Non installé | Le lot 1 utilise des snapshots PostgreSQL versionnés. |
| Base graphe spécialisée | Non installée | Le lot 1 utilise un graphe relationnel PostgreSQL. |

Statut : **GO développement local / NO-GO production**.
