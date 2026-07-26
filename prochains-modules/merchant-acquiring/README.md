# Merchant Acquiring — TPE & E-commerce

Produit autonome consacré à l'onboarding des commerçants, l'acquisition des
transactions et leur règlement.

## Gestion commerçant

- commerçants, groupes, contrats et bénéficiaires effectifs ;
- KYC/KYB, documents, statuts et workflow de validation ;
- points de vente, MCC, MID et comptes de règlement ;
- plans tarifaires, MDR, frais fixes, taxes et réserves ;
- limites, règles antifraude et gestion du risque ;
- tableaux de bord, relevés et API commerçant.

## Acquisition TPE

- parc de terminaux et gestion des TID ;
- association TID/MID/point de vente ;
- paramètres EMV, contactless et fallback ;
- achat, annulation, remboursement, préautorisation et complétion ;
- ouverture et fermeture de lots ;
- gestion des clés terminal via HSM/TMS ;
- supervision, téléparamétrage et état du terminal ;
- intégration avec un TMS séparé si nécessaire.

## Acquisition e-commerce

- API de paiement et pages de paiement hébergées ;
- tokenisation et coffre de paiement dans un périmètre PCI contrôlé ;
- intégration au module 3DS côté marchand ;
- paiement immédiat, autorisation/capture et capture partielle ;
- remboursement total ou partiel ;
- paiements récurrents et credentials-on-file ;
- webhooks signés, idempotence et protection contre le rejeu ;
- détection de fraude et règles de vélocité.

## Clearing, settlement et rapprochement

- capture et présentation des transactions ;
- lots par commerçant, devise, réseau et date ;
- calcul brut, commissions, interchange, taxes et montant net ;
- registre comptable immuable en partie double ;
- ordres de règlement commerçant ;
- retours bancaires et reprises ;
- rapprochement transactionnel, réseau et bancaire ;
- ajustements, réserves, remboursements et chargebacks ;
- relevés détaillés de settlement.

## Découpage cible

```text
merchant-domain
merchant-onboarding
terminal-management
acquiring-tpe
acquiring-ecommerce
payment-api
pricing
clearing
settlement
ledger
reconciliation
disputes
merchant-api
merchant-events
```

## MVP

1. commerçants, contrats, MID, points de vente et TID ;
2. acquisition TPE simulée ;
3. API e-commerce avec idempotence ;
4. autorisation, capture, reversal et remboursement ;
5. plans tarifaires et calcul du MDR ;
6. constitution et clôture des lots ;
7. settlement net et écritures équilibrées ;
8. rapprochement et relevé commerçant ;
9. tests contractuels avec le switch.

## Hors MVP

- certification des terminaux ;
- TMS industriel complet ;
- passerelles bancaires de paiement en production ;
- gestion avancée des chargebacks ;
- paiement fractionné et financement ;
- orchestration multi-PSP.
