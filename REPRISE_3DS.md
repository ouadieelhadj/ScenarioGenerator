# Reprise du chantier 3DS

## Etat au 2 aout 2026

Le socle 3DS sandbox est developpe et raccorde a l'achat e-commerce. Les trois
scenarios de reference ont ete executes avec succes dans une meme session :

- frictionless national : `RC=00`, `3DS=AUTHENTICATED` ;
- challenge national : `RC=00`, `3DS=AUTHENTICATED` ;
- challenge international : `RC=00`, `3DS=AUTHENTICATED`.

La verification cryptographique et la consommation anti-rejeu ont ensuite ete
ajoutees. Le test cible Acquisition + contexte 3DS membre passe (3 tests, zero
echec). Le harnais complet avec cette verification a egalement repasse les
trois achats avec succes et a arrete tous ses processus.

Le chantier est pret a etre sauvegarde sur la branche
`codex/adding-issuing-module`. Le perimetre de sauvegarde exclut explicitement
les changements WayPos, SWAM, DMAS et les fichiers de cles deja presents dans
le worktree.

## Modules

- `sg-3ds-member` : 3DS Server et ACS membre ;
- `sg-3ds-network-simulator` : DS Visa/Mastercard et ACS externe simule ;
- `sg-merchant-site-simulator` : sites national/international ;
- `sg-visa-mastercard-gateway-simulator` : passerelle financiere ;
- `sg-acquiring` : validation de la preuve puis routage financier ;
- `sg-common` : contrats 3DS partages.

## Tests et commandes reussis

- build sans tests des modules 3DS/acquiring/gateway : succes ;
- tests cibles initiaux : 6 tests, zero echec ;
- tests apres verification anti-rejeu : 3 tests, zero echec ;
- `bash ./tests/3ds/e2e/run-all-scenarios.sh` : trois achats approuves,
  `RC=00`, arret de tous les PID.

Sur cette machine, utiliser au besoin :

```bash
ECOMMERCE_E2E_STARTUP_TIMEOUT_SECONDS=240 \
bash ./tests/3ds/e2e/run-all-scenarios.sh
```

## Premier travail non termine

Le moteur financier Visa off-us n'existe pas. La passerelle retourne donc une
indisponibilite explicite et ne fabrique jamais d'approbation Visa. La prochaine
etape fonctionnelle est de developper ce module Visa, puis de raccorder la
passerelle. Pour un passage production 3DS, il faudra remplacer les simulateurs
par des composants/certificats homologues et realiser la certification reseau.

## Processus

Aucun processus du harnais 3DS n'est reste actif apres les tests.

## Etat Git de la sauvegarde

- branche : `codex/adding-issuing-module` ;
- dernier commit anterieur au chantier : `6798eb2` ;
- sauvegarde creee : commit `feat(3ds): add authenticated ecommerce flows`
  (hash final visible avec `git log -1 --oneline`) ;
- aucun secret du fichier local `runtime/issuing-connected-e2e/connected-e2e.env`
  ne fait partie de Git.

## Documentation

- `tests/3ds/README.md` ;
- `documents/design/platform/ARCHITECTURE_PLATEFORME_MONETIQUE_V1.md` ;
- cadrage source externe :
  `E:/Way4-Knowledge-Base/03_Guides/3DS/CADRAGE_MODULE_3DS_DE_BOUT_EN_BOUT.md`.
