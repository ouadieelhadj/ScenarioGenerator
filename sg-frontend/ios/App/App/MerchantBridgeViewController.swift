import UIKit
import WebKit
import Capacitor

@objc(MerchantBridgeViewController)
final class MerchantBridgeViewController: CAPBridgeViewController {
    private var privacyCover: UIView?

    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(SecureSessionPlugin())
        bridge?.registerPluginInstance(IncidentFileDownloadPlugin())
        if #available(iOS 16.4, *) { webView?.isInspectable = false }
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        for name in [UIApplication.willResignActiveNotification, UIApplication.didBecomeActiveNotification,
                     UIScene.willDeactivateNotification, UIScene.didActivateNotification,
                     UIScene.didEnterBackgroundNotification, UIScreen.capturedDidChangeNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(privacyChanged(_:)), name: name, object: nil)
        }
    }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); updatePrivacy() }

    @objc private func privacyChanged(_ notification: Notification) {
        if let scene = notification.object as? UIScene, let own = view.window?.windowScene, scene !== own { return }
        let deactivating = notification.name == UIApplication.willResignActiveNotification
            || notification.name == UIScene.willDeactivateNotification
            || notification.name == UIScene.didEnterBackgroundNotification
        updatePrivacy(forceHidden: deactivating)
    }
    private func updatePrivacy(forceHidden: Bool = false) {
        let active = view.window?.windowScene.map { $0.activationState == .foregroundActive }
            ?? (UIApplication.shared.applicationState == .active)
        let hidden = forceHidden || !active || (view.window?.screen.isCaptured ?? false)
        if hidden && privacyCover == nil {
            let cover = UIView(frame: view.bounds)
            cover.backgroundColor = .systemBackground
            cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            cover.accessibilityIdentifier = "merchant-privacy-cover"
            cover.isAccessibilityElement = true
            cover.accessibilityViewIsModal = true
            cover.accessibilityLabel = "FuturPayment - affichage masqué"
            let label = UILabel()
            label.text = cover.accessibilityLabel
            label.textAlignment = .center
            label.numberOfLines = 0
            label.translatesAutoresizingMaskIntoConstraints = false
            cover.addSubview(label)
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: cover.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: cover.centerYAnchor),
                label.leadingAnchor.constraint(greaterThanOrEqualTo: cover.leadingAnchor, constant: 20),
                label.trailingAnchor.constraint(lessThanOrEqualTo: cover.trailingAnchor, constant: -20)
            ])
            view.addSubview(cover)
            privacyCover = cover
        } else if !hidden {
            privacyCover?.removeFromSuperview()
            privacyCover = nil
        }
    }
    deinit { NotificationCenter.default.removeObserver(self) }
}
