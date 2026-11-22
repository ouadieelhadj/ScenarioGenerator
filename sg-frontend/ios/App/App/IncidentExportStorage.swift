import Foundation

enum IncidentExportStorage {
    enum Failure: Error { case invalidPrivateRoot }
    static func root(in temporaryDirectory: URL) -> URL {
        temporaryDirectory.appendingPathComponent("merchant-incident-exports", isDirectory: true)
    }
    static func cleanupInterrupted(in temporaryDirectory: URL) throws {
        let manager = FileManager.default
        let directory = root(in: temporaryDirectory)
        guard manager.fileExists(atPath: directory.path) else { return }
        let kind = try directory.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
        guard kind.isDirectory == true, kind.isSymbolicLink != true else { throw Failure.invalidPrivateRoot }
        for entry in try manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isSymbolicLinkKey]) {
            guard UUID(uuidString: entry.lastPathComponent) != nil else { continue }
            // Remove only our UUID-named private exports. Removing a link removes
            // that link itself, never the destination or the user's selected copy.
            try manager.removeItem(at: entry)
        }
    }
}
