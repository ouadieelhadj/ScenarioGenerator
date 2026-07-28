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

Commence par récupérer la dernière version de cette branche sans supprimer les
éventuelles modifications locales :

cd /f/MoneyCore/ScenarioGenerator
git status --short
git fetch origin
git switch codex/portail-rbac-maker-checker
git pull --ff-only origin codex/portail-rbac-maker-checker
git log -5 --oneline --decorate

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
3. deploiement/README.md
4. deploiement/swam/README.md

COMMENT_REPRENDRE_NOUVELLE_SESSION.md est la source officielle et détaillée. Il
décrit :

- la séparation LAB/DEV et RECETTE ;
- la configuration locale platform.env ;
- la restauration du dump transféré manuellement ;
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
```
