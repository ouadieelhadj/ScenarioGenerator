# WayPos real E2E

Two equivalent entry points execute the connected WayPos validation without
inventing any card, key, PIN or EMV value:

- `Invoke-WayPosE2E.ps1` for Windows PowerShell;
- `Invoke-WayPosE2E.sh` for Git Bash.

The complete RECETTE workflow, from Git update to the final PASS/FAIL report,
is documented in
`INSTRUCTIONS_RECETTE_DU_REPO_A_LA_FINALISATION.md`.

It covers:

- terminal and local BIN route provisioning;
- initial TAK/TPK bootstrap under the WayPos LMK;
- PVK and local card provisioning;
- dynamic TAK exchange in ANSI X9.17 format, including terminal status
  confirmation and MAC verification;
- real PIN verification;
- ARQC validation and ARPC return;
- exact repeat;
- reversal;
- final advice;
- reconciliation/EOD.

## Safety contract

- Clear terminal keys are supplied only to the simulator process through
  environment variables.
- The server administration APIs accept only keys already encrypted under
  its LMK.
- The script never prints a PAN, PIN block, clear key, key under LMK or
  complete DE55.
- Four distinct, chronologically increasing EMV/ARQC vectors are mandatory.
  Reusing one ARQC is intentionally rejected by the server's ATC replay
  protection.
- PowerShell option `-AllowExistingProvisioning`, or Git Bash option
  `--allow-existing-provisioning`, permits HTTP 409 for the terminal/card
  only. Use it solely with a previously verified E2E dataset.

## Required environment

Both scripts validate the same required variables before sending a
transaction. Their names are listed in `$requiredNames` for PowerShell and
`required_names` for Git Bash. They cover:

- server database, LMK, outbox and PAN-protection configuration;
- terminal identity, currency and MAC mode;
- initial TAK/TPK, PVK halves and MDK metadata;
- the simulator's clear initial TAK and terminal master key;
- a real local card, PIN block/PVV and four coherent EMV vectors;
- the next TAK in both ANSI X9.17 transport form and WayPos-LMK form.

Set these variables in the same controlled shell used to start
`WayPosServer` and `wayPosSimulator`. Do not place their values in this
repository.

## Prerequisites common to both modes

Before running either script:

1. load all required environment variables in the controlled shell;
2. start PostgreSQL on port `5432`;
3. start `WayPosServer` REST/ISO on ports `8530`/`8531`;
4. start `wayPosSimulator` REST on port `8532`;
5. verify that all three processes inherit the coherent RECETTE dataset.

The service URLs may be overridden in either mode. Never place the values of
the required environment variables in this repository or in a command-line
argument.

## Option A - Windows PowerShell

From the repository root:

```powershell
& .\tests\waypos\Invoke-WayPosE2E.ps1
```

With custom REST URLs:

```powershell
& .\tests\waypos\Invoke-WayPosE2E.ps1 `
    -ServerBaseUrl "http://localhost:8530" `
    -SimulatorBaseUrl "http://localhost:8532"
```

For a previously verified provisioning dataset only:

```powershell
& .\tests\waypos\Invoke-WayPosE2E.ps1 -AllowExistingProvisioning
```

## Option B - Git Bash

The Git Bash version requires:

- Bash 4 or later;
- `curl`;
- `timeout`;
- Python 3, used only to validate JSON responses without printing secrets;
- `cygpath` when `WAY_POS_LMK_FILE` uses a Windows drive path.

Open Git Bash, go to the repository root, export the controlled RECETTE
environment, and run:

```bash
./tests/waypos/Invoke-WayPosE2E.sh
```

If the executable bit was not preserved by the Windows checkout:

```bash
bash ./tests/waypos/Invoke-WayPosE2E.sh
```

With custom REST URLs:

```bash
./tests/waypos/Invoke-WayPosE2E.sh \
  --server-base-url "http://localhost:8530" \
  --simulator-base-url "http://localhost:8532"
```

For a previously verified provisioning dataset only:

```bash
./tests/waypos/Invoke-WayPosE2E.sh --allow-existing-provisioning
```

Do not invoke `bash.exe` directly from a non-Git-Bash shell. Open Git Bash or
use `git-bash.exe`; this ensures that its POSIX paths and temporary directory
are initialized correctly.

## Functional equivalence

| Step | PowerShell | Git Bash |
|---|---|---|
| Validate all variables and formats | Yes | Yes |
| Check LMK file, PostgreSQL and health | Yes | Yes |
| Provision terminal, TAK/TPK, PVK/MDK, card and BIN route | Yes | Yes |
| Execute and confirm ANSI X9.17 key change | Yes | Yes |
| Verify response and confirmation MAC | Yes | Yes |
| Execute PIN–ARQC–ARPC scenarios | Yes | Yes |
| Execute EOD, repeat, reversal and final advice | Yes | Yes |
| Stop at first error without printing secrets | Yes | Yes |

The only differences are shell syntax and option names. The REST requests,
validation conditions, operation order and success criteria are identical.

## Result interpretation

The test stops at the first missing prerequisite, invalid KCV, MAC failure,
PIN failure, ARQC failure, response mismatch, reversal/advice failure or EOD
reconciliation mismatch. A unit-test result is never presented as a
connected E2E result.

The final `SUCCESS` message is emitted only after all four scenarios complete.
If the script stops earlier, the E2E RECETTE result remains not validated.

## Git Bash pas à pas : ServerPOS, simulateur et RKI

Les scripts de `tests/waypos/gitbash/` permettent de tester séparément chaque
étape, tout en chargeant par défaut
`runtime/issuing-connected-e2e/connected-e2e.env` :

```bash
bash ./tests/waypos/gitbash/start-serverpos.sh
bash ./tests/waypos/gitbash/bootstrap-rki-test.sh
bash ./tests/waypos/gitbash/start-pos-simulator.sh
bash ./tests/waypos/gitbash/rki-exchange.sh
bash ./tests/waypos/gitbash/rki-sign-confirm.sh
```

Suivi simultané des deux journaux dans un autre terminal Git Bash :

```bash
bash ./tests/waypos/gitbash/tail-waypos-logs.sh
```

Dans ce parcours, le « sign » est la confirmation de l'échange RKI :

- `bootstrap-rki-test.sh` active la TAK initiale sous la LMK locale et génère
  une TAK sous TAMK ainsi qu'une TPK sous TPMK, sans retourner les clés ;
- `rki-exchange.sh` envoie le premier `0800/960000`, vérifie le MAC et importe
  TAK/TPK sous les clés maîtres TAMK/TPMK ;
- `rki-sign-confirm.sh` envoie le second `0800/960000` avec les statuts des
  clés importées et exige une réponse `00` avec MAC valide.

Le simulateur accepte simultanément `WAY_POS_TAMK_HEX` et
`WAY_POS_TPMK_HEX`. `WAY_POS_TAK_HEX` reste également obligatoire : c'est la
TAK initiale cohérente avec le profil du terminal ServerPOS, utilisée pour
protéger la première demande RKI. Les scripts s'arrêtent avant connexion si
elle est absente ; ils ne la remplacent jamais par une valeur inventée.

Arrêt des deux processus :

```bash
bash ./tests/waypos/gitbash/stop-waypos.sh
```

Les sorties console sont conservées dans `runtime/way-pos-gitbash/logs/` et
les PID dans `runtime/way-pos-gitbash/pids/`. Aucune clé claire n'est affichée
par les scripts.
