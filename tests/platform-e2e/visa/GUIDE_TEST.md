# Test E2E Visa Online et Base II

## Objectif

Valider le premier circuit Visa off-us : autorisation Online, rejeu
idempotent, presentment Base II et acquittement reseau simule.

```mermaid
sequenceDiagram
    participant G as Gateway e-commerce
    participant O as Visa Online Member
    participant V as VisaNet Simulator
    participant B as Base II Member
    participant N as Base II Network Simulator
    G->>O: 0100 autorisation
    O->>V: message Online
    V-->>O: 0110, DE39=00, DE38, references
    G->>O: rejeu meme idempotencyKey
    O-->>G: reponse identique
    G->>B: presentment lie
    B->>N: fichier CTF, 5 records
    N-->>B: ACCEPTED
```

## Execution

```bash
bash tests/platform-e2e/visa/run-all.sh
```

Aucun secret ni mot de passe n'est requis par ce sandbox autonome. La carte
publique de test reste contenue dans le harnais Visa historique et ne doit pas
etre remplacee par un PAN reel.

Le resultat attendu est une autorisation `APPROVED/00`, une reponse de rejeu
identique, puis un presentment `ACCEPTED` de cinq records. Les preuves sont
sous `runtime/visa-e2e/results/`.

Le transport Visa certifie, VSS/TC46, Edit Package, VROL, chargeback et
pre-arbitrage demeurent fermes tant que les specifications officielles
correspondantes ne sont pas disponibles.
