import Foundation

// Fixed public endpoint; no credentials, payload, trust override or retry.
let endpoint = URL(string: "https://merchant.futurpayment.com/api-merchant-portal/auth/login")!
func codes(_ error: NSError?, depth: Int = 0) -> [[String: Any]] {
    guard let error = error, depth < 5 else { return [] }
    let allowed = [NSURLErrorDomain, "kCFErrorDomainCFNetwork", "kCFStreamErrorDomainSSL", NSOSStatusErrorDomain]
    var result: [[String: Any]] = [["domain": allowed.contains(error.domain) ? error.domain : "OTHER", "code": error.code]]
    for key in ["_kCFStreamErrorCodeKey", "_kCFNetworkCFStreamSSLErrorOriginalValue"] {
        if let value = error.userInfo[key] as? NSNumber { result.append(["domain": "SSL_NUMERIC", "code": value.intValue]) }
    }
    result += codes(error.userInfo[NSUnderlyingErrorKey] as? NSError, depth: depth + 1)
    return result
}
final class Observation: @unchecked Sendable {
    private let lock = NSLock()
    private var value: [String: Any] = [:]
    func set(_ row: [String: Any]) { lock.lock(); defer { lock.unlock() }; value = row }
    func read() -> [String: Any] { lock.lock(); defer { lock.unlock() }; return value }
}
var rows: [[String: Any]] = []
for sample in 1...4 {
    // Four independent observations, never a retry of a login or an action.
    let config = URLSessionConfiguration.ephemeral
    config.timeoutIntervalForRequest = 25
    config.timeoutIntervalForResource = 30
    let session = URLSession(configuration: config)
    let semaphore = DispatchSemaphore(value: 0)
    let started = Date()
    var request = URLRequest(url: endpoint)
    request.httpMethod = "GET"
    request.setValue("FuturPayment-Apple-HTTPS-Diagnostic/1", forHTTPHeaderField: "User-Agent")
    let result = Observation()
    session.dataTask(with: request) { _, response, error in
        result.set(["sample": sample, "seconds": Int(Date().timeIntervalSince(started)),
            "http": (response as? HTTPURLResponse)?.statusCode ?? 0,
            "errors": codes(error as NSError?),
            "secureResponseReceived": response is HTTPURLResponse && error == nil])
        semaphore.signal()
    }.resume()
    let completed = semaphore.wait(timeout: .now() + 35) != .timedOut
    session.invalidateAndCancel()
    rows.append(completed ? result.read() : ["sample": sample, "probeDeadlineExceeded": true, "secureResponseReceived": false])
}
#if targetEnvironment(simulator)
let simulator = true
#else
let simulator = false
#endif
let report: [String: Any] = ["nativeMacURLSession": !simulator, "nativeIPhoneSimulatorURLSession": simulator, "nativeIPhoneTested": false,
    "credentialsUsed": false, "requestBodiesSent": false, "trustOrTLSChanged": false,
    "observations": rows]
let output = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
FileHandle.standardOutput.write(output)
