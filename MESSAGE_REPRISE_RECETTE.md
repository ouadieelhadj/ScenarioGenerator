# Message de reprise pour le poste RECETTE

Copier le bloc suivant dans toute nouvelle session ou le transmettre à tout
intervenant chargé d'exécuter la recette.

```text
Tu travailles sur le poste RECETTE du projet ScenarioGenerator.

Le poste d'origine est LAB/DEV : les développements, corrections et commits y
sont réalisés. Le poste RECETTE récupère les commits et exécute les validations.

Dépôt :
https://github.com/ouadieelhadj/ScenarioGenerator.git

Branche :
codex/portail-rbac-maker-checker

État de la reprise au 29 juillet 2026 :

- la dernière version a déjà été récupérée sur le poste RECETTE ;
- le commit présent et testé doit être :
  `29a17b6 — Fix MAC on SWAM network acknowledgements` ;
- le test d'interopérabilité Way4 a déjà été exécuté avec cette version ;
- les logs de notre membre SWAM sont disponibles sur le poste RECETTE ;
- la commande et le résultat M6 du calcul de MAC côté SWAM sont également
  disponibles pour comparaison.

Ne refais pas automatiquement un pull. Commence par vérifier l'état local sans
supprimer ni écraser les éventuelles données de recette :

cd /f/MoneyCore/ScenarioGenerator
git status --short
git branch --show-current
git rev-parse --short HEAD
git log -3 --oneline --decorate

Le résultat attendu est la branche
`codex/portail-rbac-maker-checker` au commit `29a17b6`.

Si, et seulement si, le poste n'est pas sur ce commit, arrête l'analyse et
signale l'écart avant toute récupération de version. Ne mélange pas les traces
du test déjà exécuté avec une autre version du code.

Si le dépôt n'existe pas encore :

cd /f/MoneyCore
git clone https://github.com/ouadieelhadj/ScenarioGenerator.git
cd ScenarioGenerator
git fetch origin
git switch --track origin/codex/portail-rbac-maker-checker

Après récupération, lis intégralement les fichiers suivants avant toute
configuration, restauration, compilation ou exécution :

1. MESSAGE_REPRISE_RECETTE.md
2. COMMENT_REPRENDRE_NOUVELLE_SESSION.md
3. SESSION_RESUME.md, en particulier les sections 20 à 22
4. documents/SESSION_RESUME.md, en privilégiant les sections les plus récentes
   sur l'interop Way4, le MAC SWAM et la commande Thales M6
5. deploiement/README.md
6. deploiement/swam/README.md

Priorité actuelle : analyser le test Way4 déjà effectué avec `29a17b6`.

Le correctif présent dans `SwamJposClient.sendAck()` :

- appelle `swamMac.apply(ack)` avant `channel.send(ack)` ;
- exige DE128 sur les ACK réseau ;
- interdit l'envoi si le MAC n'a pas pu être généré.

Le test à analyser porte sur :

- `1804/811` reçu puis `1814/811` émis ;
- `1804/899` reçu puis `1814/899` émis.

À partir des logs du membre et du calcul M6 côté SWAM :

1. corrèle les messages avec DE11/STAN et les horodatages ;
2. vérifie MTI, DE24, DE39, la présence et la longueur de DE128 ;
3. reconstitue le buffer MAC exact utilisé par le membre ;
4. compare le MAC membre, le MAC M6 et les quatre octets placés dans DE128 ;
5. confirme, sans afficher les clés, la clé utilisée pour 811 et pour 899 ;
6. distingue une absence de DE128, une mauvaise clé, un buffer différent, un
   problème de bitmap/encodage ou un rejet Way4.

Produis un verdict séparé pour `1814/811` et `1814/899` :
`CONFORME`, `NON CONFORME` ou `NON CONCLUANT`.

Présente les résultats sous la forme :

Message | STAN | DE39 | DE128 présent | MAC membre | MAC M6 | clé utilisée | verdict

Ne modifie pas le code avant d'avoir comparé les traces réelles et le résultat
M6. En cas d'écart, fournis le premier octet ou champ divergent, la cause
démontrée, le correctif minimal et le scénario exact à retester.

Le `1804/802` reste un sujet séparé. Le chemin générique peut produire un
`1814/802` MACé, mais aucun traitement fonctionnel de sign-off ou de
déconnexion ne doit être inventé sans confirmation de l'équipe SWAM.

COMMENT_REPRENDRE_NOUVELLE_SESSION.md est la source officielle et détaillée. Il
décrit :

- la séparation LAB/DEV et RECETTE ;
- la configuration locale platform.env ;
- la restauration du dump transféré manuellement ;
- la vérification et le démarrage de PostgreSQL avant restauration ;
- le démarrage unitaire du switch et du membre ;
- le bootstrap séparé des clés claires synthétiques de test ;
- le sign-on et la vérification des KCV ;
- les achats dans les deux sens ;
- les EOD et le clearing LIS ;
- les critères de réussite et les tests différés avec systèmes réels.

Les chemins de ce poste sont locaux. Ne suppose jamais qu'ils correspondent à
ceux de LAB/DEV. Utilise `platform.env` avec les valeurs validées sur RECETTE.
La génération crée également `platform-path.sh`. Au début de chaque session,
charge-le avec `source platform-path.sh` pour configurer le `PATH` du terminal.
Le JDK détecté doit contenir `java` et `javac` et ne doit jamais être le JBR
d'IntelliJ. Le bytecode cible reste Java 21. JDK 26 est accepté avec la pile de
tests actualisée Mockito `5.23.0` / Byte Buddy `1.18.7`.

Avant la restauration, `check-postgres.sh --start` teste PostgreSQL et le démarre
si nécessaire à partir de `POSTGRES_SERVICE_NAME` ou `PGDATA` dans
`platform.env`. Sans cible configurée, il s'arrête sans modifier la base.

DB_PASSWORD et SWAM_E2E_KEK_CLEAR doivent être saisis ou exportés uniquement
dans le terminal. Ne les écris pas dans platform.env, Git, un rapport ou une
conversation.

L'opérateur RECETTE est autorisé à bootstrapper des clés claires exclusivement
synthétiques et dédiées aux tests, séparément côté switch et côté membre. Cette
autorisation ne s'applique jamais à une clé réelle ou à la production.

Ne lance aucun raccordement avec un switch ou un membre réel sans instruction
explicite, environnement autorisé, fenêtre de test et accords réseau/HSM.

Si une anomalie est détectée, communique le hash du commit testé et des traces
assainies à LAB/DEV. Ne développe pas directement la correction en RECETTE.

Vecteur local de controle M6 valide le 29 juillet 2026 :

- la capture correspond au message entrant `1804/899`, STAN `580401` ;
- commande : `M6|0|0|01|1|003|U<ZMK sous LMK>|0061|<buffer>` ;
- `0061` hexadecimal = 97 octets ;
- le buffer contient les DE bruts, avec prefixes LLVAR/LLLVAR, sans MTI,
  bitmap ni DE128 ;
- les deux composantes ZMK donnent par XOR une ZMK de KCV `F6EE59` ;
- calcul local ISO 9797 Algorithme 1, 3DES-CBC, IV nul, padding Method 1 :
  `MAC8=8CA9A2A02BD2AA01`, donc `DE128=8CA9A2A0`.
- la TAK fraiche recue dans `P10`, dechiffree localement sous cette ZMK, a le
  KCV `09C354` ; sur le meme buffer elle donne
  `MAC8=1D150C9951CD1117`, donc `DE128=1D150C99` ;
- le `DE128=E47C1B48` recu dans le `1804/899` ne correspond donc ni au calcul
  ZMK ni au calcul TAK fraiche sur ce buffer exact.

La valeur `U...` visible dans la capture est la meme ZMK chiffree sous la LMK
du HSM SWAM ; ce n'est pas sa valeur claire. Le retour `M7` SWAM doit donc
produire `8CA9A2A0` sur ce buffer exact. Si le resultat differe, relever le
retour M7 complet assaini et verifier en priorite la cle sous LMK, la longueur
`0061`, puis le premier octet divergent du buffer. Pour expliquer le MAC
entrant `E47C1B48`, demander aussi le KCV de la TAK effectivement utilisee par
le switch emetteur, sans demander ni transmettre sa valeur claire.

LAB/DEV et RECETTE travaillent sur la branche commune
`codex/portail-rbac-maker-checker` avec la répartition suivante :

- RECETTE modifie uniquement `deploiement/resultat_recette.md` ;
- LAB/DEV modifie le code et `deploiement/reponse_labdev.md` ;
- avant chaque modification, chacun exécute `git pull --ff-only` ;
- avant chaque push, chacun refait `git fetch` puis `git pull --rebase` ;
- aucun push forcé n'est autorisé sur cette branche commune.
```
