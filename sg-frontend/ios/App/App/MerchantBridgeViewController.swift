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
                     UIScreen.capturedDidChangeNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(privacyChanged(_:)), name: name, object: nil)
        }
    }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); updatePrivacy() }
    @objc private func privacyChanged(_ notification: Notification) {
        updatePrivacy(forceHidden: notification.name == UIApplication.willResignActiveNotification)
    }
    private func updatePrivacy(forceHidden: Bool = false) {
        let hidden = forceHidden || UIApplication.shared.applicationState != .active || (view.window?.screen.isCaptured ?? false)
        if hidden && privacyCover == nil {
            let cover = UIView(frame: view.bounds); cover.backgroundColor = .systemBackground
            cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            let label = UILabel(frame: cover.bounds); label.text = "FuturPayment — affichage masqué"
            label.textAlignment = .center; label.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            cover.addSubview(label); view.addSubview(cover); privacyCover = cover
        } else if !hidden { privacyCover?.removeFromSuperview(); privacyCover = nil }
    }
    deinit { NotificationCenter.default.removeObserver(self) }
}
