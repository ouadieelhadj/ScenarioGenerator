# Prochains modules

Cet espace conserve les deux futurs produits à développer séparément du switch
et des simulateurs réseau existants.

## Modules

1. [`card-issuing-3ds`](card-issuing-3ds/README.md) : émission et gestion du
   cycle de vie des cartes, autorisation émetteur et 3-D Secure.
2. [`merchant-acquiring`](merchant-acquiring/README.md) : acquisition
   commerçant pour TPE et e-commerce, tarification, clearing et settlement.

## Principes communs

- applications, bases de données et cycles de livraison indépendants ;
- intégration avec le switch par API versionnée et événements idempotents ;
- aucun partage direct des tables entre les produits ;
- registre comptable immuable en partie double pour les mouvements financiers ;
- références HSM uniquement : aucune clé cryptographique stockée en clair ;
- tokenisation ou masquage du PAN dans les journaux ;
- audit métier et technique complet ;
- haute disponibilité, observabilité et reprise après incident prévues dès la
  conception ;
- conformité PCI DSS, PCI PIN et exigences EMV à intégrer au plan de
  certification.

## Ordre de réalisation proposé

1. contrats communs avec le switch ;
2. fondations sécurité, audit et observabilité ;
3. MVP `card-issuing-3ds` ;
4. MVP `merchant-acquiring` ;
5. intégration de bout en bout ;
6. durcissement, charge, résilience et certification.
