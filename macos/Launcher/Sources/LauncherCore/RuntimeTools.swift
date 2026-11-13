// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// The `python3 -m shiraz` helper commands the launcher runs, and the import
/// pipeline built on them: copy, inspect, icon, label, package metadata.
public struct RuntimeTools: Sendable {
    public var runtime: RuntimeLocation

    public init(runtime: RuntimeLocation) { self.runtime = runtime }

    /// Run one command and return its standard output, or throw its message.
    public func output(_ arguments: [String], what: String, timeout: TimeInterval = 60) async throws -> Data {
        let result = try await ProcessRunner.run(
            executable: runtime.python,
            arguments: runtime.arguments(arguments),
            environment: runtime.environment,
            workingDirectory: runtime.root,
            timeout: timeout)
        guard result.status.succeeded else {
            let message = result.stderr.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "shiraz: ", with: "")
            throw LibraryError.inspectFailed(message.isEmpty ? "\(what) \(result.status)." : message)
        }
        return result.stdout
    }

    public func inspect(apk: URL) async throws -> InspectReport {
        try InspectReport.decode(try await output(RuntimeCommand.inspect(apk: apk), what: "Inspection"))
    }

    public func extractIcon(apk: URL, output directory: URL) async throws {
        _ = try await output(RuntimeCommand.icon(apk: apk, outputDirectory: directory), what: "Icon extraction")
    }

    public func label(apk: URL) async throws -> String {
        struct Output: Decodable { var label: String }
        return try JSONDecoder().decode(Output.self, from: try await output(RuntimeCommand.label(apk: apk), what: "Label lookup")).label
    }

    /// Signing certificate digests of an APK the caller already trusts.
    public func signers(apk: URL) async throws -> [String] {
        struct Output: Decodable { var certificates: [String] }
        return try JSONDecoder().decode(Output.self, from: try await output(RuntimeCommand.signer(apk: apk), what: "Signer lookup")).certificates
    }

    /// package.properties lets apps running in Shiraz see this one through
    /// PackageManager. A failure only hides the app from them.
    public func writeMetadata(for package: String, installer: String?, store: LibraryStore) async {
        do {
            let data = try await output(RuntimeCommand.metadata(apk: store.apkURL(for: package), installer: installer),
                                        what: "Package metadata")
            try store.saveMetadata(data, for: package)
        } catch {
            try? FileManager.default.removeItem(at: store.metadataURL(for: package))
        }
    }

    /// Add one APK to the library. `expected` rejects an APK whose manifest
    /// names another package; `replacing: false` rejects one already there.
    /// `check` sees the inspected copy before it replaces anything and may throw.
    public func add(
        apk: URL, to store: LibraryStore, expected: String? = nil, installer: String? = nil, replacing: Bool = true,
        check: (@Sendable (InspectReport) async throws -> Void)? = nil
    ) async throws -> AppEntry {
        let entry = try await store.importAPK(from: apk, replacing: replacing) { staged in
            let report = try await inspect(apk: staged)
            if let expected, report.manifest.package != expected {
                throw LibraryError.inspectFailed("The APK is \(report.manifest.package), not the requested \(expected).")
            }
            try await check?(report)
            return report
        }
        await store.extractIcon(for: entry.packageName) { apk, output in
            try await extractIcon(apk: apk, output: output)
        }
        var labelled = entry
        labelled.label = try? await label(apk: store.apkURL(for: entry.packageName))
        try? store.save(labelled)
        await writeMetadata(for: entry.packageName, installer: installer, store: store)
        return labelled
    }
}
