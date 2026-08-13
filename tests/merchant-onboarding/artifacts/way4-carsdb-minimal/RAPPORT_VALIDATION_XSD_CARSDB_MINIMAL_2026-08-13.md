# Rapport de validation XSD — candidat WAY4 CARSDB minimal

**Statut technique : CONFORME AU XSD**
**Statut d'import : NO-GO provisoire — `validatedForImport=false`**

## Fichier contrôlé

- Fichier : `xadvapl000100_00001.225`
- Taille : 4 744 octets
- SHA-256 : `F36E0C13C72FAA3F1D4EB70F5028DBF0E6464FD0D38921C47531316A8EC7D267`
- Sender : `000100`
- Number : `00001`
- CreationDate : `2026-08-13`
- Institution : `0001`

## Résultat

Le fichier a été validé avec succès contre `offline/WAY4ApplFile.xsd` et ses dépendances.

- SHA-256 du XSD principal : `F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`
- Commerçants : 1
- PDV : 1
- Contrats commerçant : 1
- Contrats TPE : 1
- `DeviceInfo` : 1
- `Application/RegNumber` : 4, tous uniques
- Identifiant interne WAY4 client/contrat : absent

Campagne Maven ciblée du 13 août 2026 : 5 tests, 0 échec, 0 erreur, 0 test ignoré. La campagne PostgreSQL de bout en bout suivante : 2 tests, 0 échec, 0 erreur, 0 test ignoré.

Ce résultat autorise la revue du fichier concret, mais pas son import avant le GO explicite du validateur.
