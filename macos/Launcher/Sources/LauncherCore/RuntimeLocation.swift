// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// Where the Shiraz runtime lives. Until the runtime ships as an embedded
/// helper inside the app bundle, the launcher drives a source checkout
/// through its `python3 -m shiraz` command line.
public struct RuntimeLocation: Equatable, Sendable {
    public var root: URL
    public var python: URL

    public init(root: URL, python: URL) {
        self.root = root
        self.python = python
    }

    public struct Invalid: Error, LocalizedError, Sendable {
        public var message: String
        public var errorDescription: String? { message }
    }

    /// Throws when `root` does not look like a Shiraz checkout.
    public static func validate(root: URL, fileManager: FileManager = .default) throws {
        var isDirectory: ObjCBool = false
        guard fileManager.fileExists(atPath: root.path, isDirectory: &isDirectory), isDirectory.boolValue else {
            throw Invalid(message: "\(root.path) is not a folder.")
        }
        for marker in ["VERSION", "src/shiraz/__main__.py"]
        where !fileManager.fileExists(atPath: root.appending(path: marker).path) {
            throw Invalid(message: "\(root.path) is not a Shiraz checkout (missing \(marker)).")
        }
    }

    public static func version(root: URL) -> String? {
        (try? String(contentsOf: root.appending(path: "VERSION"), encoding: .utf8))?
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Environment variable first, then walk up from the app or current
    /// directory. This finds the checkout for `swift run` and for a bundle
    /// built under the checkout's `build/` directory.
    public static func detectRoot(
        environment: [String: String] = ProcessInfo.processInfo.environment,
        executable: URL? = Bundle.main.executableURL,
        currentDirectory: URL = URL(fileURLWithPath: FileManager.default.currentDirectoryPath),
        fileManager: FileManager = .default
    ) -> URL? {
        var candidates: [URL] = []
        if let path = environment["SHIRAZ_ROOT"], !path.isEmpty {
            candidates.append(URL(fileURLWithPath: path))
        }
        for start in [executable?.deletingLastPathComponent(), currentDirectory].compactMap({ $0 }) {
            var directory = start.standardizedFileURL
            for _ in 0..<12 {
                candidates.append(directory)
                let parent = directory.deletingLastPathComponent()
                if parent == directory { break }
                directory = parent
            }
        }
        return candidates.first { (try? validate(root: $0, fileManager: fileManager)) != nil }
    }

    /// A GUI app does not inherit the shell's PATH, so check fixed locations.
    public static func detectPython(
        environment: [String: String] = ProcessInfo.processInfo.environment,
        fileManager: FileManager = .default
    ) -> URL? {
        var paths = ["/opt/homebrew/bin/python3", "/usr/local/bin/python3", "/usr/bin/python3"]
        if let override = environment["SHIRAZ_PYTHON"], !override.isEmpty { paths.insert(override, at: 0) }
        return paths.first { fileManager.isExecutableFile(atPath: $0) }.map { URL(fileURLWithPath: $0) }
    }

    public var environment: [String: String] {
        var env = ProcessInfo.processInfo.environment
        env["PYTHONPATH"] = root.appending(path: "src").path
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        return env
    }

    public func arguments(_ shirazArguments: [String]) -> [String] {
        ["-m", "shiraz"] + shirazArguments
    }
}
