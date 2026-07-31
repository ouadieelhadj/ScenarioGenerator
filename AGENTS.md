# ScenarioGenerator - Instructions permanentes de reprise

Ce fichier est la porte d'entree obligatoire de toute nouvelle session
Codex ouverte sur `D:\MoneyCore\ScenarioGenerator`.

## Reprise automatique obligatoire

Avant toute analyse, modification, test, commit ou push :

1. Lire entierement le document de reprise du chantier demande :
   - WayPosServer / wayPosSimulator : `POS_REPRISE.md` ;
   - module cartes et issuing : `REPRISE_ISSUING.md` ;
   - DMCS / DMAS : `REPRISE_DMCS_DMAS.md` ;
   - SWAM LIS : `SESSION_REPRISE_SWAM_LIS.md` ;
   - contexte historique general : `SESSION_RESUME.md`, uniquement si les
     documents specialises ne suffisent pas.
2. Executer en lecture seule :
   - `git status --short --branch` ;
   - `git log -8 --oneline --decorate`.
3. Identifier le jalon exact, les derniers tests reussis, le premier travail
   non termine et les blocages.
4. Verifier si une autre tache Codex est active sur le meme repertoire avant
   de modifier les memes fichiers.
5. Afficher a l'utilisateur une synthese courte de la reprise avant de
   continuer le travail demande.

Ne jamais reprendre simultanement DMCS/DMAS et WayPos par simple supposition.
La derniere demande explicite de l'utilisateur determine le chantier actif.

## Etat de reprise actuel

- Chantier actif de la presente reprise : WayPos, jalon 5/6,
  E2E reel PIN-ARQC-ARPC-repeat/reversal/advice-reconciliation-EOD.
- Jalon WayPos 3/6 termine : outbox et adaptateurs DMAS Member, SWAM Member
  et Mastercard SMS valides par une non-regression agregee de 64 tests sans
  echec.
- Le jalon 4 est termine : simulateur Basic/Extended, messages systeme,
  reconciliation, champs Extended, validation de correlation, repeat exact
  et scenarios REST.
- Le jalon 5 est en cours : bootstrap TAK/TPK sous LMK et harnais connecte
  termines. La non-regression de reference atteint 129 tests sans echec,
  dont le bootstrap ANSI X9.17 sous TAMK/TPMK triple longueur.
  Le harnais existe en PowerShell et en Git Bash sous `tests/waypos/` ;
  les deux variantes ont le meme contrat fonctionnel et de securite.
  Le guide operateur complet est
  `tests/waypos/INSTRUCTIONS_RECETTE_DU_REPO_A_LA_FINALISATION.md`.
  L'E2E reel attend les secrets et quatre vecteurs ARQC/ATC de recette ;
  ne jamais les remplacer par des valeurs fictives. Relire `POS_REPRISE.md`
  pour le point exact.
- DMCS/DMAS est un chantier separe. Son premier lot publie porte le tag
  `ValidationDmcsDmasFirst`. Relire `REPRISE_DMCS_DMAS.md` avant toute reprise.
- Le projet documentaire Way4 Knowledge Base se trouve dans
  `E:\Way4-Knowledge-Base` et ne doit pas etre confondu avec le code WayPos.

## Protection du travail existant

- Le worktree peut contenir de nombreux changements utilisateur ou provenant
  d'autres jalons. Les conserver integralement.
- Ne jamais utiliser `git reset --hard`, `git checkout --`, `git clean` ou
  une suppression recursive pour remettre le depot a zero.
- Ne jamais faire `git add .` ou `git add -A`.
- Avant un commit, inventorier les fichiers et ajouter uniquement le
  perimetre explicitement valide.
- Ne pas commit/push sans demande ou validation explicite de l'utilisateur.
- Ne pas modifier les documents sources PDF.

## Regles monetiques et securite

- Implementation ISO avec jPOS.
- Ne jamais journaliser ou persister un PIN clair, une cle claire reelle, un
  PAN complet ou un secret d'environnement.
- Les PIN blocks restent opaques hors HSM.
- Aucune cle, carte, reference ou reponse d'approbation fictive ne doit
  masquer un raccordement reel manquant.
- DMCS/DMC : DE31/ARN reste volontairement bloque tant que la valeur reelle
  et sa regle ne sont pas disponibles.
- WayPos Card Control : conserver le refus explicite tant que la generation
  reelle des issuer scripts EMV n'est pas raccordee.
- Conserver l'idempotence, les liens vers la transaction originale et les
  separations de proprietaires.

## Validation

Utiliser le Maven embarque :

`D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd`

et le cache :

`D:\MoneyCore\.m2\repository`

Pour WayPos et ses adaptateurs, la non-regression de reference couvre :

- `sg-common` ;
- `sg-way-pos-server` ;
- `sg-way-pos-simulator` ;
- `sg-mc-dmas-member` ;
- `sg-swam-acquirer` ;
- `sg-mc-sms-acquirer` ;
- `sg-mc-sms-issuer`.

Toujours distinguer :

- echec fonctionnel ou de compilation ;
- dependance absente du cache ;
- timeout d'outil ;
- blocage de recette par environnement, secrets ou services externes.

## Fin ou interruption de session

Avant de terminer ou de changer de projet :

1. mettre a jour le document de reprise specialise ;
2. indiquer le jalon et le premier travail non termine ;
3. lister les fichiers ajoutes/modifies dans la session ;
4. consigner les commandes de test et leurs resultats exacts ;
5. signaler tout processus encore actif ;
6. ne jamais presenter comme termine un E2E non execute.
