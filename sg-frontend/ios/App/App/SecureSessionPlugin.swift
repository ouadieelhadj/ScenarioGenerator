import Foundation
import Security
import Capacitor

// This service belongs only to this bundle. No synchronisation, shared access group or logs.
private enum MerchantSessionKeychain {
    static var service: String { (Bundle.main.bundleIdentifier ?? "com.moneycore.merchantportal.test") + ".session" }
    static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service, kSecAttrAccount as String: "access-token",
         kSecAttrSynchronizable as String: false]
    }
    static func require(_ status: OSStatus) throws {
        if status != errSecSuccess && status != errSecItemNotFound {
            throw NSError(domain: "FuturPayment.SecureSession", code: Int(status))
        }
    }
    static func clear() throws { try require(SecItemDelete(query as CFDictionary)) }
    static func prepareInstallation() throws {
        let directory = try FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true)
        var marker = directory.appendingPathComponent("merchant-session-installation")
        if !FileManager.default.fileExists(atPath: marker.path) {
            // Keychain items can survive uninstall. A fresh installation must require login.
            try clear()
            try Data([1]).write(to: marker, options: [.atomic, .completeFileProtection])
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try marker.setResourceValues(values)
        }
    }
}

@objc(SecureSessionPlugin)
public final class SecureSessionPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "SecureSessionPlugin"
    public let jsName = "SecureSession"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "set", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "get", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clear", returnType: CAPPluginReturnPromise)
    ]
    private let queue = DispatchQueue(label: "com.moneycore.merchant.session")

    private func execute(_ call: CAPPluginCall, _ operation: @escaping () throws -> [String: Any]) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, MerchantOriginPolicy.isBundledPage(self.bridge?.webView?.url) else {
                call.reject("Origine non autorisée.", "SESSION_ORIGIN"); return
            }
            self.queue.async {
                do {
                    try MerchantSessionKeychain.prepareInstallation()
                    let result = try operation()
                    #if targetEnvironment(simulator)
                    NSLog("FP_SESSION_DIAGNOSTIC operation=%@ code=0", call.methodName)
                    #endif
                    call.resolve(result)
                } catch {
                    #if targetEnvironment(simulator)
                    // Error code only: never log a token, query or protected value.
                    NSLog("FP_SESSION_DIAGNOSTIC operation=%@ code=%ld", call.methodName, (error as NSError).code)
                    #endif
                    call.reject("Session protégée indisponible. Reconnectez-vous.", "SESSION_KEYCHAIN")
                }
            }
        }
    }
    @objc public func set(_ call: CAPPluginCall) {
        guard let value = call.getString("value"), !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let data = value.data(using: .utf8), data.count <= 65_536 else {
            call.reject("Session invalide.", "SESSION_INVALID"); return
        }
        call.options = [:] // Never retain the bearer in a pending plugin call.
        execute(call) {
            let status = SecItemUpdate(MerchantSessionKeychain.query as CFDictionary,
                [kSecValueData as String: data] as CFDictionary)
            if status == errSecItemNotFound {
                var item = MerchantSessionKeychain.query
                item[kSecValueData as String] = data
                item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
                try MerchantSessionKeychain.require(SecItemAdd(item as CFDictionary, nil))
            } else { try MerchantSessionKeychain.require(status) }
            return [:]
        }
    }
    @objc public func get(_ call: CAPPluginCall) {
        execute(call) {
            var item = MerchantSessionKeychain.query
            item[kSecReturnData as String] = true
            item[kSecMatchLimit as String] = kSecMatchLimitOne
            var value: CFTypeRef?
            let status = SecItemCopyMatching(item as CFDictionary, &value)
            if status == errSecItemNotFound { return ["value": NSNull()] }
            try MerchantSessionKeychain.require(status)
            guard let data = value as? Data, let token = String(data: data, encoding: .utf8) else {
                try MerchantSessionKeychain.clear()
                throw NSError(domain: "FuturPayment.SecureSession", code: 1)
            }
            return ["value": token]
        }
    }
    @objc public func clear(_ call: CAPPluginCall) {
        execute(call) { try MerchantSessionKeychain.clear(); return [:] }
    }
}
