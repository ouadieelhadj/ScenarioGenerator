# WayPos real E2E

`Invoke-WayPosE2E.ps1` executes the connected WayPos validation without
inventing any card, key, PIN or EMV value.

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
- `-AllowExistingProvisioning` permits HTTP 409 for the terminal/card only.
  Use it solely with a previously verified E2E dataset.

## Required environment

The script validates every required variable before sending a transaction.
The names are listed in its `$requiredNames` array and cover:

- server database, LMK, outbox and PAN-protection configuration;
- terminal identity, currency and MAC mode;
- initial TAK/TPK, PVK halves and MDK metadata;
- the simulator's clear initial TAK and terminal master key;
- a real local card, PIN block/PVV and four coherent EMV vectors;
- the next TAK in both ANSI X9.17 transport form and WayPos-LMK form.

Set these variables in the same controlled shell used to start
`WayPosServer` and `wayPosSimulator`. Do not place their values in this
repository.

## Execution

Start PostgreSQL, `WayPosServer` and `wayPosSimulator`, then run:

```powershell
& .\tests\waypos\Invoke-WayPosE2E.ps1
```

The test stops at the first missing prerequisite, invalid KCV, MAC failure,
PIN failure, ARQC failure, response mismatch, reversal/advice failure or EOD
reconciliation mismatch. A unit-test result is never presented as a
connected E2E result.
