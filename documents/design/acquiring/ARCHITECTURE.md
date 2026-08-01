# Architecture du module Acquisition

## Responsabilites

`sg-acquiring` est le proprietaire fonctionnel des donnees d'acceptation :

- produit d'acceptation versionne ;
- commercant et point de vente ;
- contrat commercant et contrat equipement ;
- terminal physique, affectation et cycle de provisioning ;
- boutique et profil d'acceptation e-commerce.

`sg-way-pos-server` reste le runtime monétique du TPE. Il ne cree pas le
commercant ni le terminal : il recoit une projection technique produite par
Acquisition lorsque le terminal est provisionne.

## Modele de contrat partage

Issuing et Acquisition utilisent la meme table physique `payment_contract`.
La colonne `contract_type` porte la distinction :

| Type | Beneficiaire | Parent |
| --- | --- | --- |
| `ISSUING_CARD` | porteur | optionnel selon le produit |
| `ACQUIRING_MERCHANT` | commercant | aucun |
| `ACQUIRING_DEVICE` | commercant | contrat `ACQUIRING_MERCHANT` actif |

Les details propres a l'acquisition restent dans
`acquiring_contract_detail` et `acquiring_device_contract_detail`. Cette
separation conserve le contrat commun sans introduire de colonnes TPE ou
e-commerce dans le coeur Issuing.

## Flux TPE

1. creer et approuver le produit d'acceptation ;
2. creer, soumettre et approuver le commercant ;
3. creer et approuver le contrat commercant ;
4. creer et approuver son contrat equipement enfant ;
5. enregistrer le terminal et l'affecter au contrat equipement ;
6. provisionner le terminal vers ServerPOS ;
7. activer le terminal apres retour positif de ServerPOS.

Le connecteur ServerPOS est fail-closed. Un echec laisse le terminal en
`PROVISIONING`, ce qui permet une reprise idempotente. Si ServerPOS repond
`409`, Acquisition n'accepte la reprise que si le TID, le MID et la
configuration existants sont strictement identiques.

## Flux e-commerce

Le module gere la boutique, ses URL autorisees et un profil d'acceptation
logique rattache a un contrat commercant actif compatible e-commerce. Le
simulateur envoie l'achat a Acquisition, qui valide le profil et le contrat,
garantit l'idempotence, journalise l'operation et route l'autorisation vers :

- Issuing LanaCash directement pour une carte locale (`00000`) ;
- l'interface banque membre puis le reseau Mastercard DMAS pour une carte
  Mastercard confrere ;
- l'interface banque membre puis le switch SWAM pour une carte confrere SWAM.

La table `pos_bin_routes`, deja utilisee par ServerPOS, est la source de
verite commune. Une route demandee par le simulateur est controlee contre le
BIN ; elle ne peut jamais forcer une carte LanaCash vers un reseau externe.
Dans les bancs DMAS/SWAM, la decision d'une carte confrere appartient au
simulateur explicite de la banque emettrice et non a `sg-card-issuing`.

La route Visa est refusee explicitement jusqu'a son implementation. Aucun
PAN, CVC, PIN block, donnees 3DS ou secret cryptographique n'est persiste dans
`acquiring_ecommerce_transaction`.

L'interface porte deja le point d'extension 3DS, mais le jalon courant refuse
toute valeur autre que `NOT_PERFORMED`. Un futur module 3DS pourra produire
les donnees d'authentification sans modifier le modele commercant/contrat.

## Limites du jalon

Ce jalon couvre l'administration, les contrats, le TPE, la projection
ServerPOS, le routage on-us direct et les parcours confreres via SWAM et DMAS
Mastercard. Le 3-D Secure reel, Visa, le clearing et le settlement restent
hors de ce jalon.
