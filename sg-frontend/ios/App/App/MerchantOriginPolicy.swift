import Foundation

enum MerchantOriginPolicy {
    static func isBundledPage(_ url: URL?) -> Bool {
        guard let url = url else { return false }
        return url.scheme == "capacitor" && url.host == "localhost" &&
            url.port == nil && url.user == nil && url.password == nil
    }
}
