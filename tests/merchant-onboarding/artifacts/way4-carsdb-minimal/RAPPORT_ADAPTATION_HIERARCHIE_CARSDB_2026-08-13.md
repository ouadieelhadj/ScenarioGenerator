# Rapport d'adaptation de la hiérarchie WAY4 CARSDB

Date : 13 août 2026
Statut : candidat technique validé, import WAY4 non exécuté
`validatedForImport=false`

## Candidat produit

- Fichier : `xadvapl000100_00004.225`
- SHA-256 : `EDE5FD42E50E9742C5E5C8768F410C3F5B34AFC633CA4262BD6DACE5494FCF80`
- XSD officiel : conforme
- Contenu : 1 client, 1 PDV et 1 TPE

Le numéro `00004` est volontairement conservé. Deux tentatives annulées ont consommé des valeurs de la séquence PostgreSQL, dont le comportement normal n'est pas transactionnel. Aucun retour arrière ni renumérotation forcée n'a été effectué.

## Structure contrôlée

La hiérarchie générée est :

`ARGROUP → ARCHAIN → AROUTLET → ARPOS`

Chaque bloc `Product` contient uniquement `ProductCode1`. Le fichier ne contient aucun élément `AccountScheme` ni `ServicePack`.

## Valeurs métier conservées

- SIC : `5992`
- MID : `990001000000001`
- TID : `99000001`
- contrat commerçant : `LCAR00000001`
- RegNumber métier : conservés ; seuls `-GROUP` et `-CHAIN` ont été ajoutés pour les deux niveaux requis.

La preuve PostgreSQL confirme qu'aucun nouvel MID, TID ou numéro de contrat commerçant n'a été alloué. Le rejeu avec la même clé d'idempotence restitue le même fichier et la même empreinte.

## Validation exécutée

- non-régression : 94 tests recensés, dont 90 exécutés sans échec ni erreur et 4 tests PostgreSQL ignorés hors activation CARSDB ;
- preuve PostgreSQL ciblée : 1 test exécuté, aucun échec, aucune erreur ;
- validation XSD officielle : réussie ;
- import WAY4 : non exécuté.
