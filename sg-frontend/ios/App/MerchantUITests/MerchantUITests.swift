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
    @MainActor private func login(_ app: XCUIApplication) throws {
        let env = ProcessInfo.processInfo.environment
        guard env["FP_IOS_AUTHENTICATED_TESTS"] == "true",
              let password = env["FP_IOS_TEST_PASSWORD"], !password.isEmpty else {
            throw XCTSkip("Le secret de recette n'est pas configuré : aucun test authentifié revendiqué.")
        }
        let user = env["FP_IOS_TEST_USER"] ?? "bim.amina"
        if element(app, "Bonjour " + user).waitForExistence(timeout: 5) { return }
        let login = app.webViews.textFields.firstMatch
        XCTAssertTrue(login.waitForExistence(timeout: 15)); login.tap(); login.typeText(user)
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
        secret.typeText(password)
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
        XCTAssertTrue(authenticated, "Connexion ou accueil attendu indisponible.")
    }
    @MainActor private func screenshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "preuve-" + name; attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor func test01LoginRequired() {
        let app = launch()
        XCTAssertTrue(element(app, "Se connecter").waitForExistence(timeout: 20))
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
        screenshot(app, "ios04-accueil-commercant")
        app.terminate(); app.launch()
        let user = ProcessInfo.processInfo.environment["FP_IOS_TEST_USER"] ?? "bim.amina"
        let restored = element(app, "Bonjour " + user).waitForExistence(timeout: 30)
        screenshot(app, "diagnostic-restauration-apres-relance")
        XCTAssertTrue(restored, "Restauration Keychain non confirmée.")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20))
        app.terminate(); app.launch()
        XCTAssertTrue(app.webViews.secureTextFields.firstMatch.waitForExistence(timeout: 20), "Une session subsiste après déconnexion.")
        screenshot(app, "ios04-deconnexion-durable")
    }
    @MainActor func test05MerchantIncidentsEntry() throws {
        let app = launch(); try login(app)
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
        if done.exists && done.isHittable { done.tap() }
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
        screenshot(app, "ios06-brouillon-natif-enregistre")
        tap(app, "Ouvrir le menu"); tap(app, "Se déconnecter")
    }

}
