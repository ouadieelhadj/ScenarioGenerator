# Soucis et dépendances — Frontend FuturPayment Switch

## SW-001 — Registre d'interfaces backend absent

- Lot : 1.
- État : ouvert.
- Constat : aucune API membre de registre d'interfaces n'est présente dans le dépôt.
- Impact : le frontend et le BFF peuvent définir le contrat et afficher la capacité, mais pas persister ni activer une interface réelle.
- Protection : aucune fausse donnée et aucune activation locale dans le BFF.

## SW-002 — Maker/Checker générique absent

- Lot : 1.
- État : ouvert.
- Constat : le schéma SQL existe, mais les contrôleurs génériques de soumission, approbation et rejet ne sont pas présents.
- Impact : les actions Maker/Checker restent désactivées jusqu'au raccordement backend.

## SW-003 — Chunks SwitchLab générés dans le build Switch

- Lot : 0/1.
- État : résolu le 6 août 2026.
- Constat : Angular compile les routes lazy SwitchLab présentes dans `app.routes.ts` même lorsqu'elles sont protégées par le guard produit et absentes du menu Switch.
- Impact : les écrans LAB ne sont pas accessibles dans Switch, mais leur code est encore présent sous forme de chunks lazy dans l'artefact.
- Résolution : points d'entrée, routes et registres dynamiques séparés pour legacy, Switch et SwitchLab.
- Vérification : builds Angular Switch et SwitchLab réussis ; zéro classe de composant SwitchLab trouvée dans les JavaScript Switch et zéro classe des nouveaux composants Switch trouvée dans les JavaScript SwitchLab.

## SW-004 — Catalogues GET Acquisition et Issuing absents

- Lots : 2/3.
- État : ouvert.
- Constat : les backends possèdent des commandes de création, soumission et approbation, mais aucun catalogue GET pour les produits, commerçants, points de vente, contrats, terminaux, boutiques, profils, cartes ou contrats issuing.
- Impact : les fonctions sont visibles dans les cockpits, mais les écrans de consultation et les actions restent bloqués.

## SW-005 — Délégation d'identité Maker/Checker incomplète

- Lots : 1 à 7.
- État : ouvert.
- Constat : la validation de session `/api/me/navigation` ne retourne pas une identité signée exploitable par le BFF pour produire les en-têtes Maker/Checker des backends membre.
- Impact : le BFF ne relaie aucune commande nécessitant `X-Caller-ID` ou l'identité checker depuis une valeur contrôlée par le navigateur.

## SW-006 — Résolveur serveur de référence carte absent

- Lots : 2 à 6.
- État : ouvert.
- Constat : les routes d'autorisation existantes attendent des données carte, mais aucun service membre ne résout une référence logique `vault://` ou `card-ref://` vers les données protégées nécessaires.
- Impact : les transactions POS, e-commerce et réseau restent fermées ; aucun PAN n'est demandé ou relayé par le frontend.

## SW-007 — Journal transactionnel membre consolidé absent

- Lots : 2/4.
- État : ouvert.
- Constat : Visa Online fournit une liste locale, mais aucune API transverse ne consolide POS, e-commerce, DMAS, SMS, SWAM et Visa.
- Impact : l'écran Transactions reste une fondation et ne prétend pas fournir un journal opérationnel global.

## SW-008 — Rapprochement et settlement incomplets

- Lot : 5.
- État : ouvert.
- Constat : DMCS acquéreur, SWAM LIS membre et Visa Base II membre exposent des fonctions hétérogènes, sans API consolidée de rapprochement, calcul, validation et comptabilisation du settlement.
- Impact : fichiers et commandes réseau sont identifiés, mais rapprochement et settlement restent `UNAVAILABLE`.

## SW-009 — Listes et preuves 3DS absentes

- Lot : 6.
- État : ouvert.
- Constat : `sg-3ds-member` permet une consultation par identifiant, mais ne fournit ni liste d'authentifications ni registre assaini des preuves ACS/3DS Server.
- Impact : le cockpit 3DS affiche la disponibilité du service, sans inventer une timeline ou des preuves.

## SW-010 — Industrialisation backend partielle

- Lot : 7.
- État : ouvert.
- Constat : déploiements et licences sont raccordés, mais les métriques, alertes durables, audit consolidé, sauvegarde et restauration Maker/Checker ne disposent pas d'API membre complète.
- Impact : ces capacités restent visibles et explicitement bloquées dans l'écran Industrialisation.

## SW-011 — Tableau de bord Switch encore en fondation

- Lot : transverse.
- État : ouvert.
- Constat : `/dashboard` affiche encore le message de fondation pour le produit Switch ; les cockpits Acquisition, Issuing, Réseaux, Clearing, E-commerce/3DS et Industrialisation portent actuellement les états opérationnels.
- Impact : aucun indicateur global Switch ne doit être déclaré testé ou opérationnel depuis le tableau de bord.
- Suite : définir les KPI consolidés et leur API membre avant d'ajouter un agrégat global.
