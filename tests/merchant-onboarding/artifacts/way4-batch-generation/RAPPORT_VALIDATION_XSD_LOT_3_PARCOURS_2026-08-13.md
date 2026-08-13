# Rapport de validation XSD - lot WAY4 des trois parcours Portal

Date : 13 aout 2026

## Verdict

**VALIDE TECHNIQUEMENT POUR REVUE, NON AUTORISE POUR IMPORT WAY4.**

Le fichier consolide les trois parcours demandes et passe la validation contre
le XSD officiel. Les bindings AURA utilises pour cette preuve restent marques
`validatedForImport=false`. Ils doivent etre remplaces ou approuves a partir
des referentiels de recette avant tout depot dans WAY4.

## Artefact XML

- Fichier : `xadvapl000100_00008.225`
- Chemin : `D:\MoneyCore\ScenarioGenerator\tests\merchant-onboarding\artifacts\way4-batch-generation\xadvapl000100_00008.225`
- Taille : 16 070 octets
- SHA-256 : `CB959214357218A4D6E90DC4C135E21149B10C88B1EF4B79DF486BE0C6C5B164`
- Sender : `000100`
- Number : `00008`
- CreationDate : `2026-08-13`
- Jour julien : `225`
- Coherence du nom physique : conforme a `xadvapl<Sender>_<Number sur 5 chiffres>.<jour julien>`

## Validation XSD

- XSD principal : `D:\LanaCash\OpenWay\installationOCI\chargementxmlway4\schemas\xsd\xsd\offline\WAY4ApplFile.xsd`
- SHA-256 XSD : `F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`
- Resultat : **valide**
- Tests cibles de generation : 5 executes, 0 echec, 0 erreur, 0 ignore
- Non-regression : 77 tests `sg-common` et 10 tests connecteur, soit 87 tests,
  0 echec, 0 erreur, 0 ignore
- Build Maven : `BUILD SUCCESS`, termine le 13 aout 2026 a 10:28:15 +01:00

## Controles automatiques

| Controle | Attendu | Constate | Statut |
|---|---:|---:|---|
| Applications commercant racines | 3 | 3 | Conforme |
| Contrats commercant | 3 | 3 | Conforme |
| Contrats TPE | 6 | 6 | Conforme |
| Application/RegNumber | 15 | 15 | Conforme |
| Application/RegNumber uniques | 15 | 15 | Conforme |
| MerchantID/MID | 0 | 0 | Conforme |
| ContractIDT | 0 | 0 | Conforme |
| Valeurs `FIRST`, `SECOND` ou mot `test` | 0 | 0 | Conforme |

Les racines detectees sont `ONB-198B8A1C`, `ONB-D9DAE641` et
`ONB-64B09020`. Le lot contient respectivement 1, 2 et 3 TPE.

## Sources et empreintes

| Source | SHA-256 |
|---|---|
| `merchant-web-canonical-acquiring.json` | `905A1EB2FD3891AB3E4B1473879C897A0AF59B874CF4958BE225DCB5E4566BEA` |
| `commercial-web-canonical-acquiring.json` | `840A1D6DB18F08DC97F954A02F0901EFA29098E437C19C13A41F88166F1F6457` |
| `mobile-canonical-acquiring.json` | `8C2DB61014B97703EDBEE78E0B2DC209B20D51C92F741494AB2816F62B79F269` |
| `way4-bindings-pending-aura-validation.json` | `21A0B14A3B883595AE0C4C8AB95394CA492A25CD9228E90272C5A6BF09ACD9C8` |

## Restrictions

- Aucun identifiant client WAY4, numero de contrat, MID, TID, TAMK, TPMK,
  TAK ou TPK n'est invente ou transmis.
- Les codes AURA ne sont pas codes en dur dans le generateur. En production,
  ils sont resolus depuis la table configurable `aura_binding`.
- Le fichier de bindings de preuve est externe au code et explicitement non
  valide pour import.
- Le XML ne doit pas etre depose dans WAY4 avant validation des bindings, de
  la plage MID et du canal d'import par les responsables AURA/OpenWay.
