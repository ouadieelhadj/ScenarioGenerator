import Foundation
import UIKit
import Capacitor
#if targetEnvironment(simulator)
import CryptoKit
#endif

// The server checks permission and CLEAN before providing the bytes. This bridge accepts
// no URL, credentials or destination path: only bounded bytes and a generated filename.
@objc(IncidentFileDownloadPlugin)
public final class IncidentFileDownloadPlugin: CAPPlugin, CAPBridgedPlugin, UIDocumentPickerDelegate {
    public let identifier = "IncidentFileDownloadPlugin"
    public let jsName = "IncidentFileDownload"
    public let pluginMethods: [CAPPluginMethod] = [CAPPluginMethod(name: "save", returnType: CAPPluginReturnPromise)]
    private var pending: CAPPluginCall?
    private var temporaryDirectory: URL?

    public override func load() {
        do { try IncidentExportStorage.cleanupInterrupted(in: FileManager.default.temporaryDirectory) }
        catch {
            // A locked/unavailable store may be retried at the next explicit save.
            // Never log a filename, bytes, directory or user identity.
            #if targetEnvironment(simulator)
            NSLog("FP_EXPORT_DIAGNOSTIC cleanup=startup_failed")
            #endif
        }
    }

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
                let root = IncidentExportStorage.root(in: FileManager.default.temporaryDirectory)
                try IncidentExportStorage.cleanupInterrupted(in: FileManager.default.temporaryDirectory)
                try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true,
                    attributes: [.protectionKey: FileProtectionType.complete])
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
        #if targetEnvironment(simulator)
        if let destination = urls.first, urls.count == 1 { diagnoseSavedCopy(destination) }
        #endif
        finish(saved: urls.count == 1, error: urls.count == 1 ? nil : "INCIDENT_COPY_NOT_CONFIRMED")
    }
    #if targetEnvironment(simulator)
    private func diagnoseSavedCopy(_ destination: URL) {
        // Read only the URL explicitly returned by the export picker. No path,
        // filename, recipient, account or bytes are logged; never compiled on a phone.
        let scoped = destination.startAccessingSecurityScopedResource()
        defer { if scoped { destination.stopAccessingSecurityScopedResource() } }
        var error: NSError?
        NSFileCoordinator(filePresenter: nil).coordinate(readingItemAt: destination, options: [], error: &error) { readable in
            do {
                let root = IncidentExportStorage.root(in: FileManager.default.temporaryDirectory).resolvingSymlinksInPath().standardizedFileURL.path
                let path = readable.resolvingSymlinksInPath().standardizedFileURL.path
                let external = path != root && !path.hasPrefix(root + "/")
                let size = try readable.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard external, size > 0, size <= IncidentExportPolicy.maxBytes else {
                    NSLog("FP_EXPORT_SAVED verified=false"); return
                }
                let data = try Data(contentsOf: readable, options: [.mappedIfSafe])
                guard data.count == size else { NSLog("FP_EXPORT_SAVED verified=false"); return }
                let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
                NSLog("FP_EXPORT_SAVED verified=true external=true bytes=%ld sha256=%@", data.count, digest)
            } catch { NSLog("FP_EXPORT_SAVED verified=false") }
        }
        if error != nil { NSLog("FP_EXPORT_SAVED verified=false") }
    }
    #endif
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
