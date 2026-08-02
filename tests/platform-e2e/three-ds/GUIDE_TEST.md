# Test E2E 3DS et achat authentifie

## Objectif

Ce parcours sandbox valide les trois cas 3DS deja implementes, suivis d'une
autorisation locale reelle dans le moteur Issuing.

```mermaid
sequenceDiagram
    participant Site as Merchant Site Simulator
    participant S as 3DS Server membre
    participant D as DS Visa/Mastercard simule
    participant A as ACS membre
    participant Q as Acquiring
    participant I as Issuing
    Site->>S: AReq
    S->>D: AReq signe
    D->>A: authentification
    alt frictionless
        A-->>Site: ARes Y
    else challenge
        Site->>A: CReq avec OTP sandbox
        A-->>Site: CRes + preuve
    end
    Site->>Q: achat + ECI + preuve 3DS
    Q->>S: verification et consommation anti-rejeu
    Q->>I: autorisation
    I-->>Site: RC=00
```

## Configuration

Dans `runtime/platform-e2e/platform-e2e.env`, definir deux secrets HMAC
sandbox d'au moins 32 caracteres et un OTP sandbox :

```dotenv
THREE_DS_SANDBOX_HMAC_KEY=
THREE_DS_NETWORK_SANDBOX_HMAC_KEY=
THREE_DS_SANDBOX_CHALLENGE_OTP=
THREE_DS_PROGRAM=MASTERCARD
```

Ce sont des secrets locaux de test, jamais des cles de production.

## Execution et criteres

```bash
bash tests/platform-e2e/three-ds/run-all.sh
```

Les trois achats attendus sont : national frictionless, national challenge et
international challenge. Chacun doit produire `RC=00` et
`authenticationStatus=AUTHENTICATED`. Le DS est fonctionnel mais non certifie
EMVCo/Visa/Mastercard ; une carte Visa off-us reste hors de ce parcours tant
que son moteur financier reel n'est pas raccorde.
