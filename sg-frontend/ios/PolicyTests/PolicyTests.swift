import Foundation
import XCTest
@testable import MerchantNativePolicies

final class PolicyTests: XCTestCase {
    private let identifier = "00000000-0000-4000-8000-000000000001"
    func testOnlyBundledPageCanCallNativePlugins() {
        XCTAssertTrue(MerchantOriginPolicy.isBundledPage(URL(string: "capacitor://localhost/incidents")))
        for value in ["https://merchant.futurpayment.com", "capacitor://elsewhere/incidents", "capacitor://localhost:443/incidents", "capacitor://user@localhost/incidents", "file:///tmp/index.html"] {
            XCTAssertFalse(MerchantOriginPolicy.isBundledPage(URL(string: value)), value)
        }
        XCTAssertFalse(MerchantOriginPolicy.isBundledPage(nil))
    }
    func testMimeNameAndSignatureMustAgree() {
        let encoded = Data("%PDF-1.4\nSynthetic fixture only".utf8).base64EncodedString()
        let name = "incident-" + identifier + ".pdf"
        XCTAssertNotNil(IncidentExportPolicy.decode(encoded, mime: "application/pdf", filename: name))
        XCTAssertNil(IncidentExportPolicy.decode(encoded, mime: "image/jpeg", filename: "incident-" + identifier + ".jpg"))
        XCTAssertNil(IncidentExportPolicy.decode(encoded, mime: "text/html", filename: name))
        for unsafe in ["../" + name, "/tmp/" + name, "incident-" + identifier + ".pdf.html", "arbitrary.pdf", name + "\n", name + "\r\n", name + "\0", name + "/child"] {
            XCTAssertNil(IncidentExportPolicy.decode(encoded, mime: "application/pdf", filename: unsafe))
        }
    }
    func testMalformedAndOversizedPayloadsFailClosed() {
        let name = "incident-" + identifier + ".pdf"
        for value in ["", "!", "JVBERi0=\n", "JVBERi0==="] {
            XCTAssertNil(IncidentExportPolicy.decode(value, mime: "application/pdf", filename: name))
        }
        let large = Data(repeating: 0x41, count: IncidentExportPolicy.maxBytes + 1).base64EncodedString()
        XCTAssertNil(IncidentExportPolicy.decode(large, mime: "application/pdf", filename: name))
    }
    func testImageSignaturesAcceptedOnlyWithTheirExtension() {
        let png = Data([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]).base64EncodedString()
        let jpeg = Data([0xff,0xd8,0xff,0xe0]).base64EncodedString()
        XCTAssertNotNil(IncidentExportPolicy.decode(png, mime: "image/png", filename: "incident-" + identifier + ".png"))
        XCTAssertNotNil(IncidentExportPolicy.decode(jpeg, mime: "image/jpeg", filename: "incident-" + identifier + ".jpg"))
        XCTAssertNil(IncidentExportPolicy.decode(png, mime: "image/jpeg", filename: "incident-" + identifier + ".jpg"))
    }
}
