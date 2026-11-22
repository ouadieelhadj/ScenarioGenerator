# DAMANCASH — Version iPhone 1.3.10, préparation du 22 novembre 2026

Le projet iOS et ses composants natifs sont préparés à partir du dépôt standard `D:/MoneyCore/ScenarioGenerator`. L'application reprend les écrans d'onboarding, les espaces commerçant/commercial/back-office et les incidents. Elle cible les API OCI existantes en HTTPS ; aucun compte ou service OCI n'est modifié par ce kit.

**Ce kit est un projet à compiler, pas une IPA déjà signée ou une application déjà testée sur iPhone.** Le poste de préparation est Windows. La compilation Xcode, l'exécution des tests Swift, la signature et la recette native restent à réaliser sur Mac. Les résultats de préparation sont dans les JSON de preuves ; ils ne remplacent pas cette étape.

## Contenu et adaptations

- Projet Xcode officiel Capacitor 8.5.1, gestion des dépendances Swift Package Manager, version iOS minimale15.
- Version1.3.10 / build10310 ; identifiant de test `com.moneycore.merchantportal.test`, nom « FuturPayment Test ». L'identifiant doit être enregistré dans la vraie équipe Apple, sans inventer de Team ID.
- `SecureSession` : trousseau Keychain, accessible seulement après déverrouillage, lié à l'appareil, sans synchronisation iCloud. La déconnexion supprime la session ; une nouvelle installation demande une nouvelle connexion.
- `IncidentFileDownload` : copie PDF/JPEG/PNG via le sélecteur Fichiers, validation du nom et des octets, plafond technique de10Mo, refus/annulation explicites et nettoyage de la copie temporaire. Les limites métier restent celles du serveur (1Mo pour la démonstration actuelle). Le serveur contrôle toujours droits et antivirus avant téléchargement.
- Permissions caméra/photothèque explicites, fichiers internes non exposés globalement, HTTPS sans exception générale, débogage WebView désactivé. Le transport HTTP natif iOS évite de modifier les règles CORS des API.
- Affichage masqué lors du passage en arrière-plan et pendant un enregistrement d'écran détecté. **iOS ne fournit pas l'équivalent exact de FLAG_SECURE : une capture d'écran ponctuelle ne peut pas être garantie bloquée.** Aucun détournement de champ de mot de passe ni API privée n'est utilisé.
- Liens d'activation/confirmation/récupération conservés en mémoire protégée, sans leurs jetons dans l'historique de navigation. Les Universal Links nécessitent la vraie identité Apple et une association HTTPS à publier séparément.

## Prérequis Mac

Un Mac compatible avec Xcode26 ou ultérieur, Xcode complet initialisé, SDK iPhone/iOS Simulator, Node22 ou ultérieur, npm et Python3. Aucune compilation iOS native n'est possible avec le seul PC Windows ou la VM Linux OCI.

Pour un simulateur : la signature de distribution n'est pas nécessaire. Pour une IPA installable/distribution TestFlight : équipe Apple Developer, certificats/profils et droits sur le bundle de test sont requis. Ne pas envoyer de mots de passe, fichiers `.p12` ou clés privées dans les messages ni dans ce kit. Les configurations de signature restent dans le trousseau du Mac ou le coffre de la chaîne de compilation.

## Commandes — Terminal du Mac

Décompresser le kit puis se placer dans `sg-frontend`. Le script s'arrête avant toute opération native si le Mac/Xcode n'est pas disponible. Il ne démarre pas automatiquement un simulateur et ne publie rien sur TestFlight.

```bash
bash tools/ios/build-iphone.sh check
bash tools/ios/build-iphone.sh prepare
bash tools/ios/build-iphone.sh simulator
```

`prepare` reconstruit le client mobile de production, synchronise le seul projet iOS, remet les réglages iOS et exécute les vrais tests Swift des politiques partagées. `simulator` effectue en plus la compilation Xcode sans signature. Ouvrir ensuite `ios/App/App.xcodeproj`, sélectionner le simulateur iPhone installé puis lancer App.

Pour produire une archive et une IPA avec les identifiants réellement attribués :

```bash
export FP_IOS_TEAM_ID='VOTRE_TEAM_ID_REEL'
bash tools/ios/build-iphone.sh archive
```

Remplacer la valeur par le véritable identifiant de dix caractères. `FP_IOS_VERSION` et `FP_IOS_BUILD` permettent d'incrémenter les versions. Si l'opérateur veut autoriser Xcode à créer/mettre à jour les profils Apple avec son compte déjà connecté, il peut définir explicitement `FP_IOS_ALLOW_PROVISIONING=1` ; cela reste désactivé par défaut. Le script exporte une IPA, **sans téléversement TestFlight**. Les sorties sont sous `ios/App/output/<date UTC>`, ou `FP_IOS_OUTPUT`.

L'archive préparée sur Windows contient aussi les fichiers web compilés. Pour ouvrir le projet directement sur Mac sans reconstruction préalable, les dépendances npm doivent toutefois être présentes à l'emplacement attendu par le package Swift. Le chemin recommandé reste le script `prepare`, qui reconstruit depuis les sources livrées et vérifie le résultat.

## Liens universels

Le script `generate-associated-domains.py` génère le fichier Apple à partir du Team ID réel ; il ne le déploie pas. Après export, vérifier/rapprocher son contenu avec tout fichier Apple déjà présent sur `merchant.futurpayment.com`, puis publier à `/.well-known/apple-app-site-association` en HTTPS sans redirection et avec un type JSON adapté. Préserver l'association Android existante. Sans cette association, les courriels continuent d'ouvrir le parcours web, mais le passage automatique dans l'application iPhone n'est pas qualifié.

## Recette restant à effectuer sur iPhone

Voir `CAHIER_RECETTE_IPHONE.md`. Les52 tests JavaScript de sécurité/session/liens/export réussis durant la préparation couvrent le code partagé ; ils ne démontrent pas que Keychain, caméra, Fichiers et UIKit fonctionnent sur un appareil iOS. Les quatre tests XCTest fournis utilisent les vrais fichiers Swift de politique ; leur exécution reste à faire sur Mac. Les pentests demandés constituent une campagne distincte et ne sont pas déclarés exécutés.

Avant toute diffusion, faire relire les déclarations de confidentialité aux responsables FuturPayment, confirmer les informations de l'application dans App Store Connect, l'URL de confidentialité et le questionnaire de chiffrement. Le manifeste décrit les catégories de données effectivement proposées par les parcours ; il ne remplace pas cette validation. Aucun consentement ou certificat Apple n'a été inventé.

## Conservation et retour arrière

La préparation n'a modifié ni l'APK Android publiée, ni les API, ni Nginx, ni les comptes ou clés. Pour abandonner le test iPhone, ne pas installer/publier l'IPA ; conserver les preuves et le projet. Aucun retour SQL ou arrêt de service n'est nécessaire. Ne pas supprimer les modifications d'autres sessions dans le dépôt standard. Le fichier README et les preuves de préparation doivent accompagner toute passation.

Références : https://capacitorjs.com/docs/ios ; https://capacitorjs.com/docs/ios/configuration ; https://capacitorjs.com/docs/ios/privacy-manifest ; https://developer.apple.com/testflight/ .


## Actualisation du 22 novembre 2026 - recette native en cours

Les paragraphes de préparation ci-dessus constituent l'état initial. Capacitor est maintenant aligné sur8.5.1. Les quatre tests Swift ont réellement réussi sur le Mac hébergé, ainsi que les52contrôles JavaScript et la compilation pour simulateur. La CLI utilise seulement ouadieelhadj sur le dépôt standard public, selon la dernière décision du propriétaire ; aucune exigence de dépôt privé n'est à réintroduire.

Les résultats45017962040 et45019475326 restent des campagnes partielles, sans succès global. La connexion et l'accès incidents ont été observés, mais la restauration après relance n'est pas encore qualifiée. Le run45053786588 est le diagnostic suivant ; consulter son état réel avant toute relance. Les codes de retour natifs du trousseau sont collectés sur simulateur sans afficher de token ou de valeur Keychain. Les deux phases de construction utilisent le même DerivedData afin de ne pas recompiler deux fois les mêmes dépendances ; les XCUITest restent réellement exécutés après build-for-testing.

La signature de distribution Apple et une IPA installable sur téléphone restent distinctes de la construction simulateur. Aucun Team ID ou certificat n'a été inventé. Les cas caméra/Fichiers, cycle complet d'affiliation, isolation native, reprise et réinstallation restent à compléter. Lire REPRISE_IPHONE_DAMANCASH.md à la racine pour les actualisations suivantes, et ne pas interpréter cette note comme un résultat final.
