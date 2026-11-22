import Foundation

enum IncidentExportPolicy {
    static let maxBytes = 10_000_000
    static let maxBase64 = 4 * ((maxBytes + 2) / 3)

    static func decode(_ encoded: String, mime: String, filename: String) -> Data? {
        let ext: String
        switch mime {
        case "application/pdf": ext = "pdf"
        case "image/jpeg": ext = "jpg"
        case "image/png": ext = "png"
        default: return nil
        }
        let pattern = "^incident-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\." + ext + "$"
        guard let matched = filename.range(of: pattern, options: .regularExpression),
              matched == filename.startIndex..<filename.endIndex,
              !encoded.isEmpty, encoded.utf8.count <= maxBase64,
              let data = Data(base64Encoded: encoded), !data.isEmpty,
              data.count <= maxBytes, data.base64EncodedString() == encoded else { return nil }
        let header: [UInt8]
        switch mime {
        case "application/pdf": header = [0x25, 0x50, 0x44, 0x46, 0x2d]
        case "image/jpeg": header = [0xff, 0xd8, 0xff]
        default: header = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
        }
        return data.starts(with: header) ? data : nil
    }
}
