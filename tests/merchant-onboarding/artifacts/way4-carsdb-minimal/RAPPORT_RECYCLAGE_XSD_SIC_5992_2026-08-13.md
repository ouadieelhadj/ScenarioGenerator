# Rapport de recyclage et validation XSD — SIC 5992

**Validation technique du fichier : CONFORME**
**Autorisation d'import : NO-GO — `validatedForImport=false`**

## Fichier corrigé

- fichier : `xadvapl000100_00002.225` ;
- taille : 4 744 octets ;
- SHA-256 : `E6E28016DB56D05302996939831F20B0AD5A041F314DF8B96656150E222E32FF` ;
- `FileHeader/Number` : `00002` ;
- validation contre le XSD officiel : réussie ;
- SHA-256 du XSD principal : `F76E4927B2365B6A7B9FA9B7EE1B0CF28C87313CDE724BD6C6484673D0E8A680`.

## Recyclage contrôlé

La modification fonctionnelle entre les deux dossiers est limitée à :

- ancien SIC : `5411` ;
- nouveau SIC : `5992`.

Les valeurs suivantes sont strictement conservées :

- RegNumber : `ONB-198B8A1C`, `ONB-198B8A1C-ACCOUNT`, `ONB-198B8A1C-ADDRESS`, `ONB-198B8A1C-TPE-001` ;
- MID : `990001000000001` ;
- TID : `99000001` ;
- contrat commerçant : `LCAR00000001`.

Le test PostgreSQL réel a marqué le premier dossier comme rejeté, généré le nouveau lot, puis rejoué ce lot. Résultat : 3 tests réussis, aucune erreur et aucune nouvelle allocation. Les trois séquences et les trois valeurs persistées sont restées inchangées.

La non-régression finale recense 93 tests : 90 exécutés avec succès et 3 tests PostgreSQL ignorés dans la campagne standard fermée. Ces trois tests PostgreSQL ont été exécutés séparément sur CARSDB avec succès.

## Preuves Oracle encore obligatoires

La présence active de `5992 / Florist` dans `OWS.V_SIC_USED` et l'absence d'objet partiel après le premier rejet ne peuvent pas être confirmées depuis le poste local. Le script Oracle en lecture seule à exécuter est :

`D:\LanaCash\OpenWay\installationOCI\environnementRecetteLanacash\way4-sql-tool-bindings-aura\CONTROLER_RECYCLAGE_REJET_SIC_5992_CARSDB.sql`

Le fichier ne doit pas être importé avant réception d'une sortie conforme : une correspondance `5992 / Florist` et zéro pour chacun des sept contrôles d'objet partiel.
