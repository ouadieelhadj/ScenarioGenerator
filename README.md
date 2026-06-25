# ScenarioGenerator — Simulateur monetique Mastercard DMAS

Plateforme de generation + orchestration de scenarios monetiques (autorisations, advices, reversals) contre un simulateur DMAS Mastercard.

## Modules (Maven multi-module, branche feature/multi-module)

- sg-generator-orchestrator (8080) : Orchestrateur. Genere les tests/scenarios, execute (TPS, replay).
- sg-dmas-acquirer (8084 REST, 8600 jPOS) : Acquereur reseau. Tient la connexion permanente jPOS vers l'issuer (DmasJposServer).
- sg-dmas-issuer (8501 REST) : Banque/emetteur. Decide les autorisations, tient les soldes (DmasJposClient).
- sg-common : Entites, packager EBCDIC, utils, repositories partages.
- sg-issuer (8200 ISO, 8201) : Issuer interne/legacy (cible du mode internal de l'orchestrator).

## Flux d'autorisation cible (connexion permanente)

orchestrator (8080) --REST /api/admin/dmas/auth--> sg-dmas-acquirer (8084) --connexion permanente jPOS (pushAndWait)--> sg-dmas-issuer (8501)

- Active par tps.engine.mode: dmas (orchestrator) + transport:jpos (injecte par TpsEngine + DmasMapper).
- Prerequis : un sign-on issuer doit avoir active la session permanente avant toute autorisation.
- STAN (DE11) sur 6 chiffres (n-6, conforme spec CIS p.297).

## Tests

Voir tests/dmas/README.md : scripts reutilisables pour valider le flux orchestre de bout en bout (sign-on, carte, test simple, test TPS, execution, verif logs jpos).

## Conventions de dev

- Maven 3.6.3 (PATH dans ~/.bashrc), JAVA_HOME jdk-24. mvn install -q -DskipTests.
- Modif sg-common -> reinstaller sg-common AVANT de recompiler les modules dependants.
- Login : admin / Admin123! (champ JSON "login", endpoint /auth/login).
- ddl-auto issuer : validate (l'user applicatif n'a pas les droits DDL).
