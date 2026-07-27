# Guide résumé de test du portail modulaire

## 1. Démarrage du frontend

Dans Git Bash :

```bash
cd /d/MoneyCore/ScenarioGenerator
bash deploiement/frontend/start-frontend.sh
```

Ouvrir ensuite :

```text
http://localhost:4200
```

Le backend orchestrateur doit être disponible sur `http://localhost:8080`.

Compte local initial :

```text
Utilisateur : admin
Mot de passe : Admin123!
```

## 2. Test E2E navigateur automatisé

Dans Git Bash :

```bash
cd /d/MoneyCore/ScenarioGenerator
bash deploiement/frontend/frontend-e2e.sh
```

Le script :

1. applique la migration additive du portail ;
2. compile et démarre l'orchestrateur si nécessaire ;
3. démarre Angular si nécessaire ;
4. installe Chromium Playwright si nécessaire ;
5. exécute les scénarios navigateur ;
6. arrête uniquement les services qu'il a démarrés.

Résultat attendu :

```text
RESULTAT : E2E FRONTEND PASSED
```

Le rapport HTML est généré dans :

```text
sg-frontend/playwright-report/index.html
```

## 3. Contrôles automatisés actuels

- redirection d'un visiteur non authentifié ;
- connexion de l'administrateur ;
- chargement des modules autorisés depuis le backend ;
- présence de SWAM LIS Membre, SWAM LIS Switch et DMAS ;
- chargement du même écran clearing avec le contexte Membre puis Switch ;
- déconnexion et suppression de la session.

## 4. Matrice de recette manuelle

### Navigation

- connecter un utilisateur possédant plusieurs profils ;
- vérifier l'union des modules et écrans autorisés ;
- ajouter une interdiction individuelle ;
- vérifier que l'interdiction masque l'écran ;
- vérifier que les parents nécessaires restent visibles ;
- vérifier que les écrans frères non autorisés restent invisibles ;
- saisir directement une route interdite et vérifier le refus.

### Modules et écrans communs

- ouvrir Transactions depuis SWAM LIS Membre ;
- vérifier le contexte `SWAM_LIS_MEMBER` ;
- ouvrir le même écran depuis SWAM LIS Switch ;
- vérifier le contexte `SWAM_LIS_SWITCH` ;
- vérifier que les données et actions utilisent le bon backend.

### Maker/Checker

- affecter un Maker à un Checker pour une opération ;
- créer une demande avec le Maker ;
- vérifier que seul le Checker affecté la reçoit ;
- essayer l'auto-validation et vérifier le refus ;
- valider puis vérifier le mode d'exécution batch ou API ;
- rejeter avec un motif ;
- changer le Checker avec un motif ;
- changer le Maker depuis le profil Superviseur ;
- activer un Checker remplaçant pendant une période définie.

### Visibilité des données

- vérifier que le Maker ne voit que ses opérations ;
- vérifier que le Checker ne voit que les Makers qui lui sont rattachés ;
- vérifier que le Superviseur voit uniquement son équipe ;
- vérifier qu'un Administrateur sans permission métier ne voit pas les opérations.

### SLA

- créer une opération avant le cut-off ;
- créer une opération après le cut-off ;
- vérifier le calcul sur jours ouvrés bancaires marocains ;
- tester un week-end, un jour férié et une fermeture exceptionnelle ;
- vérifier les échéances Maker, Checker, technique et globale ;
- vérifier les couleurs vert, orange et rouge ;
- vérifier qu'une réaffectation ne repousse pas les échéances.

### Notifications

- soumettre une demande et vérifier la notification du Checker ;
- dépasser l'échéance et vérifier l'email au Maker avec Checker en copie ;
- vérifier l'absence de doublon d'email ;
- vérifier les notifications de validation, rejet et réaffectation.

### SWAM LIS

- consulter les transactions Membre et Switch ;
- lancer les EOD ;
- générer les LIS outgoing ;
- intégrer les LIS croisés ;
- contrôler le rapprochement ;
- créer et valider un chargeback ;
- vérifier son inclusion dans le prochain outgoing ;
- créer et intégrer une représentation ;
- vérifier l'équilibre comptable.

## 5. Diagnostic

Logs du test :

```text
tmp/frontend-e2e/backend.log
tmp/frontend-e2e/frontend.log
```

Traces Playwright en cas d'échec :

```text
sg-frontend/test-results/
```

Pour afficher le rapport :

```bash
cd /d/MoneyCore/ScenarioGenerator/sg-frontend
npx playwright show-report
```
