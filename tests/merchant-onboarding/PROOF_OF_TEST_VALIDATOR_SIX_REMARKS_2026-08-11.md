# Preuve de correction des six remarques du validateur

Date : 11 août 2026
Branche : `codex/AddingFrontendMerchantPortal`
Commit de référence avant corrections : `fdef73f97ad6c4b51a70c800378583145fff89cd`
Répertoire : `D:\MoneyCore\ScenarioGenerator`

Cette preuve porte sur la préparation technique. Aucun import WAY4 et aucune
recette E2E positive Portal -> Connecteur -> WAY4 n'ont été exécutés. Aucun PDV,
contact, identifiant WAY4, MID ou TID fictif n'a été créé.

## Corrections réalisées

| Remarque | Correction et preuve |
|---|---|
| Connecteur WAY4 désactivé | Les événements `way4.export.requested` ne sont plus réservés lorsque le connecteur est désactivé. Un événement déjà en `PROCESSING` est remis en `PENDING`, sans échec final. Tests : `OnboardingOutboxDispatcherRoutingTest` et `HttpWay4ConnectorAdapterTest`. |
| Échec WAY4 indépendant d'Acquiring | L'état d'export WAY4 trace le code, le message assaini, le caractère recyclable et la date d'échec. Un échec WAY4 ne modifie ni le dossier ni le provisionnement FuturPayment Acquiring. Tests : `OnboardingOutboxCompletionIndependenceTest` et `OnboardingWay4ExportStateTest`. |
| OAuth2 connecteur | Cinq cas sont couverts : jeton absent, jeton invalide, mauvaise audience, scope manquant, audience et scope valides. `Way4ConnectorSecurityTest` : 5/5 réussis. |
| Campagne propre | Les répertoires `target` des quatre modules ont été supprimés avant la campagne. Le plugin Maven `clean`, absent du cache hors ligne, n'a pas été téléchargé. La reconstruction et les tests ont ensuite été exécutés intégralement avec le Maven embarqué. |
| Destination métier | `FUTURPAYMENT`, `WAY4` et `BOTH` sont persistés et obligatoires avant soumission/approbation. Le routage crée uniquement les événements correspondant à la sélection. Le payload v1 historique reste inchangé ; un endpoint dédié sélectionne la destination, tandis que l'API v2 la porte directement. Tests : trois routes dans `OnboardingDestinationRoutingTest` et contrat v1 dans `MerchantOnboardingApiCompatibilityTest`. |
| Lien unique d'activation | Le Web et Android génèrent le même lien `https://portal.futurpayment.com/activation?token=...`. Le manifeste Android contient un intent filter HTTPS `autoVerify=true`; `@capacitor/app` traite le démarrage à froid et `appUrlOpen`; le build Web publie `/.well-known/assetlinks.json`. |

## Campagne Java finale

Commande :

```text
D:\MoneyCore\idea-2026.1.3.win\plugins\maven\lib\maven3\bin\mvn.cmd
  -o -nsu -q -f pom.xml
  -pl sg-acquiring,sg-merchant-onboarding,sg-way4-aura-connector -am test
  -Dmaven.repo.local=D:\MoneyCore\.m2\repository
```

Résultats extraits des rapports Surefire fraîchement régénérés :

| Module | Tests | Échecs | Erreurs | Ignorés |
|---|---:|---:|---:|---:|
| `sg-common` | 77 | 0 | 0 | 0 |
| `sg-acquiring` | 26 | 0 | 0 | 0 |
| `sg-merchant-onboarding` | 37 | 0 | 0 | 0 |
| `sg-way4-aura-connector` | 7 | 0 | 0 | 0 |
| **Total** | **147** | **0** | **0** | **0** |

Classes ciblées ajoutées ou renforcées : 16 tests, tous réussis :

- compatibilité API v1 : 3 ;
- routage des trois destinations : 3 ;
- maintien en attente quand WAY4 est désactivé : 2 ;
- indépendance d'échec et trace WAY4 : 2 ;
- adaptateur WAY4 désactivé : 1 ;
- sécurité OAuth2 du connecteur : 5.

## Frontends et Android

- build `merchant-portal-web` : réussi ;
- build `merchant-mobile` : réussi ;
- synchronisation Capacitor Android : réussie ;
- `assembleDebug` : réussi ;
- APK : `sg-frontend/android/app/build/outputs/apk/debug/app-debug.apk`,
  9 235 302 octets, généré le 11 août 2026 à 12:30:37 ;
- plugin natif présent : `com.capacitorjs.plugins.app.AppPlugin` ;
- package Android : `com.moneycore.merchantportal` ;
- empreinte SHA-256 du certificat debug réellement vérifiée sur l'APK :
  `1E:15:47:0E:5D:0D:92:70:87:AE:4C:71:7B:78:D1:03:BA:91:D8:89:C2:0F:09:44:C3:F3:2C:06:30:F9:98:AF` ;
- la même empreinte est publiée dans l'artefact Web
  `dist/merchant-portal-web/browser/.well-known/assetlinks.json`.

Limite externe : au moment du contrôle, le DNS public de
`portal.futurpayment.com` ne se résout pas. L'association App Link est donc
validée dans le code, le manifeste fusionné, l'APK signé et l'artefact Web,
mais sa vérification Android sur le domaine public attend le déploiement DNS/HTTPS
de cet artefact. Pour une APK de production, l'empreinte du certificat de
signature release devra être ajoutée avant publication.

## PostgreSQL 18 réel

Serveur contrôlé : PostgreSQL 18.4, base `scenariogenerator`.

Sauvegarde prise avant la campagne :
`runtime/merchant-portal-e2e/backups/before-validator-six-fixes-20260811-121740.dump`
(693 833 octets).

Migrations appliquées une première fois puis rejouées dans des transactions
avec arrêt sur erreur :

- `sql/merchant-onboarding/V7__direct_portal_way4_export_state.sql` ;
- `sql/way4-aura/V2__portal_direct_mid_and_return_preparation.sql` ;
- `sql/merchant-onboarding/V8__destination_and_way4_failure_trace.sql`.

| Contrôle | Avant | Après application | Après rejeu |
|---|---:|---:|---:|
| Dossiers Onboarding | 16 | 16 | 16 |
| Événements outbox | 0 | 0 | 0 |
| États WAY4 | 0 | 0 | 0 |
| Allocations MID | 0 | 0 | 0 |

Contrôles finaux :

- destinations historiques : 16 `FUTURPAYMENT`, conformément au parcours réel antérieur ;
- destination absente hors brouillon : 0 ; destination absente totale : 0 ;
- doublons `RegNumber` : 0 ;
- doublons MID : 0 ;
- états WAY4 orphelins : 0 ;
- doublons de clé d'idempotence WAY4 : 0 ;
- dossiers Onboarding sans exactement un PDV principal actif : 0 ;
- commerçants Acquiring actifs sans exactement un PDV principal actif : 1 ;
- commerçants Acquiring actifs sans aucun PDV : 1.

L'anomalie Acquiring restante concerne toujours le commerçant
`885d1af8-2f05-465a-832a-6a91ae613da3`. Elle n'a pas été corrigée car aucune
adresse ni aucun contact métier réel n'a été fourni.

## Conclusion transmise au validateur

Les six adaptations de code ont été revues et les validations locales sont
réussies. Le validateur a accordé le GO technique pour clôturer le
développement et effectuer le commit/push strictement sélectif. Ce GO ne vaut
pas autorisation de recette E2E réelle.

Le NO-GO de la recette E2E reste maintenu jusqu'à :

1. la réconciliation réelle du commerçant actif sans PDV ;
2. le déploiement et la vérification HTTPS publique de `assetlinks.json` ;
3. la levée des prérequis WAY4/AURA et OAuth2 déjà identifiés.

Après ces levées et validation du dossier de preuves, une seule recette E2E
complète pourra être exécutée sur décision explicite du validateur.
