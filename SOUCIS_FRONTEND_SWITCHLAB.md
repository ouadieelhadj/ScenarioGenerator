# Soucis et limites — Frontend SwitchLab

Ce registre accompagne le développement des Lots 1 à 7 de FuturPayment SwitchLab.
Il ne remplace pas les tests d'acceptation, qui seront exécutés avec l'utilisateur après le développement.

## Points ouverts

### SWLAB-001 — Charge transactionnelle partielle

- Lot concerné : Lot 3, avec dépendances sur les Lots 4 à 7.
- État : ouvert.
- Constat : les profils LOAD, STRESS, ENDURANCE et SPIKE exécutent actuellement des sondes réelles de disponibilité sur plusieurs simulateurs.
- Limite : ils ne génèrent pas encore un mélange complet de transactions Visa, Mastercard, SWAM et 3DS.
- Impact : les métriques de disponibilité, taux d'erreur et p95 sont opérationnelles pour les sondes, mais ne constituent pas encore une mesure de performance monétique de bout en bout.
- Suite : raccorder progressivement les adaptateurs transactionnels développés dans les Lots 4 à 7.

### SWLAB-002 — Validation d'acceptation différée

- Lots concernés : tous.
- État : accepté temporairement par l'utilisateur.
- Constat : les compilations sont autorisées, mais les tests automatisés et manuels sont volontairement différés.
- Impact : la conformité fonctionnelle réelle reste à confirmer pendant la session de test commune.
- Suite : conserver un plan de test consolidé et ne déclarer aucun lot validé avant cette session.

### SWLAB-003 — API Mastercard SMS non sécurisable telle quelle

- Lot concerné : Lot 4.
- État : ouvert, bloquant pour les commandes SMS Online.
- Constat : `GET /api/admin/mc/sim/last-key` renvoie actuellement `zmk`, `clear_key` et `encrypted_key` en plus du KCV.
- Impact : le BFF SwitchLab ne peut pas relayer cette réponse sans violer la règle interdisant les clés en clair dans le frontend.
- Protection appliquée : ne jamais proxifier la réponse brute ; une future vue ne pourra exposer que l'état et le KCV tronqué.
- Suite backend : créer une API SMS assainie et des commandes Online explicites, sans matériel de clé.

### SWLAB-004 — PAN par défaut dans l'API SWAM

- Lot concerné : Lot 4.
- État : ouvert, bloquant pour le lancement sécurisé d'une transaction SWAM depuis le frontend.
- Constat : les routes SWAM `/purchase` et `/financial` déclarent un PAN de test par défaut en clair.
- Impact : le BFF ne doit ni exposer ce paramètre au navigateur ni dépendre d'une valeur PAN codée en dur.
- Protection appliquée : les futurs contrats SwitchLab n'accepteront qu'une référence `secret://`, `vault://` ou `env://`.
- Suite backend : ajouter un résolveur de références côté serveur ou une route de scénario prédéfinie sans PAN transmis.

### SWLAB-005 — Endpoints de bootstrap de clés incompatibles avec le frontend

- Lot concerné : Lot 4.
- État : ouvert.
- Constat : les bootstraps DMAS et SWAM acceptent une KEK en clair ; l'injection DMAS accepte également une clé de travail en clair.
- Impact : ces commandes ne peuvent pas être exposées par le BFF SwitchLab.
- Protection appliquée : seule la lecture assainie du statut/KCV DMAS et le déclenchement d'un échange réseau sans clé fournie par le navigateur sont autorisables.
- Suite backend : intégrer un HSM ou un résolveur de références de clés côté serveur et supprimer les paramètres de clés claires.

### SWLAB-006 — Scénarios Online financiers incomplets

- Lot concerné : Lot 4.
- État : ouvert, bloquant pour le critère d'acceptation nominal/refus.
- Constat : DMAS ne possède pas encore de route financière réseau vers membre ; SMS n'a pas de commande Online ; Visa attend une enveloppe ISO Base64 brute ; SWAM attend un PAN.
- Impact : le cockpit peut superviser les réseaux et exécuter un echo DMAS, mais pas encore lancer proprement tous les scénarios financiers nominaux et de refus.
- Suite backend : fournir quatre API de scénarios prédéfinis ou un résolveur de données sensibles entièrement côté serveur.

### SWLAB-007 — Import DMCS par chemin serveur

- Lot concerné : Lot 5.
- État : ouvert, bloquant pour l'import DMCS depuis le frontend.
- Constat : les routes DMCS `/api/dmcs/ipm/incoming` et `/api/dmcs/read` acceptent un paramètre `path` fourni par l'appelant.
- Impact : une exposition directe permettrait la lecture de chemins arbitraires et certaines réponses divulguent des chemins locaux.
- Protection appliquée : ces routes ne sont pas proxifiées par le BFF SwitchLab.
- Suite backend : créer un dépôt contrôlé avec identifiant d'artefact, liste blanche de répertoires et téléchargement audité.

### SWLAB-008 — Dépôt Clearing Visa et preuves incomplets

- Lot concerné : Lot 5.
- État : ouvert.
- Constat : Visa Base II accepte une enveloppe de fichier brute et les moteurs Clearing ne proposent pas tous une route sûre de téléchargement des preuves.
- Impact : le frontend ne peut pas envoyer un contenu clearing brut ni télécharger uniformément les preuves.
- Suite backend : ajouter une API d'artefacts contrôlés et des preuves téléchargeables par identifiant opaque.

### SWLAB-009 — OTP de challenge 3DS exposé

- Lot concerné : Lot 6.
- État : ouvert, critique.
- Constat : la route sandbox `/api/3ds/network/v1/external-acs/sandbox/display` renvoie l'OTP de challenge en clair.
- Impact : cette route ne peut jamais être relayée au frontend ni utilisée comme mécanisme de challenge commercial.
- Protection appliquée : l'adaptateur BFF SwitchLab ignore et bloque cette route.
- Suite backend : fournir un simulateur de saisie séparé ou un mécanisme de challenge à usage unique sans divulgation de l'OTP.

### SWLAB-010 — Paiement e-commerce sans résolveur de références sensibles

- Lot concerné : Lot 6.
- État : ouvert, bloquant pour le parcours complet.
- Constat : les contrats marchands demandent PAN, date d'expiration et parfois données de challenge en clair.
- Impact : le BFF ne peut pas lancer frictionless, challenge puis autorisation depuis le navigateur.
- Protection appliquée : les scénarios sont catalogués mais désactivés ; aucune donnée carte n'est acceptée par les contrats SwitchLab.
- Suite backend : ajouter un coffre/résolveur de références serveur et confirmer le simulateur acquiring e-commerce autonome.

### SWLAB-011 — Industrialisation partielle

- Lot concerné : Lot 7.
- État : ouvert.
- Constat : les traces BFF sont en mémoire, aucun moteur d'alertes persistant n'est présent et la restauration de configuration n'est pas couverte par un workflow Maker/Checker opérationnel.
- Impact : audit durable, alerting et restauration commerciale ne peuvent pas être déclarés prêts pour la production.
- Protection appliquée : sauvegarde limitée à une configuration non sensible ; restauration non exposée ; readiness affichée sans faux succès.
- Suite backend : journal transactionnel durable, alert manager, stockage de sauvegardes signé et workflow Maker/Checker de restauration.

## Points résolus

- Lot 3 : persistance durable JSON configurable ajoutée.
- Lot 3 : analyse contrôlée des manifestes avec rejet des champs sensibles ajoutée.
- Lot 3 : exports PDF et XLSX autonomes ajoutés.
- Lot 3 : compilation Maven et Angular réussie le 5 août 2026, sans tests.
