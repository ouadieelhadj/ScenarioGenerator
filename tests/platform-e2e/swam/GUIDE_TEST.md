# Test E2E SWAM SID et clearing LIS

## Objectif

Valider l'autorisation bidirectionnelle entre membre et switch, puis le cycle
LIS comprenant EOD, fichiers, chargeback, representation et comptabilite.

```mermaid
sequenceDiagram
    participant M as SWAM Member
    participant S as SWAM Switch Simulator
    participant LM as LIS Member
    participant LS as LIS Switch
    M->>S: sign-on + echange ZPK
    M->>S: 5 achats
    S->>M: 5 achats sur la liaison permanente
    M->>LM: EOD et fichier LIS
    S->>LS: EOD et fichier LIS
    LM->>LS: fichier + chargeback
    LS->>LM: fichier + chargeback
    LM->>LS: representation
    LM-->>LS: comptabilite equilibree
```

## Configuration et execution

Le fichier local ignore doit contenir `DB_PASSWORD` et
`SWAM_E2E_KEK_CLEAR`. La KEK doit etre synthetique, reservee au test et ne
doit jamais etre journalisee.

```bash
bash tests/platform-e2e/swam/run-all.sh
```

Le resultat attendu est `RESULTAT : PASSED`, avec autorisations approuvees,
liaison unique bidirectionnelle, fichiers LIS integres, chargebacks recus,
representation et ecritures equilibrees. Ce sandbox ne prouve pas la
compatibilite MAC avec le switch SWAM reel ; l'incident MAC de recette reste
un sujet de raccordement distinct.
