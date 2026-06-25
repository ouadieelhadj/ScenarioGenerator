# Tests DMAS — flux orchestré sur connexion permanente jPOS

Scripts réutilisables pour valider le flux d'autorisation DMAS de bout en bout :
**générateur (sg-generator-orchestrator) → REST 8084 → sg-dmas-acquirer → connexion permanente jPOS → sg-dmas-issuer**.

## Prérequis
- Les 3 modules démarrés : sg-generator-orchestrator (8080), sg-dmas-acquirer (8084), sg-dmas-issuer (8501)
- Config orchestrator : `tps.engine.mode: dmas` (sinon le flux part en socket interne vers sg-issuer 8200)
- Login partout : admin / Admin123!
- Le `transport:jpos` est injecté par TpsEngine + DmasMapper (sg-generator-orchestrator), donc tout le flux orchestré emprunte la connexion permanente.

## Scripts (à lancer depuis tests/dmas/)
| Script | Rôle |
|--------|------|
| `lib-auth.sh` | Fonctions communes (URLs, get_token sans jq). Sourcé par les autres. |
| `00-signon.sh` | Active la session permanente jPOS. **À lancer en premier** (sinon pushAndWait échoue). |
| `01-create-card.sh [pan] [solde]` | Crée/MAJ une carte de test côté issuer. Défaut : PAN 5321962145453348, solde 1000000 centimes. |
| `02-create-test-simple.sh [pan]` | Crée un test SIMPLE (1 autorisation, sans tpsSteps). Retourne un `id` = testId. |
| `03-create-test-tps.sh [pan]` | Crée un test TPS (mode CHARGE : 5 TPS / 3s). Retourne un `id` = testId. |
| `04-run-execution.sh <testId>` | Exécute un test via l'orchestrator (POST /api/executions/start/{testId}). |
| `05-check-logs-jpos.sh` | Vérifie le passage par jPOS (logs acquéreur + issuer). |

## Parcours type
```bash
cd tests/dmas
./00-signon.sh                    # session permanente
./01-create-card.sh               # carte avec solde
./02-create-test-simple.sh        # -> note l'id (ex: 12)
./04-run-execution.sh 12          # exécute
./05-check-logs-jpos.sh           # doit montrer Transport=JPOS + APPROUVE -> 00
# puis le TPS :
./03-create-test-tps.sh           # -> note l'id (ex: 13)
./04-run-execution.sh 13
./05-check-logs-jpos.sh           # rafale d'autorisations jpos
```

## Critère de succès
- Acquéreur (sg-dmas-acquirer) : `[DMAS-AUTH] Transport = JPOS` + `<- 0110 DE39=00 approved=true`
- Issuer (sg-dmas-issuer) : `[JPOS-CLI] 0100 AUTORISATION recue` + `[DMAS-ISS] Decision : APPROUVE -> 00`
- STAN sur 6 chiffres (conforme DE11 n-6, spec CIS p.297)
