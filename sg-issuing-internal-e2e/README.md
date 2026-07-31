# E2E interne Issuing

Ce module valide le raccordement interne des canaux existants au coeur
Issuing sans exiger PostgreSQL ni le demarrage de plusieurs processus.

Le test `IssuingMultiChannelE2ETest` exerce :

- ServerPOS, SWAM Issuer et DMAS Mastercard avec leurs adaptateurs reels ;
- la resolution de l'endpoint Issuing parametre en base via le client reel ;
- un appel HTTP JSON/REST reel sur une boucle locale ;
- le moteur de decision Issuing reel et le debit du financement ;
- le rejeu idempotent ServerPOS, avec la meme autorisation et sans second debit ;
- le moteur M/Chip 4 CVN10 existant pour ARQC, anti-rejeu ATC et ARPC tag 91 ;
- un LMK temporaire et une MDK aleatoire generee pendant le test, sans secret
  statique dans le depot.

Les repositories, l'annuaire de configuration et le financement sont des
doubles en memoire. Ce test ne remplace donc pas la recette connectee avec
PostgreSQL, les services separes, un payShield et les cles de recette.

Execution depuis la racine du depot :

```powershell
& 'D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd' `
  -o -nsu -f pom.xml -pl sg-issuing-internal-e2e -am test `
  '-Dmaven.repo.local=D:\MoneyCore\.m2\repository'
```

Le code suivi implemente CVN10. Les documents historiques du depot indiquent
que CVN01 reste a raccorder a partir de vecteurs et d'une regle officielle ;
ce harnais ne simule pas une prise en charge CVN01 inexistante.
