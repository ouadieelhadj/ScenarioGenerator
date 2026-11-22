import XCTest

final class MerchantUITests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    @MainActor private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(fr)", "-AppleLocale", "fr_FR"]
        app.launch()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 30))
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
        viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.40))
            .press(forDuration: 0.05, thenDragTo: viewport.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.18)))
        let secret = app.webViews.secureTextFields.firstMatch
        XCTAssertTrue(secret.waitForExistence(timeout: 10))
        _ = XCTWaiter.wait(for: [], timeout: 0.5)
        XCTAssertTrue(secret.isHittable, "Le champ sécurisé doit être visible avant saisie.")
        secret.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.5)).tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        _ = XCTWaiter.wait(for: [], timeout: 0.5)
        secret.typeText(password)
        tap(app, "Se connecter")
        XCTAssertTrue(element(app, "Bonjour " + user).waitForExistence(timeout: 30), "Connexion ou accueil attendu indisponible.")
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
        XCTAssertTrue(element(app, "Bonjour " + user).waitForExistence(timeout: 30), "Restauration Keychain non confirmée.")
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
}
