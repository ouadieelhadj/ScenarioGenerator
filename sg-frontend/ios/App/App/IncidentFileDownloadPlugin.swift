import Foundation
import UIKit
import Capacitor

// The server checks permission and CLEAN before providing the bytes. This bridge accepts
// no URL, credentials or destination path: only bounded bytes and a generated filename.
@objc(IncidentFileDownloadPlugin)
public final class IncidentFileDownloadPlugin: CAPPlugin, CAPBridgedPlugin, UIDocumentPickerDelegate {
    public let identifier = "IncidentFileDownloadPlugin"
    public let jsName = "IncidentFileDownload"
    public let pluginMethods: [CAPPluginMethod] = [CAPPluginMethod(name: "save", returnType: CAPPluginReturnPromise)]
    private var pending: CAPPluginCall?
    private var temporaryDirectory: URL?

    @objc public func save(_ call: CAPPluginCall) {
        guard let encoded = call.getString("base64"), let mime = call.getString("contentType"),
              let name = call.getString("filename"),
              let bytes = IncidentExportPolicy.decode(encoded, mime: mime, filename: name) else {
            call.options = [:]; call.reject("Fichier refusé.", "INCIDENT_COPY_INVALID"); return
        }
        call.options = [:]
        DispatchQueue.main.async { [weak self] in
            guard let self = self, MerchantOriginPolicy.isBundledPage(self.bridge?.webView?.url) else {
                call.reject("Origine non autorisée.", "INCIDENT_COPY_ORIGIN"); return
            }
            guard self.pending == nil else { call.reject("Une copie est déjà en cours.", "INCIDENT_COPY_BUSY"); return }
            guard let controller = self.bridge?.viewController, controller.presentedViewController == nil else {
                call.reject("Fermez la fenêtre précédente avant de copier.", "INCIDENT_COPY_PICKER_UNAVAILABLE"); return
            }
            do {
                let root = FileManager.default.temporaryDirectory.appendingPathComponent("merchant-incident-exports", isDirectory: true)
                try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true,
                    attributes: [.protectionKey: FileProtectionType.complete])
                // Clear only this application's private interrupted exports, never user-selected files.
                for item in try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) {
                    if UUID(uuidString: item.lastPathComponent) != nil { try FileManager.default.removeItem(at: item) }
                }
                var directory = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false,
                    attributes: [.protectionKey: FileProtectionType.complete])
                self.temporaryDirectory = directory
                var values = URLResourceValues(); values.isExcludedFromBackup = true
                try directory.setResourceValues(values)
                let source = directory.appendingPathComponent(name)
                try bytes.write(to: source, options: [.atomic, .completeFileProtection])
                let picker = UIDocumentPickerViewController(forExporting: [source], asCopy: true)
                picker.delegate = self; picker.allowsMultipleSelection = false
                self.pending = call
                controller.present(picker, animated: true)
            } catch {
                self.pending = call; self.finish(saved: false, error: "INCIDENT_COPY_PREPARE_FAILED")
            }
        }
    }
    public func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        // This callback confirms that the system export completed, not merely that a picker opened.
        finish(saved: urls.count == 1, error: urls.count == 1 ? nil : "INCIDENT_COPY_NOT_CONFIRMED")
    }
    public func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { finish(saved: false) }
    private func finish(saved: Bool, error: String? = nil) {
        let call = pending; pending = nil
        var failure = error
        if let directory = temporaryDirectory {
            do { try FileManager.default.removeItem(at: directory) }
            catch { failure = "INCIDENT_COPY_CLEANUP_FAILED" }
        }
        temporaryDirectory = nil
        if let failure = failure { call?.reject("Copie non confirmée. Vérifiez le fichier avant un nouvel essai.", failure) }
        else { call?.resolve(["saved": saved]) }
    }
}
