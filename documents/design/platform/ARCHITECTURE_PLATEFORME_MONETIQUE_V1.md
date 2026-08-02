# Architecture de la plateforme monetique - version initiale

## Objectif

Cette version reunit les briques Issuing, Acquisition TPE/e-commerce, 3DS et
les simulateurs de reseaux. Elle separe strictement l'authentification du
porteur (3DS) de l'autorisation financiere (Issuing, SWAM, DMAS ou futur Visa).

Le Directory Server et les ACS externes contenus dans le depot sont des
simulateurs de recette. Ils ne sont pas des produits certifies EMVCo, Visa ou
Mastercard et ne doivent pas etre exposes comme tels en production.

## Vue d'ensemble

```mermaid
flowchart LR
    TPE["TPE / sg-way-pos-simulator"] --> POS["sg-way-pos-server"]
    NAT["Site marchand national"] --> MS["sg-merchant-site-simulator"]
    INT["Site marchand international"] --> MS

    MS --> M3["sg-3ds-member<br/>3DS Server + ACS"]
    MS --> N3["sg-3ds-network-simulator<br/>DS Visa/Mastercard + ACS confrere"]
    M3 <--> N3

    POS --> ACQ["sg-acquiring"]
    MS --> ACQ
    ACQ --> ISS["sg-card-issuing<br/>cartes LanaCash"]
    ACQ --> SWAM["SWAM membre/acquirer<br/>cartes confreres domestiques"]
    ACQ --> GW["sg-visa-mastercard-gateway-simulator"]
    GW --> DMAS["DMAS Mastercard"]
    GW --> VO["sg-visa-online-member"]
    VO <--> VN["sg-visa-visanet-simulator"]
    VO --> B2["sg-visa-base2-member"]
    B2 <--> B2N["sg-visa-base2-network-simulator"]
```

## Responsabilites des modules

| Module | Role | Port recette | Etat |
|---|---|---:|---|
| `sg-card-issuing` | Contrats cartes, soldes et autorisation locale | 8540 | Fonctionnel |
| `sg-acquiring` | Contrats commercants, TPE/e-commerce et routage | 8550 | Fonctionnel |
| `sg-merchant-site-simulator` | Sites national/international et orchestration achat | 8551 | Fonctionnel sandbox |
| `sg-3ds-member` | 3DS Server acquereur et ACS emetteur, separes par API | 8560 | Fonctionnel sandbox |
| `sg-3ds-network-simulator` | DS Visa/Mastercard et ACS confrere | 8561 | Simulateur uniquement |
| `sg-visa-mastercard-gateway-simulator` | Multiplexeur des reseaux financiers | 8563 | Mastercard et Visa raccordables, fermes par defaut |
| `sg-visa-online-member` | Autorisation Visa Online du membre | 8564 | Fonctionnel sandbox non certifie |
| `sg-visa-visanet-simulator` | Reseau VisaNet d'autorisation | 8565 | Simulateur uniquement |
| `sg-visa-base2-member` | Preparation et emission CTF Base II | 8566 | Premier presentment TC05 sandbox |
| `sg-visa-base2-network-simulator` | Controle et acquittement des fichiers Base II | 8567 | Simulateur uniquement |
| `sg-way-pos-server` | Serveur TPE, terminal et commercant issus d'Acquiring | 8530 | Fonctionnel |
| DMAS | Reseau Mastercard simule et membre bancaire | 8083/8084 | Fonctionnel recette |
| SWAM | Switch domestique simule et membre bancaire | 8511/8094 | Fonctionnel recette |

## Achat e-commerce national avec carte LanaCash

```mermaid
sequenceDiagram
    participant C as Porteur / site national
    participant M as Merchant Site Simulator
    participant S as 3DS Server membre
    participant D as DS simule
    participant A as ACS membre
    participant Q as Acquisition
    participant I as Issuing local

    C->>M: Commander
    M->>S: Authentification 3DS
    S->>D: AReq
    D->>A: AReq
    A-->>D: ARes (Y ou C)
    D-->>S: ARes
    opt Challenge
        M->>A: CReq
        A->>D: RReq
        D->>S: RReq
        S-->>D: RRes
        A-->>M: CRes
    end
    M->>Q: Achat + ECI + preuve
    Q->>S: Verification et consommation anti-rejeu
    S-->>Q: Valide
    Q->>I: Autorisation directe
    I-->>M: DE39=00 / autorisation
```

Une carte emise localement ne passe ni par DMAS ni par SWAM, quel que soit son
programme Visa ou Mastercard.

## Achat international avec carte LanaCash

Le site international joue le role d'un 3DS Server etranger. Le DS simule
route l'authentification vers l'ACS LanaCash. Pour un challenge, le `RReq`
autoritatif revient au 3DS Server du site international avant l'autorisation.
La transaction financiere de la carte LanaCash reste ensuite traitee par
l'Issuing local dans le perimetre de recette.

## Cartes confreres et reseaux financiers

```mermaid
flowchart TD
    A["Achat e-commerce authentifie"] --> Q["sg-acquiring"]
    Q --> R{"Route BIN autoritative"}
    R -->|Carte LanaCash| L["LOCAL_ISSUING"]
    R -->|Confrere domestique| S["SWAM"]
    R -->|Mastercard off-us| G["Passerelle Visa/Mastercard"]
    G --> D["DMAS Mastercard"]
    R -->|Visa off-us| G
    G --> V["Visa Online Member"]
    V --> N["VisaNet simule"]
    V --> B["Base II Member / TC05"]
    B --> BN["Reseau Base II simule"]
```

Le nom de programme fourni par le marchand n'est jamais utilise pour choisir
seul la route financiere : la route reste issue du routage BIN d'Acquisition.

## Securite et limites

- version de message sandbox : `2.3.1.1` ;
- preuve HMAC sandbox, cles uniquement dans un fichier local ignore par Git ;
- maximum de trois tentatives OTP ;
- `RReq` est autoritatif pour le challenge ;
- correlation sur les identifiants 3DS Server, DS et ACS ;
- preuve verifiee et consommee avant l'autorisation financiere ;
- aucun PAN, OTP ou secret complet dans les logs ou la base 3DS ;
- routes Visa et Base II fermees par defaut tant que leurs transports ne sont
  pas explicitement actives ;
- les deux reseaux Visa portent toujours la provenance `SIMULATED_NETWORK` ;
- un raccordement certifie reel exigera specifications officielles, certificats,
  gestion HSM, homologation reseau et tests de conformite.

## Recette

Les guides executables sont `tests/3ds/README.md` et
`tests/visa/TEST_VISA_ONLINE_BASEII.md`. Les commandes de reference sont :

```bash
bash ./tests/3ds/e2e/run-all-scenarios.sh
bash ./tests/visa/e2e/run-visa-online-base2.sh
```
