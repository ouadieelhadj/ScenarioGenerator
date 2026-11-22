# DAMANCASH — Recette native iPhone à exécuter

Statut initial : **NON EXÉCUTÉE sur iOS**. Les acquis Android/web sont conservés séparément. Utiliser des données fictives et les profils de démonstration existants ; ne pas réinitialiser leurs accès.

| Cas | Compte/périmètre | Étapes et résultat à vérifier | Preuve attendue |
|---|---|---|---|
| IOS01 | Opérateur Mac | Prévol, Swift tests, compilation simulateur puis signature valide du bundle de test | Versions Xcode/SDK, logs, empreinte de l'IPA |
| IOS02 | bim.amina | Connexion, commerce BIM correct, fermeture/réouverture, restauration Keychain puis déconnexion | Référence du profil, UI et contrôle de session sans jeton |
| IOS03 | BIM puis Carrefour | Tentative de lecture/export d'un incident de l'autre commerçant refusée | Réponse403/404, aucune donnée étrangère affichée |
| IOS04 | Commerçant personnel | Ouvrir un lien d'activation/récupération réel autorisé ; route propre, jeton consommé une fois ; URL étrangère refusée | Association Apple, parcours à froid/à chaud, aucun jeton dans les captures |
| IOS05 | Commerçant fictif | Parcours direct : identité, pièces, brouillon, soumission puis décision selon le workflow existant | Référence du dossier et états avant/après, pas de création externe supposée comme vérifiée |
| IOS06 | Commercial | Créer un dossier pour compte, confirmer puis retrouver le bon dossier et auteur | Référence, portefeuille et historique |
| IOS07 | bim.amina | Déclarer un incident, conserver l'accusé et référence ; reprise après coupure sans doublon | Même référence relue dans les deux API |
| IOS08 | Commerçant | Accepter caméra, déposer photo ; refuser caméra puis déclarer sans photo | Consentement réel, JPEG reçu, statut antivirus ; aucune photo obligatoire inventée |
| IOS09 | Commerçant | Sélectionner PDF/PNG dans Fichiers ; limites appliquées, quarantaine non téléchargeable, CLEAN copiable | MIME, taille, empreinte et états serveur |
| IOS10 | Commerçant | Exporter une pièce propre vers Fichiers puis annuler une deuxième copie | Fichier exact, annulation distincte d'un succès, copie temporaire supprimée |
| IOS11 | Salma puis commerçant | Affecter, demander complément, ajouter note interne ; réponse native ; note interne absente côté commerçant | Auteurs, états et messages visibles selon le profil |
| IOS12 | Support/commerçant | Résoudre, confirmer puis réouvrir dans le délai ; refus si droits absents | Historique et incrément des versions |
| IOS13 | Commerçant | Mettre l'application en arrière-plan, verrouiller l'appareil, enregistrer l'écran | Aperçu protégé ; trousseau indisponible verrouillé ; limite des captures ponctuelles documentée |
| IOS14 | Commerçant | Expiration/révocation, changement de compte pendant un chargement, déconnexion pendant un export | Aucune réponse ancienne appliquée au nouveau compte, aucun nouvel envoi automatique |
| IOS15 | Responsable de recette | Vérifier les avis aux contacts configurés et le contenu sur iPhone | Objet/référence et statut SMTP ; réception en boîte vérifiée séparément |
| IOS16 | Opérateur | Désinstaller/réinstaller puis ouvrir ; ancienne session non reprise sans connexion | Preuve de nouvelle installation et nouvelle authentification |

Pour chaque cas, renseigner : date UTC, modèle/version iOS, version/build/signature, utilisateur, référence métier, résultat, preuve et éventuelle anomalie. Ne pas inscrire mots de passe, jetons personnels ni véritables justificatifs dans les preuves. Une capture web n'est jamais une preuve de l'interface iPhone.

Les contrôles de sécurité complémentaires porteront sur le stockage, les échanges TLS, les liens, les API, les droits et les fichiers. Ne pas transformer la réussite des tests unitaires en un verdict de pentest.
