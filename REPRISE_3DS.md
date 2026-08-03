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

## Complement du 3 aout 2026 - parcours navigateur marchand et ACS

Le parcours e-commerce dispose maintenant d'une interface operateur complete :

- la boutique `sg-merchant-site-simulator` permet la saisie manuelle du PAN,
  de l'expiration et du montant ;
- le marchand utilise l'URL ACS retournee par le parcours 3DS et ne connait
  pas en dur l'ACS emetteur ;
- `sg-3ds-member` expose la page ACS LanaCash pour une carte locale ;
- `sg-3ds-network-simulator` expose une page ACS externe sandbox pour les
  scenarios reseau ;
- l'OTP est affiche seulement sous le profil `connected-e2e`, puis saisi par
  l'operateur ;
- le retour marchand reprend le checkout, verifie la preuve 3DS et lance
  l'autorisation financiere ;
- le PAN n'est ni journalise ni persiste par la boutique.

Le harnais detaille est sous `tests/3ds/browser-e2e/`. Il demarre separement
Issuing, la passerelle, Acquisition, le membre 3DS, le simulateur reseau 3DS
et le site marchand. Il fournit aussi le provisionnement, le statut, le suivi
des journaux, l'arret et le controle Playwright.

Validations exactes executees :

- Maven cible : `sg-common` 69 tests, boutique 4 tests, membre 3DS 3 tests,
  simulateur reseau 3DS 3 tests, aucun echec, `BUILD SUCCESS` ;
- packaging des six JAR necessaires : `BUILD SUCCESS` ;
- six sondes HTTP du harnais : `OK` ;
- Playwright Chromium : `1 passed` ;
- resultat navigateur : `APPROVED`, `RC=00`, `LOCAL_ISSUING`,
  `AUTHENTICATED` ;
- captures controlees sous
  `runtime/acquiring-ecommerce-e2e/ui-evidence/`.

Les six services de ce parcours sont volontairement laisses actifs apres la
validation afin que l'utilisateur effectue le test manuel sur
`http://127.0.0.1:8551/`. Ils doivent ensuite etre arretes avec
`bash tests/3ds/browser-e2e/stop.sh`.

Premier travail non termine : le parcours navigateur complet avec ACS externe
et autorisation financiere off-us Visa/Mastercard n'est pas revendique comme
valide. La page ACS externe est implementee et testee isolement ; son E2E
financier depend du routage reseau cible et doit faire l'objet d'une campagne
separee.
