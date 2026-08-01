# Reprise de la session SWAM LIS

## État du développement

- Branche : `codex/swam-lis-clearing`
- Commit poussé : `9ef5980` (`Finaliser le clearing SWAM LIS bilateral`)
- Dépôt distant : `origin/codex/swam-lis-clearing`
- Modules : `sg-swam-lis-common`, `sg-swam-lis-member`, `sg-swam-lis-switch`
- Le SID supporte les transactions dans les deux sens sur une liaison permanente unique.
- Le scénario couvre les EOD membre et switch, les LIS outgoing, les imports croisés,
  le rapprochement, les chargebacks, la représentation et les écritures comptables.

## Validation déjà effectuée

- Reactor Maven complet : succès.
- E2E complet : `RESULTAT : PASSED (36 controles)`.
- Comptabilités membre et switch équilibrées.
- Les modifications préexistantes sans rapport (`NetworkRef.java`, traces, documents,
  `runtime/`, `tmp/`, etc.) sont restées locales et hors du commit.

## Commande Git Bash

```bash
cd /d/MoneyCore/ScenarioGenerator
export SWAM_E2E_KEK_CLEAR="0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
bash deploiement/swam-lis-e2e.sh
```

La clé ci-dessus est exclusivement une clé de test.

## Dernier état observé chez l'utilisateur

```text
[OK] Migration journaux SID pour clearing
[OK] SWAM switch issuer UP
```

Les nombreux messages PostgreSQL `NOTICE` indiquant que les colonnes existent déjà
sont normaux. Ils ne constituent pas des erreurs. Il faut laisser le script continuer
jusqu'au résultat final ou diagnostiquer le prochain service si aucune nouvelle ligne
n'apparaît pendant environ deux minutes.

## Raccordement Issuing du 2026-07-31

- `sg-swam-issuer` délègue maintenant les messages d'autorisation/financiers
  au cœur `sg-card-issuing`.
- La cible REST est l'endpoint actif `SWAM` du registre Issuing en base.
- Le dialecte ISO, la MAC et le mapping des codes restent la responsabilité
  de SWAM ; aucune indisponibilité Issuing n'est convertie en approbation.
- Un test d'adaptateur SWAM passe sans échec. L'E2E SWAM connecté reste à
  exécuter.

## E2E interne Issuing du 2026-07-31

- L'adaptateur reel `sg-swam-issuer` est exerce avec ServerPOS et DMAS dans
  `sg-issuing-internal-e2e` jusqu'au moteur de decision Issuing, via un appel
  JSON/REST HTTP reel sur boucle locale.
- La preuve couvre aussi le repeat idempotent ServerPOS et le moteur EMV
  partage CVN10 ARQC/ARPC ; elle ne modifie pas le chantier SWAM LIS.
- Non-regression dependante : 132 tests, 0 echec, `BUILD SUCCESS` le
  2026-07-31 a 11:36:35 +01:00.
- L'E2E SWAM avec PostgreSQL, processus separes et liaison reseau reste a
  executer.

## Reprise MAC SWAM membre vers switch reel du 2026-07-31

- Chantier actif : diagnostic puis correction du MAC entre notre SWAM Member
  et le switch SWAM reel, sans reprendre le chantier WayPos par supposition.
- Preuve de recette analysee : message `1804` de sign-off, RRN
  `620414260723`, rejete par le switch avec `RC 63`.
- Cause observable dans la trace : `Ended MAC field is absent ... Index = 128`.
  Le switch ne trouve donc pas le champ DE128 dans la trame recue et rejette
  le message avant toute comparaison de valeur de MAC.
- Une ancienne campagne avait valide avec Way4 la convention ANSI X9.19 avec
  `MTI + bitmap + champs`. Les captures M6 plus recentes des fonctions 811/899
  ont conduit le code courant a utiliser M6 Alg01 sur les valeurs des DE, sans
  MTI, bitmap ni valeur DE128. Ne pas choisir entre ces conventions sans la
  specification officielle SWAM et un message commun aux deux traces.
- Tests ajoutes le 2026-07-31 :
  `sg-common/src/test/java/com/staging/sg/common/iso/SwamPackagerMacTest.java`
  et un cas 1804/802 dans `SwamMacBuilderTest.java`.
- Le test de paquetage prouve que notre message 1804/802 contient DE128 apres
  pack/unpack, avec bitmap secondaire present et bit 128 actif. Le test M6
  prouve separement que la valeur DE128 n'est pas injectee dans le buffer HSM.
- Le chemin d'emission membre existant pose DE128 avant l'appel au channel pour
  les ACK 1814/811 et 1814/899 et interdit l'envoi si DE128 est absent.
- Validation ciblee : 6 tests, 0 echec, `BUILD SUCCESS` a 15:13:24 +01:00.
- Validation SWAM agregee (`sg-common`, `sg-swam-issuer`,
  `sg-swam-acquirer`) : 80 tests, 0 echec, `BUILD SUCCESS` a
  15:16:02 +01:00.
- Conclusion locale : la disparition de DE128 n'est pas reproduite dans notre
  packager ni dans le chemin ACK. Le premier travail non termine est une
  comparaison synchronisee des trames ASCII/hex, buffers M6/M7 et KCV pour un
  meme message 811 ou 899, puis l'alignement du builder sur la regle officielle
  confirmee par SWAM et Way4.
- Services de test arretes proprement le 2026-07-31 : processus Java qui
  occupaient les ports `8500/8501`, `8510/8511`, `8530/8531` et `8540`.
  PostgreSQL et le processus ancien sur le port `8080` ont ete conserves.
