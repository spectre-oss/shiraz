// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// Writes plain text to the general pasteboard through `/usr/bin/pbcopy`.
/// The launcher's UI target is SwiftUI only and SwiftUI has no call that
/// fills the pasteboard from a button, so this stays on Foundation.
/// `pbcopy` reads its input in the locale's encoding; an app started from
/// Finder has no `LANG`, so the child always gets a UTF-8 locale.
public struct Clipboard: Sendable {
    public var executable: URL
    public var arguments: [String]

    public init(executable: URL = URL(filePath: "/usr/bin/pbcopy"), arguments: [String] = []) {
        self.executable = executable
        self.arguments = arguments
    }

    public struct Failure: LocalizedError, Equatable, Sendable {
        public var status: Int32
        public var errorDescription: String? { "Copying to the clipboard failed (pbcopy exited with \(status))." }
    }

    /// Runs off the caller's actor: a full log is up to a few megabytes.
    @concurrent
    public func copy(_ text: String) async throws {
        let process = Process()
        process.executableURL = executable
        process.arguments = arguments
        var environment = ProcessInfo.processInfo.environment
        environment["LANG"] = "en_US.UTF-8"
        environment["LC_ALL"] = "en_US.UTF-8"
        process.environment = environment
        let input = Pipe()
        process.standardInput = input
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        do {
            try input.fileHandleForWriting.write(contentsOf: Data(text.utf8))
            try input.fileHandleForWriting.close()
        } catch {
            process.terminate()
            process.waitUntilExit()
            throw error
        }
        process.waitUntilExit()
        guard process.terminationReason == .exit, process.terminationStatus == 0 else {
            throw Failure(status: process.terminationStatus)
        }
    }
}
