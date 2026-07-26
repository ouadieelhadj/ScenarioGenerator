# Card Issuing & 3-D Secure

Produit autonome consacré à l'émission de cartes et à l'autorisation côté
émetteur.

## Périmètre

- porteurs, clients et comptes carte ;
- BIN ranges, produits carte et profils ;
- génération du PAN conforme ISO/IEC 7812 ;
- cartes physiques, virtuelles et renouvelables ;
- cycle de vie : création, activation, blocage, remplacement, expiration ;
- plafonds par canal, période, pays, devise et type d'opération ;
- autorisation, advice, reversal et gestion des holds ;
- PIN, PIN block, CVV/CVC, CVV2, iCVV et profils EMV via HSM ;
- personnalisation physique et données EMV ;
- événements, audit, rapprochement et interfaces avec le Core Banking.

## 3-D Secure

Le sous-domaine 3DS devra couvrir le rôle émetteur :

- intégration ou implémentation d'un Access Control Server (ACS) ;
- flux frictionless et challenge ;
- enrôlement du porteur et méthodes d'authentification ;
- évaluation du risque et exemptions réglementaires configurables ;
- gestion des appareils et sessions d'authentification ;
- résultats 3DS transmis au moteur d'autorisation ;
- conservation contrôlée des preuves d'authentification ;
- compatibilité avec les versions EMV 3-D Secure retenues lors du cadrage ;
- homologation EMVCo et réseau traitée comme un chantier distinct.

## Découpage cible

```text
card-domain
card-management
card-authorization
card-security-hsm
card-personalization
card-3ds-acs
card-risk
card-api
card-events
```

## MVP

1. gestion des porteurs, comptes et produits ;
2. émission d'une carte virtuelle ;
3. activation, blocage et plafonds ;
4. autorisation et reversal ;
5. interfaces HSM simulées puis réelles ;
6. ACS 3DS avec parcours frictionless et challenge de test ;
7. API d'administration et audit ;
8. tests contractuels avec le switch.

## Hors MVP

- certification réseau et EMVCo ;
- fabrication industrielle des cartes ;
- tokenisation de réseau ;
- wallet provisioning ;
- programme de fidélité ;
- gestion complète des litiges.
