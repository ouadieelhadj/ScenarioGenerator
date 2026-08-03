# Exemple de log reel Server Way4 - reference packager WayPos

## Objet et regles de securite

Cette reference est derivee du journal Way4 reel
`ext_20260724130158.log`, sans copier ce journal dans le depot.

Elle permet de controler le cadrage TCP et la forme ISO 8583 des messages
echanges avec un TPE Feitian. Les donnees de paiement ont ete neutralisees en
conservant leurs longueurs. Ce fichier ne contient donc aucun PAN reel, piste
reelle, PIN block reel, cryptogramme ICC reel, MAC reel ou cle.

La valeur hexadecimale ci-dessous est un specimen structurel de test. Elle ne
doit pas etre utilisee comme vecteur cryptographique de recette.

## Synthese du flux observe

Le message financier envoye sur le fil par le TPE est un `0200`. Way4 le
transforme ensuite en `0100` dans son modele interne, puis retourne un `0210`
au TPE. Le `0100` du journal n'est donc pas un deuxieme message TCP emis par le
terminal.

```text
TPE Feitian              POS_FEITIAN Way4             Moteur interne Way4
    |  0200 (achat)              |                            |
    |--------------------------->|  0100 interne             |
    |                            |--------------------------->|
    |                            |  0110 interne, RC=96       |
    |                            |<---------------------------|
    |  0210, RC=96               |                            |
    |<---------------------------|                            |
```

## Cadrage TCP reel

- octets recus par Way4 : `293` ;
- longueur du payload ISO : `291` octets ;
- en-tete : longueur non signee sur deux octets, big-endian ;
- longueur `291` en hexadecimal : `0x0123` ;
- forme d'une trame : `01 23 || payload ISO de 291 octets`.

Ce cadrage correspond a `WayPosLengthChannel`.

## Message 0200 externe observe

Champs presents dans la requete TPE :

| Champ | Forme observee | Verification locale |
|---|---:|---|
| MTI | `0200`, BCD | conforme |
| Bitmap | primaire, binaire, DE2 a DE64 | conforme |
| DE2 | LLVAR BCD, 16 chiffres | conforme, valeur neutralisee |
| DE3 | 6 chiffres BCD | conforme |
| DE4 | 12 chiffres BCD | conforme |
| DE7 | 10 chiffres BCD | conforme |
| DE11 | 6 chiffres BCD | conforme |
| DE14 | 4 chiffres BCD | conforme |
| DE22 | 3 chiffres BCD | conforme |
| DE23 | 3 chiffres BCD | conforme |
| DE25 | 2 chiffres BCD | conforme |
| DE35 | LLVAR, longueur 33 | conforme, piste neutralisee |
| DE41 | CHAR fixe 8, `12488881` | conforme |
| DE42 | absent du message observe | accepte par le validateur actuel |
| DE49 | 3 chiffres BCD, `504` | conforme |
| DE52 | binaire fixe, 8 octets | conforme, valeur neutralisee |
| DE55 | LLLVAR BCD, 131 octets binaires | conforme, valeur neutralisee |
| DE63 | LLLVAR BCD, 57 caracteres | conforme |
| DE64 | binaire fixe, 4 octets | conforme, valeur neutralisee |

Le mode MAC Way4 observe est `MAC=B`, coherent avec un profil ServerPOS dont
`macData=BIN`.

## Payload hexadecimal neutralise du 0200

Le specimen conserve exactement les champs et les longueurs observes. Sa
longueur est de 291 octets et son bitmap est `7224068020809203`.

```text
020072240680208092031641111111111111110000000000000065890724154116000217251200510000003330303030303030303030303030303030303030303030303030303030303030303031323438383838310504000000000000000001310000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005730313653563138353230373437393630303030303230524E31323438383838313137383439303430373630313250433231303031343130303100000000
```

La trame TCP neutralisee complete commence donc par `0123`, suivi de ce
payload.

## Reference automatisee

Le test
`sg-common/src/test/java/com/staging/sg/common/iso/WayPosRealServerReferenceTest.java`
verifie :

- la longueur exacte de 291 octets ;
- l'hexadecimal neutralise complet ;
- le round-trip pack/unpack jPOS ;
- DE41, la longueur de DE55, la longueur de DE63 et DE64.

Commande :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -o -nsu -f pom.xml -pl sg-common `
  -Dtest=WayPosRealServerReferenceTest test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

## Message 0100

Le `0100` disponible dans le journal est la projection interne Way4 du `0200`.
Il contient notamment des champs internes superieurs a DE64. Il ne doit pas
servir de trame de reference pour le packager TCP WayPos, qui traite le message
externe a bitmap primaire.

Conclusion : le packager externe doit etre valide sur `0200/0210`; le mapping
fonctionnel doit, lui, verifier la correspondance `0200 externe -> 0100
interne`.

## Messages 0800/0810 RKI

Le journal analyse contient des `0800/0810` du module `MC_CREDIT`, mais pas un
dump complet `0800/0810` RKI de `POS_FEITIAN` pour le terminal `12488881`.
Ils ne sont donc pas repris ici.

Cette section sera completee uniquement avec un vrai cycle du TPE :

1. requete `0800` de changement de cles ;
2. reponse `0810` ;
3. confirmation envoyee par le TPE ;
4. reponse de confirmation ;
5. longueurs, bitmap et liste des champs ;
6. hexadecimal neutralise a longueur identique.

Ne pas remplacer ces messages absents par un vecteur invente.

## Resultat actuel

- cadrage TCP : conforme ;
- forme externe du `0200` : conforme ;
- DE42 absent : non bloquant pour le message observe ;
- mode MAC binaire : coherent ;
- `0100` : mapping interne Way4, pas un message TPE ;
- `0800/0810` POS RKI : attente d'un journal reel complet ;
- resultat financier du cas observe : reponse externe `0210`, RC `96`.

## Ecarts corriges dans ServerPOS et le simulateur

La lecture jPOS du payload reel etait correcte, mais le controle metier
ServerPOS imposait a tort DE37 dans la requete `0200`. Le TPE reel ne transmet
pas DE37 ; Way4 le genere dans la reponse `0210`.

Corrections appliquees :

- DE37 n'est plus obligatoire dans une requete financiere `0100/0200` ;
- ServerPOS genere un DE37 numerique de 12 chiffres dans la reponse si la
  requete ne le contient pas ;
- DE7 est conserve dans la reponse et controle par le simulateur ;
- le simulateur peut omettre DE37 et DE42 si ces valeurs ne sont pas fournies,
  afin de reproduire la forme observee sur le TPE reel ;
- la reference neutralisee est unpackee, validee et repackee par jPOS.

Validation du 2026-08-03 :

- `sg-common` : 70 tests, 0 echec ;
- `sg-way-pos-server` : 35 tests, 0 echec ;
- `sg-way-pos-simulator` : 18 tests, 0 echec ;
- total : 123 tests, 0 echec ;
- packaging des deux JAR : `BUILD SUCCESS`.

Le vrai cycle RKI `0800/0810` reste a comparer avec un journal
`POS_FEITIAN` contenant l'echange complet.
