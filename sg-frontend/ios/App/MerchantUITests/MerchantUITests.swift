import XCTest

final class MerchantUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    @MainActor private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(fr)", "-AppleLocale", "fr_FR"]
        app.launch()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 30))
        let cover = app.otherElements["merchant-privacy-cover"]
        let foreground = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: cover)
        XCTAssertEqual(XCTWaiter.wait(for: [foreground], timeout: 15), .completed, "La protection doit se retirer quand la scène redevient active.")
        return app
    }
    @MainActor private func element(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let predicate = NSPredicate(format: "label == %@", label)
        for query in [app.webViews.buttons, app.webViews.links, app.webViews.staticTexts] {
            let value = query.matching(predicate).firstMatch
            if value.exists { return value }
        }
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }
    @MainActor private func tap(_ app: XCUIApplication, _ label: String) {
        for _ in 0..<6 {
            let value = element(app, label)
            if value.exists && value.isHittable { value.tap(); return }
            app.webViews.firstMatch.swipeUp()
        }
        XCTFail("Contrôle non accessible : " + label)
    }
    @MainActor private func clearExistingInput(_ field: XCUIElement) {
        let value = field.value as? String ?? ""
        // Inspect only length in memory; never print the value or use it in an assertion.
        if !value.isEmpty && value != field.placeholderValue {
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.5)).tap()
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: min(value.count, 256)))
        }
    }
    @MainActor private func login(_ app: XCUIApplication, userOverride: String? = nil) throws {
        let env = ProcessInfo.processInfo.environment
        guard env["FP_IOS_AUTHENTICATED_TESTS"] == "true",
              let password = env["FP_IOS_TEST_PASSWORD"], !password.isEmpty else {
            throw XCTSkip("Le secret de recette n'est pas configuré : aucun test authentifié revendiqué.")
        }
        let user = userOverride ?? env["FP_IOS_TEST_USER"] ?? "bim.amina"
        if element(app, "Bonjour " + user).waitForExistence(timeout: 5) { return }
        let login = app.webViews.textFields.firstMatch
        XCTAssertTrue(login.waitForExistence(timeout: 60)); login.tap(); clearExistingInput(login); login.typeText(user)
        dismissFormKeyboard(app)
        // Move the field above the software keyboard before tapping it. Ionic can
        // reposition its input while the first keyboard is appearing.
        let viewport = app.webViews.firstMatch
        let secret = app.webViews.secureTextFields.firstMatch
        XCTAssertTrue(secret.waitForExistence(timeout: 10))
        _ = XCTWaiter.wait(for: [], timeout: 0.5)
        for _ in 0..<5 {
            let bottom = app.keyboards.firstMatch.exists ? app.keyboards.firstMatch.frame.minY : viewport.frame.maxY
            if secret.frame.minY < viewport.frame.minY + 60 {
                viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.25))
                    .press(forDuration: 0.05, thenDragTo: viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.50)))
            } else if secret.frame.maxY > bottom - 12 {
                viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.50))
                    .press(forDuration: 0.05, thenDragTo: viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.25)))
            } else { break }
        }
        XCTAssertTrue(secret.isHittable, "Le champ sécurisé doit être visible avant saisie.")
        secret.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.5)).tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        _ = XCTWaiter.wait(for: [], timeout: 0.5)
        clearExistingInput(secret)
        secret.typeText(password)
        dismissFormKeyboard(app)
        // Wait for Ionic's last input update before sending a credential. A
        // second local edit is allowed before the sole network submission;
        // there is no retry of a failed login or any uncertain server action.
        func inputLengthReady() -> Bool {
            let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value MATCHES %@", "^.{\(password.count)}$"), object: secret)
            return XCTWaiter.wait(for: [ready], timeout: 3) == .completed
        }
        var valueCountMatches = inputLengthReady()
        if !valueCountMatches {
            secret.tap(); clearExistingInput(secret); secret.typeText(password)
            dismissFormKeyboard(app); valueCountMatches = inputLengthReady()
        }
        print("FP_LOGIN_INPUT lengthMatches=" + String(valueCountMatches))
        XCTAssertTrue(valueCountMatches, "La saisie sécurisée complète doit être confirmée avant l’unique envoi.")
        XCTAssertTrue((login.value as? String) == user, "Identifiant saisi non confirmé.")
        let button = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Se connecter")).firstMatch
        XCTAssertTrue(button.exists && button.isEnabled, "Le formulaire doit être valide avant envoi.")
        for _ in 0..<4 {
            let keyboard = app.keyboards.firstMatch
            if button.isHittable && (!keyboard.exists || button.frame.maxY < keyboard.frame.minY - 8) { break }
            viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.40))
                .press(forDuration: 0.05, thenDragTo: viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.18)))
        }
        screenshot(app, "diagnostic-formulaire-avant-envoi")
        button.tap()
        let authenticated = element(app, "Bonjour " + user).waitForExistence(timeout: 30)
        screenshot(app, "diagnostic-resultat-connexion")
        if !authenticated {
            for (label, status) in [
                ("Identifiant ou mot de passe incorrect.", 401),
                ("Accès à la connexion refusé. Réessayez ou contactez votre support.", 403),
                ("Trop de tentatives. Patientez avant de réessayer.", 429),
                ("Le service de connexion est indisponible. Vérifiez votre connexion puis réessayez.", 0)
            ] where element(app, label).exists {
                print("FP_LOGIN_UI_FAILURE status=" + String(status))
            }
        }
        XCTAssertTrue(authenticated, "Connexion ou accueil attendu indisponible.")
    }
    @MainActor private func openIncident(_ app: XCUIApplication, reference: String) {
        for page in 0..<4 {
            let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element(app, "Actualiser"))
            XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)
            if page > 0 { for _ in 0..<10 { app.webViews.firstMatch.swipeDown() } }
            let fixture = app.webViews.buttons.matching(NSPredicate(format: "label CONTAINS %@", reference)).firstMatch
            for _ in 0..<16 {
                if fixture.exists && fixture.isHittable {
                    print("FP_NATIVE_INCIDENT_PAGE ordinal=" + String(page + 1))
                    fixture.tap(); return
                }
                app.webViews.firstMatch.swipeUp()
            }
            let next = element(app, "Suivant")
            guard next.exists && next.isEnabled else { break }
            for _ in 0..<5 {
                if next.isHittable { break }
                app.webViews.firstMatch.swipeUp()
            }
            XCTAssertTrue(next.isHittable); next.tap()
        }
        XCTFail("Dossier synthétique autorisé absent des quatre pages contrôlées.")
    }

    @MainActor private func pickerScreen(_ name: String) {
        let proof = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        proof.name = "preuve-" + name; proof.lifetime = .keepAlways
        add(proof)
    }
    @MainActor private func screenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "preuve-" + name; attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor func test01LoginRequired() {
        let started = Date()
        let app = launch()
        screenshot(app, "ios01-etat-initial-avant-attente")
        let ready = element(app, "Se connecter").waitForExistence(timeout: 60)
        print("FP_INITIAL_LOGIN seconds=" + String(Int(Date().timeIntervalSince(started))))
        XCTAssertTrue(ready, "Premier écran de connexion indisponible sur le simulateur froid.")
        XCTAssertTrue(app.webViews.textFields.firstMatch.exists)
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.exists)
        XCTAssertFalse(app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Se connecter")).firstMatch.isEnabled)
        screenshot(app, "ios01-connexion-publique")
    }
    @MainActor func test02PublicOnboardingEntry() {
        let app = launch(); tap(app, "Commencer mon affiliation")
        XCTAssertTrue(element(app, "Préparer et suivre votre affiliation").waitForExistence(timeout: 20))
        XCTAssertFalse(app.webViews.secureTextFields.firstMatch.exists)
        screenshot(app, "ios02-entree-affiliation")
    }
    @MainActor func test03AccountRecoveryEntry() {
        let app = launch(); tap(app, "Mot de passe oublié ?")
        XCTAssertTrue(element(app, "Récupérer mon compte").waitForExistence(timeout: 20))
        screenshot(app, "ios03-entree-recuperation")
    }
    @MainActor func test04MerchantSessionRestoreAndLogout() throws {
        let app = launch(); try login(app)
        XCTAssertTrue(element(app, "BIM").waitForExistence(timeout: 25), "Le nom du commerce doit provenir du rattachement actif renvoyé par le serveur.")
        screenshot(app, "ios04-accueil-commercant")
        app.terminate(); app.launch()
        let user = ProcessInfo.processInfo.environment["FP_IOS_TEST_USER"] ?? "bim.amina"
        let restored = element(app, "Bonjour " + user).waitForExistence(timeout: 30)
        screenshot(app, "diagnostic-restauration-apres-relance")
        XCTAssertTrue(restored, "Restauration Keychain non confirmée.")
        XCTAssertTrue(element(app, "BIM").waitForExistence(timeout: 25), "Le commerce autorisé doit rester visible après restauration.")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
        app.terminate(); app.launch()
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20), "Une session subsiste après déconnexion.")
        screenshot(app, "ios04-deconnexion-durable")
    }
    @MainActor func test05MerchantIncidentsEntry() throws {
        let app = launch(); try login(app)
        XCTAssertTrue(element(app, "BIM").waitForExistence(timeout: 25), "Le commerce actif autorisé doit être affiché à partir de l’API.")
        screenshot(app, "ios05-commerce-rattache")
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        XCTAssertTrue(element(app, "Déclarer un incident").waitForExistence(timeout: 30))
        XCTAssertTrue(element(app, "Exporter les références autorisées").exists)
        screenshot(app, "ios05-assistance-commercant")
        // This test only reads the real incident screen. It does not claim a deposit,
        // photo, support decision, isolation attack or full onboarding acceptance.
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor private func dismissFormKeyboard(_ app: XCUIApplication) {
        let done = app.toolbars.buttons.matching(NSPredicate(format: "label IN {'OK', 'Done', 'Terminé'}")).firstMatch
        for _ in 0..<4 {
            if !done.exists { return }
            let frame = done.frame, bounds = app.frame
            if !frame.isEmpty && !frame.isNull && !frame.isInfinite && bounds.contains(CGPoint(x: frame.midX, y: frame.midY)) {
                // The simulator accessibility hit-point resolver can fail while the
                // toolbar animates. Tap the same visible button at its actual frame.
                app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: frame.midX - bounds.minX, dy: frame.midY - bounds.minY)).tap()
                return
            }
            _ = XCTWaiter.wait(for: [], timeout: 0.2)
        }
    }
    @MainActor private func fillIncidentField(_ app: XCUIApplication, field: XCUIElement, value: String) {
        XCTAssertTrue(field.waitForExistence(timeout: 15))
        for _ in 0..<7 {
            if field.isHittable { break }
            app.webViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(field.isHittable); field.tap(); field.typeText(value)
        dismissFormKeyboard(app)
    }
    @MainActor func test06NativeIncidentDraftCreation() throws {
        let app = launch(); try login(app)
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        let declaration = element(app, "Déclarer un incident")
        XCTAssertTrue(declaration.waitForExistence(timeout: 30))
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: declaration)
        XCTAssertEqual(XCTWaiter.wait(for: [loaded], timeout: 25), .completed, "Le périmètre BIM doit être chargé avant le dépôt.")
        tap(app, "Déclarer un incident")
        XCTAssertTrue(element(app, "Nouvelle déclaration").waitForExistence(timeout: 15))
        screenshot(app, "ios06-formulaire-avant-saisie")
        let beneficiary = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR label BEGINSWITH %@", "Choisir un dossier autorisé", "Commerce ou dossier bénéficiaire")).firstMatch
        XCTAssertTrue(beneficiary.exists && beneficiary.isHittable, "Sélecteur bénéficiaire natif indisponible.")
        beneficiary.tap()
        let wheel = app.pickerWheels.firstMatch
        if wheel.waitForExistence(timeout: 3) {
            // This exact label was read from the existing BIM fixture on OCI.
            wheel.adjust(toPickerWheelValue: "BIM")
            dismissFormKeyboard(app)
        } else {
            let option = app.buttons.matching(NSPredicate(format: "label == %@", "BIM")).firstMatch
            XCTAssertTrue(option.waitForExistence(timeout: 5)); option.tap()
        }
        let subject = "iOS natif " + UUID().uuidString.prefix(8).lowercased()
        fillIncidentField(app, field: app.webViews.textFields.matching(NSPredicate(format: "label == %@", "Objet")).firstMatch, value: subject)
        fillIncidentField(app, field: app.webViews.textViews.matching(NSPredicate(format: "label == %@", "Description")).firstMatch, value: "Brouillon fictif créé depuis le simulateur iPhone. Aucun traitement financier.")
        tap(app, "Enregistrer le brouillon")
        XCTAssertTrue(element(app, "Brouillon enregistré.").waitForExistence(timeout: 30), "Aucun accusé natif de sauvegarde reçu.")
        XCTAssertTrue(element(app, subject).exists)
        // Only a synthetic subject, never credentials, is exported for server reconciliation.
        print("FP_NATIVE_DRAFT subject=" + subject)
        for _ in 0..<3 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios06-brouillon-natif-enregistre")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor func test06bNativeIncidentSubmission() throws {
        let app = launch(); try login(app)
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        let declaration = element(app, "Déclarer un incident")
        XCTAssertTrue(declaration.waitForExistence(timeout: 30))
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: declaration)
        XCTAssertEqual(XCTWaiter.wait(for: [loaded], timeout: 25), .completed, "Le périmètre BIM doit être chargé avant le dépôt.")
        tap(app, "Déclarer un incident")
        XCTAssertTrue(element(app, "Nouvelle déclaration").waitForExistence(timeout: 15))
        screenshot(app, "ios06-formulaire-avant-saisie")
        let beneficiary = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR label BEGINSWITH %@", "Choisir un dossier autorisé", "Commerce ou dossier bénéficiaire")).firstMatch
        XCTAssertTrue(beneficiary.exists && beneficiary.isHittable, "Sélecteur bénéficiaire natif indisponible.")
        beneficiary.tap()
        let wheel = app.pickerWheels.firstMatch
        if wheel.waitForExistence(timeout: 3) {
            // This exact label was read from the existing BIM fixture on OCI.
            wheel.adjust(toPickerWheelValue: "BIM")
            dismissFormKeyboard(app)
        } else {
            let option = app.buttons.matching(NSPredicate(format: "label == %@", "BIM")).firstMatch
            XCTAssertTrue(option.waitForExistence(timeout: 5)); option.tap()
        }
        let subject = "iOS depot " + UUID().uuidString.prefix(8).lowercased()
        fillIncidentField(app, field: app.webViews.textFields.matching(NSPredicate(format: "label == %@", "Objet")).firstMatch, value: subject)
        fillIncidentField(app, field: app.webViews.textViews.matching(NSPredicate(format: "label == %@", "Description")).firstMatch, value: "Déclaration fictive envoyée depuis le simulateur iPhone. Aucun traitement financier.")
        tap(app, "Envoyer")
        let receipt = app.webViews.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Dépôt confirmé : INC-")).firstMatch
        XCTAssertTrue(receipt.waitForExistence(timeout: 30), "Aucun accusé natif de dépôt reçu.")
        XCTAssertTrue(element(app, subject).exists)
        // Only a synthetic subject, never credentials, is exported for server reconciliation.
        print("FP_NATIVE_SUBMISSION subject=" + subject)
        for _ in 0..<3 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios07-depot-natif-confirme")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor private func openSupportFixture(_ app: XCUIApplication, reference: String) {
        for _ in 0..<14 { app.webViews.firstMatch.swipeDown() }
        let card = app.webViews.buttons.matching(NSPredicate(format: "label CONTAINS %@", reference)).firstMatch
        for _ in 0..<18 {
            if card.exists && card.isHittable { break }
            app.webViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(card.exists && card.isHittable, "Le dossier fictif autorisé doit être accessible.")
        card.tap()
        XCTAssertTrue(element(app, "Pièces jointes").waitForExistence(timeout: 20))
    }
    @MainActor private func merchantAction(_ app: XCUIApplication, action: String, text: String) {
        let select = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Action")).firstMatch
        for _ in 0..<15 {
            if select.exists && select.isHittable { break }
            app.webViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(select.exists && select.isHittable)
        if (select.value as? String) != action {
            select.tap()
            let wheel = app.pickerWheels.firstMatch
            if wheel.waitForExistence(timeout: 3) {
                wheel.adjust(toPickerWheelValue: action); dismissFormKeyboard(app)
            } else {
                let prefix = action.hasPrefix("Contester") ? "Contester" : action.hasPrefix("Confirmer personnellement") ? "Confirmer personnellement" : action
                let option = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH[cd] %@", prefix)).firstMatch
                screenshot(app, action.hasPrefix("Contester") ? "diagnostic-menu-reouverture" : "diagnostic-menu-action")
                XCTAssertTrue(option.waitForExistence(timeout: 5), "Choix natif de l’action indisponible ; voir le menu capturé.")
                if !option.isHittable && app.menus.firstMatch.exists { app.menus.firstMatch.swipeUp() }
                XCTAssertTrue(option.isHittable); option.tap()
            }
        }
        let message = app.webViews.textViews.matching(NSPredicate(format: "label == %@", "Message ou motif")).firstMatch
        fillIncidentField(app, field: message, value: text)
        tap(app, action)
        XCTAssertTrue(element(app, "Action enregistrée.").waitForExistence(timeout: 25))
    }
    @MainActor func test07NativeSupportReplyAndResolution() throws {
        let app = launch(); try login(app)
        let env = ProcessInfo.processInfo.environment
        guard let reply = env["FP_IOS_REPLY_REFERENCE"], let confirm = env["FP_IOS_CONFIRM_REFERENCE"],
              let reopen = env["FP_IOS_REOPEN_REFERENCE"], let publicMarker = env["FP_IOS_PUBLIC_MESSAGE"],
              let internalMarker = env["FP_IOS_INTERNAL_MESSAGE"], let answer = env["FP_IOS_REPLY_MESSAGE"] else {
            throw XCTSkip("Les trois dossiers support natifs ne sont pas préparés.")
        }
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        XCTAssertTrue(element(app, "Déclarer un incident").waitForExistence(timeout: 30))
        openSupportFixture(app, reference: reply)
        XCTAssertTrue(element(app, publicMarker).waitForExistence(timeout: 20))
        XCTAssertFalse(element(app, internalMarker).exists, "Une note interne ne doit jamais être affichée au commerçant.")
        XCTAssertFalse(element(app, "Ajouter une note interne").exists)
        merchantAction(app, action: "Répondre", text: answer)
        XCTAssertTrue(element(app, answer).waitForExistence(timeout: 15))
        screenshot(app, "ios11-reponse-commercant-sans-note-interne")
        print("FP_NATIVE_SUPPORT reply=true")
        openSupportFixture(app, reference: confirm)
        merchantAction(app, action: "Confirmer personnellement la résolution", text: "Résolution fictive confirmée depuis le client iPhone.")
        screenshot(app, "ios12-resolution-confirmee")
        print("FP_NATIVE_SUPPORT confirm=true")
        openSupportFixture(app, reference: reopen)
        merchantAction(app, action: "Contester et réouvrir", text: "Réouverture fictive demandée depuis le client iPhone.")
        screenshot(app, "ios12-reouverture-commercant")
        print("FP_NATIVE_SUPPORT reopen=true")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }


    @MainActor func test09bSaveNativeAttachmentCopy() throws {
        let app = launch(); try login(app)
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        XCTAssertTrue(element(app, "Déclarer un incident").waitForExistence(timeout: 30))
        openIncident(app, reference: "INC-90b54e47-6fec-4f03-b47d-69cd0ceb1aed")
        let download = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Télécharger")).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 20)); XCTAssertTrue(download.isEnabled)
        tap(app, "Télécharger")
        let save = app.buttons.matching(NSPredicate(format: "label ==[c] %@ OR label ==[c] %@", "Enregistrer", "Save")).firstMatch
        if !save.waitForExistence(timeout: 15) || !save.isEnabled {
            let local = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR label == %@", "Sur mon iPhone", "On My iPhone")).firstMatch
            XCTAssertTrue(local.waitForExistence(timeout: 10)); local.tap()
        }
        if save.exists && !save.isEnabled {
            let downloads = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR label == %@", "Téléchargements", "Downloads")).firstMatch
            if downloads.exists && downloads.isHittable { downloads.tap() }
        }
        screenshot(app, "ios10-destination-avant-copie")
        XCTAssertTrue(save.waitForExistence(timeout: 15)); XCTAssertTrue(save.isEnabled && save.isHittable)
        save.tap()
        XCTAssertTrue(element(app, "Copie enregistrée.").waitForExistence(timeout: 30), "La copie doit être confirmée par le sélecteur système.")
        for _ in 0..<7 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios10-copie-enregistree")
        print("FP_NATIVE_EXPORT saved=true")
        // The subsequent test10 restores the actual session before host reinstall.
    }

    @MainActor func test09cNativeMerchantIsolation() throws {
        let app = launch(); try login(app)
        XCTAssertTrue(element(app, "BIM").waitForExistence(timeout: 25))
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        let refresh = element(app, "Actualiser")
        XCTAssertTrue(refresh.waitForExistence(timeout: 30))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: refresh)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)
        let reference = "INC-90b54e47-6fec-4f03-b47d-69cd0ceb1aed"
        openIncident(app, reference: reference)
        XCTAssertTrue(element(app, "Télécharger").waitForExistence(timeout: 20), "Le ticket privé BIM doit être réellement consultable par son commerçant.")
        for _ in 0..<7 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios03-bim-ticket-prive-avant-changement")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
        try login(app, userOverride: "carrefour.leila")
        let commerce = app.webViews.staticTexts.matching(NSPredicate(format: "label ==[cd] %@", "Carrefour")).firstMatch
        XCTAssertTrue(commerce.waitForExistence(timeout: 25), "Le commerce Carrefour doit être chargé depuis son propre rattachement.")
        XCTAssertFalse(element(app, "BIM").exists)
        XCTAssertFalse(element(app, "Bonjour bim.amina").exists)
        screenshot(app, "ios03-carrefour-accueil-apres-changement")
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        let create = element(app, "Déclarer un incident")
        XCTAssertTrue(create.waitForExistence(timeout: 30))
        let contextReady = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: create)
        XCTAssertEqual(XCTWaiter.wait(for: [contextReady], timeout: 30), .completed)
        tap(app, "Actualiser")
        let refreshed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element(app, "Actualiser"))
        XCTAssertEqual(XCTWaiter.wait(for: [refreshed], timeout: 30), .completed)
        let count = app.webViews.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "[0-9]+ incident\\(s\\) dans votre périmètre\\.")).firstMatch
        XCTAssertTrue(count.waitForExistence(timeout: 15), "La liste Carrefour doit avoir un compteur chargé.")
        XCTAssertFalse(element(app, "Liste indisponible, module désactivé ou accès refusé.").exists)
        XCTAssertFalse(element(app, "Les acquéreurs autorisés sont indisponibles.").exists)
        XCTAssertFalse(app.webViews.buttons.matching(NSPredicate(format: "label CONTAINS %@", reference)).firstMatch.exists)
        XCTAssertFalse(element(app, "Copie enregistrée.").exists)
        screenshot(app, "ios03-carrefour-incidents-sans-ticket-bim")
        print("FP_NATIVE_ISOLATION switched=true ownRead=true foreignReferenceAbsent=true")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
    }

    @MainActor func test09dNativeImageAttachmentUpload() throws {
        guard let reference = ProcessInfo.processInfo.environment["FP_IOS_UPLOAD_REFERENCE"], reference.hasPrefix("INC-") else {
            throw XCTSkip("Le dossier synthétique du dépôt natif n'est pas préparé.")
        }
        let app = launch(); try login(app)
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        XCTAssertTrue(element(app, "Déclarer un incident").waitForExistence(timeout: 30))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: element(app, "Actualiser"))
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)
        openIncident(app, reference: reference)
        let upload = element(app, "Ajouter un fichier ou une photo")
        XCTAssertTrue(upload.waitForExistence(timeout: 20))
        for _ in 0..<18 {
            if upload.exists && upload.isHittable { break }
            app.webViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(upload.exists && upload.isHittable)
        screenshot(app, "ios09-piece-avant-selecteur")
        upload.tap()
        screenshot(app, "ios09-choix-source-image")
        if ProcessInfo.processInfo.environment["FP_IOS_UPLOAD_SOURCE"] == "files" {
            let choose = app.buttons.matching(NSPredicate(format: "label IN %@", ["Choisir le fichier", "Choisir des fichiers", "Choose File", "Choose Files", "Browse"])).firstMatch
            XCTAssertTrue(choose.waitForExistence(timeout: 10), "La sélection Fichiers doit être proposée.")
            choose.tap()
            pickerScreen("ios09-selecteur-fichiers-avant-recherche")
            let stem = "incident-d41ce29b-7cfd-439f-bf38-77032b23d605"
            let file = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", stem)).firstMatch
            if !file.waitForExistence(timeout: 12) {
                let browse = app.buttons.matching(NSPredicate(format: "label IN %@", ["Explorer", "Parcourir", "Browse"])).firstMatch
                if browse.exists && browse.isHittable { browse.tap() }
                let local = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Sur mon iPhone", "On My iPhone"])).firstMatch
                if local.waitForExistence(timeout: 5) && local.isHittable { local.tap() }
                if !file.waitForExistence(timeout: 5) {
                    let downloads = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Téléchargements", "Downloads"])).firstMatch
                    if downloads.exists && downloads.isHittable { downloads.tap() }
                }
            }
            pickerScreen("ios09-selecteur-fichiers-apres-recherche")
            XCTAssertTrue(file.waitForExistence(timeout: 10) && file.isHittable, "La copie créée par le vrai sélecteur Enregistrer doit être disponible.")
            pickerScreen("ios09-selecteur-fichier-synthetique")
            file.tap()
            let open = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ouvrir", "Open"])).firstMatch
            if open.waitForExistence(timeout: 2) && open.isEnabled && open.isHittable { open.tap() }
        } else {
            let library = app.buttons.matching(NSPredicate(format: "label IN %@", ["Photothèque", "Photo Library", "Choisir une photo", "Choose Photo", "Choisir une photo ou une vidéo"])).firstMatch
            XCTAssertTrue(library.waitForExistence(timeout: 10), "La photothèque native doit être proposée explicitement.")
            library.tap()
            pickerScreen("ios09-selecteur-systeme-avant-recherche")
            // A disposable hosted simulator contains only the prepared synthetic asset.
            // Acknowledge a real first-use UI if present; never change TCC with a command.
            for _ in 0..<2 {
                let consent = app.buttons.matching(NSPredicate(format: "label IN %@", ["Continuer", "Continue"])).firstMatch
                if consent.waitForExistence(timeout: 1) && consent.isHittable {
                    pickerScreen("ios09-consentement-phototheque")
                    consent.tap()
                } else { break }
            }
            // Run45106459090 proves that the remote picker may still display
            // "Chargement..." after25seconds. Wait for a real hittable thumbnail;
            // never tap a coordinate while the gallery is still loading.
            // The newly imported synthetic mire is first in the reviewed grid;
            // sample Photos assets may also exist. Exact server SHA remains mandatory.
            let asset = NSPredicate(format: "label BEGINSWITH[cd] %@ OR label MATCHES[cd] %@ OR label BEGINSWITH[cd] %@ OR label BEGINSWITH[cd] %@", "Photo,", "Photo [0-9].*", "Image,", "FP-IOS-SYNTHETIC-UPLOAD")
            let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let bounds = app.frame
            var selected: XCUIElement?
            let deadline = Date().addingTimeInterval(120)
            repeat {
                for candidate in [app.descendants(matching: .any).matching(asset).firstMatch,
                                  app.collectionViews.cells.firstMatch,
                                  system.descendants(matching: .any).matching(asset).firstMatch] {
                    if candidate.exists && candidate.isHittable { selected = candidate; break }
                }
                if selected == nil {
                    // Match the actual first gallery thumbnail, not the Photos logo,
                    // an arbitrary system control or a tap into the blank loading view.
                    let thumbnails = app.images.allElementsBoundByIndex.filter { image in
                        let f = image.frame
                        return image.exists && f.width > bounds.width * 0.20
                            && f.width < bounds.width * 0.50 && f.height > bounds.width * 0.20
                            && f.minX < bounds.minX + bounds.width * 0.10
                            && f.minY > bounds.minY + bounds.height * 0.20
                            && f.maxY < bounds.maxY - bounds.height * 0.10
                    }.sorted { $0.frame.minY < $1.frame.minY }
                    selected = thumbnails.first
                }
                if selected != nil { break }
                _ = XCTWaiter.wait(for: [], timeout: 0.5)
            } while Date() < deadline
            pickerScreen("ios09-selecteur-systeme-apres-recherche")
            print("FP_PHOTO_PICKER appCells=" + String(app.collectionViews.cells.count) + " appImages=" + String(app.images.count) + " assetFound=" + String(selected != nil))
            for (index, image) in app.images.allElementsBoundByIndex.prefix(15).enumerated() {
                let f = image.frame
                print("FP_PHOTO_IMAGE index=\(index) x=\(f.minX) y=\(f.minY) w=\(f.width) h=\(f.height) hittable=\(image.isHittable)")
            }
            guard let photo = selected else {
                XCTFail("La photothèque doit avoir terminé son chargement et rendre la mire sélectionnable.")
                return
            }
            pickerScreen("ios09-selecteur-photo-synthetique")
            // Run45109939644: native Photos exposes real thumbnail images with
            // valid visible frames but isHittable=false (decorative image children).
            // Touch the centre of the actual resolved thumbnail, never a fixed
            // screen coordinate or the blank loading view. Exact server SHA stays required.
            let thumbnailFrame = photo.frame
            let thumbnailCentre = CGPoint(x: thumbnailFrame.midX, y: thumbnailFrame.midY)
            XCTAssertTrue(bounds.contains(thumbnailCentre) && thumbnailFrame.width > bounds.width * 0.20)
            if photo.isHittable { photo.tap() }
            else {
                app.coordinate(withNormalizedOffset: .zero)
                    .withOffset(CGVector(dx: thumbnailCentre.x - bounds.minX, dy: thumbnailCentre.y - bounds.minY)).tap()
            }
            pickerScreen("ios09-photo-apres-toucher-vignette")
            for owner in [app, XCUIApplication(bundleIdentifier: "com.apple.springboard")] {
                let add = owner.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Ajouter", "Add", "Ajouter (1)", "Add (1)", "Choisir", "Choose", "Utiliser la photo", "Use Photo", "Ouvrir", "Open", "Terminé", "Done", "Ajouter la photo", "Add Photo"])).firstMatch
                if add.waitForExistence(timeout: 2) && add.isEnabled && add.isHittable {
                    pickerScreen("ios09-photo-avant-confirmer-selection")
                    add.tap()
                    break
                }
            }
            pickerScreen("ios09-photo-apres-confirmation")
        }
        let received = app.webViews.staticTexts.matching(NSPredicate(format: "label IN %@", ["Pièce contrôlée.", "Pièce reçue ; consultation bloquée tant que le contrôle de sécurité n’est pas validé."])).firstMatch
        let acknowledged = received.waitForExistence(timeout: 35)
        pickerScreen("ios09-retour-apres-attente-depot")
        XCTAssertTrue(acknowledged, "Le serveur doit accuser réception du dépôt depuis le sélecteur.")
        XCTAssertFalse(element(app, "Ajout non confirmé ou refusé. Actualisez les pièces avant un nouvel envoi.").exists)
        for _ in 0..<7 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios09-piece-recue-depuis-selecteur")
        print("FP_NATIVE_UPLOAD pickerSelected=true serverAcknowledged=true")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor func test09eReadUploadedAttachment() throws {
        let app = launch(); try login(app)
        guard let reference = ProcessInfo.processInfo.environment["FP_IOS_RECEIVED_FILE_REFERENCE"] else { XCTFail("La pièce déjà reçue doit être liée avant la lecture."); return }
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        openIncident(app, reference: reference)
        let clean = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "image/png", "CLEAN")).firstMatch
        for _ in 0..<18 {
            if clean.exists && clean.isHittable { break }
            app.webViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(clean.exists && clean.isHittable, "La pièce contrôlée doit être réellement affichée.")
        let download = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Télécharger")).firstMatch
        XCTAssertTrue(download.exists && download.isEnabled)
        screenshot(app, "ios09-piece-fichiers-deja-recue-lecture-seule")
        print("FP_NATIVE_RECEIVED_ATTACHMENT read=true clean=true businessMutation=false")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor func test10SeedSessionForReinstall() throws {
        let app = launch(); try login(app)
        app.terminate(); app.launch()
        let user = ProcessInfo.processInfo.environment["FP_IOS_TEST_USER"] ?? "bim.amina"
        XCTAssertTrue(element(app, "Bonjour " + user).waitForExistence(timeout: 30), "La session doit être restaurée avant le test de réinstallation.")
        screenshot(app, "ios16-session-avant-desinstallation")
        print("FP_REINSTALL_SEED restored=true")
        app.terminate()
    }

    @MainActor func test08FreshInstallationRequiresLogin() {
        let app = launch()
        XCTAssertTrue(element(app, "Se connecter").waitForExistence(timeout: 60), "Une nouvelle installation doit demander une connexion.")
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.exists)
        let user = ProcessInfo.processInfo.environment["FP_IOS_TEST_USER"] ?? "bim.amina"
        XCTAssertFalse(element(app, "Bonjour " + user).exists, "Ancienne session reprise après désinstallation.")
        screenshot(app, "ios16-connexion-apres-reinstallation")
        print("FP_REINSTALL_RESULT loginRequired=true")
    }

    @MainActor func test09CancelNativeAttachmentCopy() throws {
        let app = launch(); try login(app)
        tap(app, "Ouvrir le menu"); tap(app, "Assistance · Incidents")
        XCTAssertTrue(element(app, "Déclarer un incident").waitForExistence(timeout: 30))
        // This private BIM draft and its CLEAN synthetic PNG were qualified on OCI.
        openIncident(app, reference: "INC-90b54e47-6fec-4f03-b47d-69cd0ceb1aed")
        let download = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Télécharger")).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 20))
        XCTAssertTrue(download.isEnabled, "Seule une pièce CLEAN doit être copiable.")
        let notice = "Enregistrement annulé. Le dossier reste disponible."
        XCTAssertFalse(element(app, notice).exists, "La nouvelle tentative ne doit pas réutiliser un ancien accusé.")
        tap(app, "Télécharger")
        let owners = [app, XCUIApplication(bundleIdentifier: "com.apple.springboard")]
        var cancel: XCUIElement?
        let deadline = Date().addingTimeInterval(35)
        var backs = 0
        // iOS26 may open inside "On My iPhone". Its top-left control navigates
        // back to Locations; Cancel is only available at that root screen.
        // Use the actual native navigation bar, never a guessed screen coordinate.
        while Date() < deadline {
            if element(app, notice).exists { print("FP_NATIVE_EXPORT cancelledAfterNativeNavigation=true"); break }
            for owner in owners {
                // Modern Files uses a native close toolbar item on some iOS runtimes.
                // Restrict Close/Fermer to native navigation bars, never a web detail button.
                let nativeClose = owner.navigationBars.buttons.matching(NSPredicate(format: "label IN %@", ["Annuler", "Cancel", "Fermer", "Close"])).firstMatch
                if nativeClose.exists && nativeClose.isEnabled && nativeClose.isHittable { cancel = nativeClose; break }
                let toolbarClose = owner.toolbars.buttons.matching(NSPredicate(format: "label BEGINSWITH[cd] %@ OR label BEGINSWITH[cd] %@", "Fermer", "Close")).firstMatch
                if toolbarClose.exists && toolbarClose.isEnabled && toolbarClose.isHittable { cancel = toolbarClose; break }
                let candidate = owner.buttons.matching(NSPredicate(format: "label ==[c] %@ OR label ==[c] %@", "Annuler", "Cancel")).firstMatch
                if candidate.exists && candidate.isEnabled && candidate.isHittable { cancel = candidate; break }
            }
            if cancel != nil { break }
            let save = app.buttons.matching(NSPredicate(format: "label ==[c] %@ OR label ==[c] %@", "Enregistrer", "Save")).firstMatch
            let back = app.navigationBars.buttons.firstMatch
            if backs < 3 && save.exists && back.exists && back.isEnabled {
                let frame = back.frame, bounds = app.frame
                if !frame.isEmpty && !frame.isNull && !frame.isInfinite && frame.midX < bounds.minX + bounds.width * 0.28 && frame.midY < bounds.minY + bounds.height * 0.30 {
                    back.tap(); backs += 1
                    print("FP_PICKER_BACK count=" + String(backs))
                }
            }
            _ = XCTWaiter.wait(for: [], timeout: 0.3)
        }
        pickerScreen("ios10-selecteur-copie-native")
        for owner in owners {
            for control in owner.navigationBars.buttons.allElementsBoundByIndex {
                print("FP_PICKER_NAV label=" + control.label + " enabled=" + String(control.isEnabled) + " hittable=" + String(control.isHittable))
            }
        }
        for label in ["Annuler", "Cancel", "Enregistrer", "Save", "OK", "Done"] {
            print("FP_PICKER_CONTROL label=" + label + " app=" + String(app.buttons[label].exists) + " system=" + String(owners[1].buttons[label].exists))
        }
        if !element(app, notice).exists {
            if let cancel = cancel {
                XCTAssertTrue(cancel.isEnabled && cancel.isHittable)
                cancel.tap()
            } else {
                // The captured iOS26 sheet has no Cancel button. Perform the
                // actual system pull-down gesture; never call a plugin/test hook
                // or infer success from the disappearance of the picker.
                let window = app.windows.firstMatch
                XCTAssertTrue(window.exists && !window.frame.isEmpty)
                let start = window.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.10))
                let finish = window.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.82))
                start.press(forDuration: 0.15, thenDragTo: finish)
                print("FP_PICKER_DISMISS gesture=system_sheet_pull_down")
                pickerScreen("ios10-apres-glissement-annulation")
            }
        }
        XCTAssertTrue(element(app, notice).waitForExistence(timeout: 20), "Une annulation ne doit pas être présentée comme une copie réussie.")
        for _ in 0..<7 { app.webViews.firstMatch.swipeDown() }
        screenshot(app, "ios10-annulation-confirmee")
        print("FP_NATIVE_EXPORT cancelled=true")
        // Keep this existing session for the following host-controlled reinstall phase.
    }


    // Existing approved synthetic case, positively checked against OCI on 2026-11-23.
    // Read only: no save, approval, upload, invitation or registration is performed.
    @MainActor func test11PersonalDossierReadOnly() throws {
        var user = "fp-u000004", reference = "ONB-D1DB521C"
        if let raw = ProcessInfo.processInfo.environment["FP_IOS_APPROVED_READ_FIXTURE"],
           let bytes = raw.data(using: .utf8),
           let fixture = try JSONSerialization.jsonObject(with: bytes) as? [String: String],
           let boundUser = fixture["login"], let boundReference = fixture["reference"] {
            user = boundUser; reference = boundReference
        }
        let app = launch(); try login(app, userOverride: user)
        XCTAssertTrue(element(app, reference).waitForExistence(timeout: 30))
        screenshot(app, "ios05-compte-personnel-dossier-existant")
        tap(app, "Ouvrir le menu")
        screenshot(app, "ios05-menu-personnel-avant-depliage")
        tap(app, "Mon commerce"); tap(app, "Mon dossier")
        XCTAssertTrue(element(app, "Dossier commercant et PDV").waitForExistence(timeout: 30))
        XCTAssertTrue(element(app, reference).exists)
        XCTAssertTrue(element(app, "APPROVED").exists)
        screenshot(app, "ios05-dossier-approuve-lecture-seule")
        print("FP_NATIVE_ONBOARDING existingPersonalDossierRead=true approved=true businessMutation=false")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }
    @MainActor func test12CommercialOnboardingReadOnly() throws {
        let app = launch(); try login(app, userOverride: "damancash.karim")
        tap(app, "Ouvrir le menu")
        screenshot(app, "ios06-menu-commercial-avant-depliage")
        tap(app, "Activité commerciale"); tap(app, "Nouveau prospect")
        XCTAssertTrue(element(app, "Créer un prospect commerçant").waitForExistence(timeout: 30))
        let create = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Créer et inviter")).firstMatch
        XCTAssertTrue(create.exists)
        XCTAssertFalse(create.isEnabled, "Un prospect vide et sans consentement ne doit pas être envoyé.")
        XCTAssertTrue(app.webViews.textFields.firstMatch.exists)
        screenshot(app, "ios06-prospect-commercial-vide-non-envoye")
        tap(app, "Ouvrir le menu"); tap(app, "Dossiers commerciaux")
        XCTAssertTrue(element(app, "Mes dossiers").waitForExistence(timeout: 30))
        let filter = app.webViews.searchFields.firstMatch
        XCTAssertTrue(filter.waitForExistence(timeout: 30), "La liste doit réellement terminer son chargement.")
        screenshot(app, "ios06-liste-commerciale-reelle")
        print("FP_NATIVE_ONBOARDING commercialEntryRead=true commercialListLoaded=true businessMutation=false")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    // One real public request from the app. Neutral acknowledgement is not an
    // activation, an identity allocation or proof of mailbox delivery.
    @MainActor func test13PublicPersonalRegistrationRequest() throws {
        let env = ProcessInfo.processInfo.environment
        guard let email = env["FP_IOS_ONBOARDING_EMAIL"], !email.isEmpty,
              let acquirerName = env["FP_IOS_ONBOARDING_ACQUIRER_NAME"], !acquirerName.isEmpty else {
            throw XCTSkip("Adresse de recette contrôlée et acquéreur requis.")
        }
        let app = launch(); tap(app, "Commencer mon affiliation")
        XCTAssertTrue(element(app, "Préparer et suivre votre affiliation").waitForExistence(timeout: 30))
        let selector = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR value == %@", "Acquéreur de votre affiliation", "Choisir un acquéreur")).firstMatch
        XCTAssertTrue(selector.waitForExistence(timeout: 30))
        selector.tap()
        let wheel = app.pickerWheels.firstMatch
        if wheel.waitForExistence(timeout: 5) {
            wheel.adjust(toPickerWheelValue: acquirerName); dismissFormKeyboard(app)
        } else {
            let choice = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", acquirerName)).firstMatch
            XCTAssertTrue(choice.waitForExistence(timeout: 10)); choice.tap()
        }
        let address = app.webViews.textFields.firstMatch
        XCTAssertTrue(address.exists); address.tap(); address.typeText(email); dismissFormKeyboard(app)
        let submit = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Demander mon accès sécurisé")).firstMatch
        XCTAssertTrue(submit.exists); XCTAssertFalse(submit.isEnabled, "Le consentement reste obligatoire.")
        tap(app, "J’accepte que ces informations soient utilisées pour ouvrir et suivre mon dossier d’affiliation.")
        XCTAssertTrue(submit.isEnabled)
        screenshot(app, "ios05-demande-publique-avant-envoi")
        submit.tap() // Exactly once; no uncertain-write retry.
        let accepted = app.webViews.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Votre demande a été reçue.")).firstMatch
        XCTAssertTrue(accepted.waitForExistence(timeout: 60))
        screenshot(app, "ios05-demande-publique-accuse-neutre")
        print("FP_NATIVE_ONBOARDING publicRegistrationAcknowledged=true mailReceiptNotInferred=true")
    }
    // Dedicated test case allocated by the real public request. All values are
    // synthetic, supplied only in the private XCTest environment, never in source.
    @MainActor private func revealDraftControl(_ app: XCUIApplication, _ control: XCUIElement, diagnostic: String = "contrôle") {
        let view = app.webViews.firstMatch
        for _ in 0..<30 {
            if control.exists {
                if !control.isEnabled { _ = XCTWaiter.wait(for: [], timeout: 0.5); continue }
                let frame = control.frame, visible = view.frame
                if control.isHittable && frame.minY > visible.minY + 12 && frame.maxY < visible.maxY - 10 { return }
                if !frame.isEmpty && frame.midY < visible.minY + 100 { view.swipeDown() }
                else { view.swipeUp() }
            } else { view.swipeUp() }
        }
        print("FP_NATIVE_DRAFT_MISSING label=" + diagnostic)
        for field in app.webViews.textFields.allElementsBoundByIndex.prefix(90) { print("FP_NATIVE_DRAFT_LABEL " + field.label) }
        screenshot(app, "ios05-diagnostic-champ-dossier")
        XCTFail("Contrôle du dossier inaccessible : " + diagnostic)
    }
    @MainActor private func selectDraftChoice(_ app: XCUIApplication, label: String, placeholder: String, choice: String) {
        // A wrapping HTML label is also exposed as StaticText. Selecting it as
        // firstMatch touched text, not the actual popup (run45146755455).
        let control = app.webViews.descendants(matching: .any).matching(NSPredicate(
            format: "(value == %@ OR label == %@ OR label == %@) AND elementType != %d",
            placeholder, placeholder, label, XCUIElement.ElementType.staticText.rawValue)).firstMatch
        revealDraftControl(app, control, diagnostic: label)
        if (control.value as? String) == choice {
            print("FP_NATIVE_DRAFT_CHOICE alreadySelected=true label=" + label)
            return
        }
        screenshot(app, "ios05-choix-avant-" + label)
        control.tap()
        screenshot(app, "ios05-choix-ouvert-" + label)
        let wheel = app.pickerWheels.firstMatch
        if wheel.waitForExistence(timeout: 3) {
            wheel.adjust(toPickerWheelValue: choice)
            dismissFormKeyboard(app)
        } else {
            // Current iOS may render a native menu instead of a wheel. Use the
            // visible menu item, never inject a DOM value or call a business API.
            let button = app.buttons.matching(NSPredicate(format: "label == %@", choice)).firstMatch
            let option = app.staticTexts.matching(NSPredicate(format: "label == %@", choice)).firstMatch
            if button.waitForExistence(timeout: 5) && button.isHittable { button.tap() }
            else {
                XCTAssertTrue(option.waitForExistence(timeout: 5) && option.isHittable, "Choix natif indisponible : " + label)
                option.tap()
            }
        }
        XCTAssertTrue((control.value as? String) == choice || control.label == choice,
                      "La valeur choisie doit être relue depuis le contrôle : " + label)
        screenshot(app, "ios05-choix-confirme-" + label)
    }
    @MainActor private func fillDraftFields(_ app: XCUIApplication, _ fields: [[String: Any]]) throws {
        for (number, row) in fields.enumerated() {
            let label = try XCTUnwrap(row["label"] as? String)
            let value = try XCTUnwrap(row["value"] as? String)
            let index = row["index"] as? Int ?? 0
            let prefix = row["prefix"] as? Bool ?? false
            let predicate = NSPredicate(format: prefix ? "label BEGINSWITH %@" : "label == %@", label)
            let control = app.webViews.textFields.matching(predicate).element(boundBy: index)
            revealDraftControl(app, control, diagnostic: label)
            XCTAssertTrue(control.isEnabled, "Le titulaire doit pouvoir modifier son brouillon.")
            control.tap(); clearExistingInput(control); control.typeText(value); dismissFormKeyboard(app)
            XCTAssertTrue((control.value as? String) == value, "La saisie du champ n'est pas confirmée : " + label)
            print("FP_NATIVE_DRAFT_FIELD index=" + String(number) + " confirmed=true")
        }
    }
    @MainActor func test14PersonalLegalDossierNativeSave() throws {
        guard let raw = ProcessInfo.processInfo.environment["FP_IOS_DRAFT_FIXTURE"],
              let data = raw.data(using: .utf8),
              let input = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw XCTSkip("Dossier dédié contrôlé requis ; aucune écriture revendiquée.")
        }
        let user = try XCTUnwrap(input["login"] as? String)
        let reference = try XCTUnwrap(input["reference"] as? String)
        let company = try XCTUnwrap(input["companyFields"] as? [[String: Any]])
        let outlet = try XCTUnwrap(input["outletFields"] as? [[String: Any]])
        let app = launch(); try login(app, userOverride: user)
        XCTAssertTrue(element(app, reference).waitForExistence(timeout: 30))
        tap(app, "Ouvrir le menu"); tap(app, "Mon commerce"); tap(app, "Mon dossier")
        XCTAssertTrue(element(app, "Dossier commercant et PDV").waitForExistence(timeout: 30))
        XCTAssertTrue(element(app, reference).exists); XCTAssertTrue(element(app, "DRAFT").exists)
        screenshot(app, "ios05-nouveau-dossier-draft-avant-saisie")
        selectDraftChoice(app, label: "Système de destination", placeholder: "Choisir une destination", choice: "FuturPayment Acquiring")
        selectDraftChoice(app, label: "Type", placeholder: "Personne morale", choice: "Personne morale")
        for field in company {
            if (field["label"] as? String) == "RIB" {
                selectDraftChoice(app, label: "Canal d’acceptation", placeholder: "Choisir un canal", choice: "TPE")
            }
            try fillDraftFields(app, [field])
        }
        let add = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Ajouter un PDV")).firstMatch
        revealDraftControl(app, add); add.tap()
        try fillDraftFields(app, outlet)
        let save = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Enregistrer le dossier complet")).firstMatch
        revealDraftControl(app, save)
        XCTAssertTrue(save.isEnabled, "Toutes les coordonnées obligatoires et le PDV doivent être complets.")
        screenshot(app, "ios05-dossier-complet-avant-enregistrement")
        save.tap() // One explicit business write. Never retry an uncertain save.
        let notice = element(app, "Dossier juridique, PDV, TPE et e-commerce enregistres.")
        for _ in 0..<28 {
            if notice.exists && notice.isHittable { break }
            app.webViews.firstMatch.swipeDown()
        }
        XCTAssertTrue(notice.waitForExistence(timeout: 45))
        screenshot(app, "ios05-dossier-juridique-enregistre")
        print("FP_NATIVE_ONBOARDING legalDossierSaved=true dedicatedAccount=true explicitSaveCount=1")
    }
    @MainActor private static var savedDossierLayoutVerified = false

    // Read the saved profile, focus its real input and preserve every value.
    // This must pass before the dependent KYC upload test is allowed to write.
    @MainActor func test14bSavedDossierKeyboardLayout() throws {
        Self.savedDossierLayoutVerified = false
        guard let raw = ProcessInfo.processInfo.environment["FP_IOS_KYC_FIXTURE"],
              let data = raw.data(using: .utf8),
              let input = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw XCTSkip("Prévol KYC requis pour le dossier enregistré.")
        }
        let user = try XCTUnwrap(input["login"] as? String)
        let reference = try XCTUnwrap(input["reference"] as? String)
        let app = launch()
        if element(app, "Bonjour bim.amina").waitForExistence(timeout: 5) {
            tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
            XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
        }
        try login(app, userOverride: user)
        XCTAssertTrue(element(app, reference).waitForExistence(timeout: 30))
        tap(app, "Ouvrir le menu"); tap(app, "Mon commerce"); tap(app, "Mon dossier")
        XCTAssertTrue(element(app, "Dossier commercant et PDV").waitForExistence(timeout: 30))
        XCTAssertTrue(element(app, reference).exists)
        let field = app.webViews.textFields.matching(NSPredicate(format: "label == %@", "Raison sociale")).firstMatch
        revealDraftControl(app, field, diagnostic: "raison sociale enregistrée")
        let initialValue = try XCTUnwrap(field.value as? String)
        XCTAssertFalse(initialValue.isEmpty)
        let before = field.frame
        XCTAssertGreaterThan(before.width, 80)
        screenshot(app, "ios05-clavier-avant-focus")
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        _ = XCTWaiter.wait(for: [], timeout: 1)
        let focused = field.frame, viewport = app.webViews.firstMatch.frame
        XCTAssertEqual(focused.width, before.width, accuracy: max(3, before.width * 0.03), "Le focus ne doit pas agrandir le formulaire.")
        XCTAssertGreaterThanOrEqual(focused.minX, viewport.minX - 2)
        XCTAssertLessThanOrEqual(focused.maxX, viewport.maxX + 2)
        screenshot(app, "ios05-clavier-focus-sans-zoom")
        dismissFormKeyboard(app)
        _ = XCTWaiter.wait(for: [], timeout: 0.5)
        XCTAssertEqual(field.value as? String, initialValue, "La vérification ne modifie pas la donnée.")
        XCTAssertEqual(field.frame.width, before.width, accuracy: max(3, before.width * 0.03))
        screenshot(app, "ios05-clavier-ferme-donnee-conservee")
        print("FP_NATIVE_LAYOUT focusStable=true horizontalClipping=false valuePreserved=true businessMutation=false")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        Self.savedDossierLayoutVerified = true
    }

    @MainActor func test00SavedKycDocumentStatusesReadOnly() throws {
        guard let raw = ProcessInfo.processInfo.environment["FP_IOS_KYC_READ_FIXTURE"],
              let data = raw.data(using: .utf8),
              let input = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw XCTSkip("Le contre-test en lecture seule exige le dossier dédié complet.")
        }
        let user = try XCTUnwrap(input["login"] as? String)
        let reference = try XCTUnwrap(input["reference"] as? String)
        let documents = try XCTUnwrap(input["documents"] as? [[String: Any]])
        XCTAssertEqual(documents.count, 6)
        let app = launch()
        if element(app, "Bonjour bim.amina").waitForExistence(timeout: 3) {
            tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        }
        try login(app, userOverride: user)
        XCTAssertTrue(element(app, reference).waitForExistence(timeout: 30))
        tap(app, "Ouvrir le menu"); tap(app, "Mon commerce"); tap(app, "Mon dossier")
        XCTAssertTrue(element(app, "Dossier commercant et PDV").waitForExistence(timeout: 30))
        var checked = 0
        for (index, row) in documents.enumerated() {
            let label = try XCTUnwrap(row["label"] as? String)
            let choose = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Choisir le justificatif : " + label)).firstMatch
            revealDraftControl(app, choose, diagnostic: "lecture KYC " + String(index + 1))
            let title = app.webViews.staticTexts.matching(NSPredicate(format: "label == %@", label)).firstMatch
            let pending = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "PENDING"))
            var visiblePending = false
            for _ in 0..<12 {
                let bounds = app.webViews.firstMatch.frame
                let top = title.frame.maxY, bottom = choose.frame.minY
                visiblePending = pending.allElementsBoundByIndex.contains { value in
                    let frame = value.frame
                    return !frame.isEmpty && frame.minY >= top - 2 && frame.maxY <= bottom + 2
                        && frame.minY > bounds.minY + 12 && frame.maxY < bounds.maxY - 12
                }
                if visiblePending { break }
                if top < bounds.minY + 24 { app.webViews.firstMatch.swipeDown() }
                else { app.webViews.firstMatch.swipeUp() }
            }
            screenshot(app, "ios05-lecture-kyc-piece-" + String(index + 1))
            XCTAssertTrue(visiblePending, "Le statut de la pièce déjà reçue doit être visible sur sa propre carte.")
            checked += 1
        }
        XCTAssertEqual(checked, 6)
        print("FP_NATIVE_KYC_READ documents=" + String(checked) + " pending=true businessMutation=false")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

    @MainActor func test15PersonalKycDocumentsNativeUpload() throws {
        XCTAssertTrue(Self.savedDossierLayoutVerified, "Le contrôle de lisibilité doit réussir avant tout dépôt KYC.")
        guard let raw = ProcessInfo.processInfo.environment["FP_IOS_KYC_FIXTURE"],
              let data = raw.data(using: .utf8),
              let input = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw XCTSkip("Prévol du dossier KYC dédié requis ; aucun dépôt revendiqué.")
        }
        let user = try XCTUnwrap(input["login"] as? String)
        let reference = try XCTUnwrap(input["reference"] as? String)
        let documents = try XCTUnwrap(input["documents"] as? [[String: Any]])
        let app = launch()
        // test09b saves the synthetic image as BIM. Switch by the normal logout UI.
        if element(app, "Bonjour bim.amina").waitForExistence(timeout: 5) {
            tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
            XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
        }
        try login(app, userOverride: user)
        XCTAssertTrue(element(app, reference).waitForExistence(timeout: 30))
        tap(app, "Ouvrir le menu"); tap(app, "Mon commerce"); tap(app, "Mon dossier")
        XCTAssertTrue(element(app, "Dossier commercant et PDV").waitForExistence(timeout: 30))
        XCTAssertTrue(element(app, reference).exists)
        for (index, row) in documents.enumerated() {
            let label = try XCTUnwrap(row["label"] as? String)
            let choose = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Choisir le justificatif : " + label)).firstMatch
            revealDraftControl(app, choose, diagnostic: "justificatif " + String(index + 1))
            XCTAssertTrue(choose.isEnabled); choose.tap()
            let browse = app.buttons.matching(NSPredicate(format: "label IN %@", ["Choisir le fichier", "Choisir des fichiers", "Choose File", "Choose Files", "Browse"])).firstMatch
            XCTAssertTrue(browse.waitForExistence(timeout: 10), "Le sélecteur Fichiers doit être proposé.")
            browse.tap()
            let stem = "incident-d41ce29b-7cfd-439f-bf38-77032b23d605"
            let file = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", stem)).firstMatch
            if !file.waitForExistence(timeout: 10) {
                let locations = app.buttons.matching(NSPredicate(format: "label IN %@", ["Explorer", "Parcourir", "Browse"])).firstMatch
                if locations.exists && locations.isHittable { locations.tap() }
                let local = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Sur mon iPhone", "On My iPhone"])).firstMatch
                if local.waitForExistence(timeout: 5) && local.isHittable { local.tap() }
                if !file.waitForExistence(timeout: 5) {
                    let downloads = app.descendants(matching: .any).matching(NSPredicate(format: "label IN %@", ["Téléchargements", "Downloads"])).firstMatch
                    if downloads.exists && downloads.isHittable { downloads.tap() }
                }
            }
            pickerScreen("ios05-kyc-selecteur-piece-" + String(index + 1))
            XCTAssertTrue(file.waitForExistence(timeout: 10) && file.isHittable, "Utiliser uniquement la copie synthétique enregistrée par le vrai sélecteur.")
            file.tap()
            let open = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ouvrir", "Open"])).firstMatch
            if open.waitForExistence(timeout: 2) && open.isEnabled && open.isHittable { open.tap() }
            let upload = app.webViews.buttons.matching(NSPredicate(format: "label == %@", "Téléverser le justificatif : " + label)).firstMatch
            revealDraftControl(app, upload, diagnostic: "dépôt " + String(index + 1))
            XCTAssertTrue(upload.isEnabled)
            upload.tap() // Exactly one upload per required type; no retry after uncertainty.
            // WebKit exposes only the visible document cards. Never count all
            // off-screen PENDING labels as a server acknowledgement.
            let title = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
            revealDraftControl(app, title, diagnostic: "état du justificatif " + String(index + 1))
            let pending = app.webViews.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "PENDING"))
            var ownPendingVisible = false
            for _ in 0..<12 {
                let top = title.frame.maxY
                let bottom = choose.frame.minY
                ownPendingVisible = pending.allElementsBoundByIndex.contains { value in
                    let frame = value.frame
                    return !frame.isEmpty && frame.minY >= top - 2 && frame.maxY <= bottom + 2
                        && frame.intersects(app.webViews.firstMatch.frame)
                }
                if ownPendingVisible { break }
                _ = XCTWaiter.wait(for: [], timeout: 0.5)
            }
            screenshot(app, "ios05-kyc-etat-apres-depot-" + String(index + 1))
            XCTAssertTrue(ownPendingVisible, "L'état du justificatif courant doit être visible entre son titre et sa sélection de fichier.")
            screenshot(app, "ios05-kyc-piece-deposee-" + String(index + 1))
            print("FP_NATIVE_KYC_UPLOADED index=" + String(index + 1) + " pending=true")
        }
        XCTAssertFalse(element(app, "Affiliation terminee").exists)
        print("FP_NATIVE_KYC completed=true uploads=" + String(documents.count) + " authenticityClaimed=false")
    }
}
