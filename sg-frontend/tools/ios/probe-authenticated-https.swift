import Foundation
import Security

// Separate diagnostic executable. No change to the app, ATS, DNS or server.
// Standard system trust is used: no authentication-challenge override exists.
let endpoint = URL(string: "https://merchant.futurpayment.com/api-merchant-portal/auth/login")!

func safeCodes(_ error: NSError?, depth: Int = 0) -> [[String: Any]] {
    guard let error = error, depth < 5 else { return [] }
    let allowed = [NSURLErrorDomain, "kCFErrorDomainCFNetwork", "kCFStreamErrorDomainSSL", NSOSStatusErrorDomain]
    var result: [[String: Any]] = [["domain": allowed.contains(error.domain) ? error.domain : "OTHER", "code": error.code]]
    for key in ["_kCFStreamErrorCodeKey", "_kCFNetworkCFStreamSSLErrorOriginalValue"] {
        if let value = error.userInfo[key] as? NSNumber { result.append(["domain": "SSL_NUMERIC", "code": value.intValue]) }
    }
    return result + safeCodes(error.userInfo[NSUnderlyingErrorKey] as? NSError, depth: depth + 1)
}

final class ResultBox: @unchecked Sendable {
    private let lock = NSLock()
    private var result: [String: Any] = [:]
    func set(_ value: [String: Any]) { lock.lock(); defer { lock.unlock() }; result = value }
    func read() -> [String: Any] { lock.lock(); defer { lock.unlock() }; return result }
}

final class MetricsDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var transactions: [Int: [[String: Any]]] = [:]
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil) // Never forward credentials through a redirect.
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didFinishCollecting metrics: URLSessionTaskMetrics) {
        let rows = metrics.transactionMetrics.map { metric -> [String: Any] in
            func elapsed(_ start: Date?, _ end: Date?) -> Any {
                guard let start = start, let end = end else { return NSNull() }
                return Int(end.timeIntervalSince(start) * 1000)
            }
            return ["httpProtocol": metric.networkProtocolName ?? "UNKNOWN", "reusedConnection": metric.isReusedConnection,
                    "tlsProtocol": metric.negotiatedTLSProtocolVersion.map { Int($0.rawValue) } ?? 0,
                    "tlsCipher": metric.negotiatedTLSCipherSuite.map { Int($0.rawValue) } ?? 0,
                    "dnsMs": elapsed(metric.domainLookupStartDate, metric.domainLookupEndDate),
                    "connectMs": elapsed(metric.connectStartDate, metric.connectEndDate),
                    "tlsMs": elapsed(metric.secureConnectionStartDate, metric.secureConnectionEndDate),
                    "responseMs": elapsed(metric.requestStartDate, metric.responseStartDate)]
        }
        lock.lock(); defer { lock.unlock() }; transactions[task.taskIdentifier] = rows
    }
    func read(_ id: Int) -> [[String: Any]] { lock.lock(); defer { lock.unlock() }; return transactions[id] ?? [] }
}

func observe(_ session: URLSession, delegate: MetricsDelegate, method: String, body: Data?) -> [String: Any] {
    var request = URLRequest(url: endpoint)
    request.httpMethod = method
    request.httpBody = body
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    request.setValue("no-store", forHTTPHeaderField: "Cache-Control")
    if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
    let gate = DispatchSemaphore(value: 0), box = ResultBox(), started = Date()
    let task = session.dataTask(with: request) { data, response, error in
        let http = (response as? HTTPURLResponse)?.statusCode ?? 0
        var authenticated = false
        if method == "POST", http == 200, error == nil, response?.url == endpoint, let data = data,
           let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            authenticated = (value["token"] as? String)?.isEmpty == false
                && value["login"] as? String == "bim.amina" && value["role"] is String
        }
        // Only numeric diagnostics and booleans survive; discard the whole response.
        box.set(["method": method, "http": http, "errors": safeCodes(error as NSError?),
                 "milliseconds": Int(Date().timeIntervalSince(started) * 1000),
                 "exactEndpoint": response?.url == endpoint, "authenticated": authenticated,
                 "secureResponseReceived": response is HTTPURLResponse && error == nil])
        gate.signal()
    }
    task.resume()
    let completed = gate.wait(timeout: .now() + 38) != .timedOut
    if !completed { task.cancel() }
    var row = completed ? box.read() : ["method": method, "probeDeadlineExceeded": true, "authenticated": false]
    row["transactions"] = delegate.read(task.taskIdentifier)
    return row
}

let input = FileHandle.standardInput.readDataToEndOfFile()
guard let credentials = try? JSONSerialization.jsonObject(with: input) as? [String: String],
      credentials["login"] == "bim.amina", let password = credentials["password"], !password.isEmpty else {
    fputs("DIAGNOSTIC_INPUT_MISSING\n", stderr); exit(2)
}
let body = try JSONSerialization.data(withJSONObject: ["login": "bim.amina", "password": password])
var observations: [[String: Any]] = []
// Exactly four valid POSTs per platform. No retry or password guessing.
for variant in ["dedicated-default", "ephemeral", "default-prewarmed", "diagnostic-tls12"] {
    let configuration = variant == "ephemeral" ? URLSessionConfiguration.ephemeral : URLSessionConfiguration.default
    configuration.urlCache = nil
    configuration.httpCookieStorage = nil
    configuration.urlCredentialStorage = nil
    configuration.timeoutIntervalForRequest = 25
    configuration.timeoutIntervalForResource = 32
    if variant == "diagnostic-tls12" { configuration.tlsMaximumSupportedProtocolVersion = .TLSv12 }
    let delegate = MetricsDelegate()
    let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
    var row: [String: Any] = ["variant": variant, "sample": 1]
    if variant == "default-prewarmed" { row["anonymousPrewarm"] = observe(session, delegate: delegate, method: "GET", body: nil) }
    row["login"] = observe(session, delegate: delegate, method: "POST", body: body)
    observations.append(row)
    session.invalidateAndCancel()
}
#if targetEnvironment(simulator)
let simulator = true
#else
let simulator = false
#endif
let report: [String: Any] = ["nativeMacURLSession": !simulator, "nativeIPhoneSimulatorURLSession": simulator,
    "physicalIPhoneTested": false, "credentialsUsedInMemory": true, "postRequests": 4,
    "productOrServerSettingsChanged": false, "systemTrustUnchanged": true, "redirectsAllowed": false,
    "responseBodiesOrTokensRetained": false, "cliContextIsProductApp": false,
    "tls12VariantIsIsolatedDiagnosticOnly": true, "observations": observations]
FileHandle.standardOutput.write(try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]))
