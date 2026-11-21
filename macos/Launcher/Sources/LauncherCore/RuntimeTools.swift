// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// The `python3 -m shiraz` helper commands the launcher runs, and the import
/// pipeline built on them: copy, inspect, icon, label, package metadata.
public struct RuntimeTools: Sendable {
    private struct Enrollment: Decodable {
        let package: String
        let uid: Int
        let version: Int64
        let apk_sha256: String
    }
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

    /// Keep library ownership through enrollment and the host's exact-digest
    /// launch acknowledgment. Failed or ambiguous starts are never retried here.
    public func launchRegistered(package: String, store: LibraryStore, host: SharedHostClient, requester: HostLaunchRequester? = nil, embed: EmbedPaths? = nil) async throws -> HostReply {
        guard host.registry.path == store.registryDirectory.resolvingSymlinksInPath().standardizedFileURL.path else {
            throw LibraryError.inspectFailed("Shared host belongs to a different app library.")
        }
        return try await store.withPackageSnapshot(package) { entry in
            if let problem = entry.settings.validationError { throw LibraryError.inspectFailed(problem) }
            let result = try await output(["register-package", store.apkURL(for: package).path,
                                           "--registry", store.registryDirectory.path, "--expected-package", package,
                                           "--expected-sha256", entry.sha256], what: "Package registration", timeout: 150)
            let enrollment = try JSONDecoder().decode(Enrollment.self, from: result)
            guard enrollment.package == package, enrollment.apk_sha256 == entry.sha256,
                  (10000 ... 19999).contains(enrollment.uid), enrollment.version >= 0 else {
                throw LibraryError.inspectFailed("Invalid registered package identity.")
            }
            return try await host.start(package: package, sha256: entry.sha256, settings: entry.settings, requester: requester, embed: embed)
        }
    }

    /// Remove library files only after the current registered identity has been
    /// retired. Forward every reply so completed launches are not lost.
    public func removeRegistered(package: String, store: LibraryStore, host: SharedHostClient,
                                 onReply: @Sendable (HostReply) async -> Void) async throws {
        guard host.registry.path == store.registryDirectory.resolvingSymlinksInPath().standardizedFileURL.path else {
            throw LibraryError.inspectFailed("Shared host belongs to a different app library.")
        }
        try await store.remove(package, after: { _ in
            let lookup = try await host.identity(package: package)
            await onReply(lookup)
            guard lookup.ok == true, lookup.found != nil else {
                throw LibraryError.inspectFailed(lookup.error ?? "Cannot read registered package identity.")
            }
            if let identity = lookup.identity {
                let reply = try await host.retire(package: package, sha256: identity.sha256, uid: identity.uid)
                await onReply(reply)
                guard reply.ok == true, reply.retired != nil else {
                    throw LibraryError.inspectFailed(reply.error ?? "Cannot retire the registered package.")
                }
            }
            let status = try await host.status()
            await onReply(status)
            guard status.ok == true, let running = status.running, !running.contains(package) else {
                throw LibraryError.inspectFailed(status.error ?? "The package is still running.")
            }
        })
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

    private struct VerifiedIdentity: Decodable {
        let apk_sha256: String
        let verified_signers_sha256: [String]
    }

    private func verifiedIdentity(apk: URL) async throws -> VerifiedIdentity {
        let identity = try JSONDecoder().decode(VerifiedIdentity.self, from:
            try await output(["verify-signature", apk.path], what: "APK signature verification", timeout: 150))
        func digest(_ value: String) -> Bool {
            value.utf8.count == 64 && value.utf8.allSatisfy { (48 ... 57).contains($0) || (97 ... 102).contains($0) }
        }
        guard digest(identity.apk_sha256), (1 ... 16).contains(identity.verified_signers_sha256.count),
              identity.verified_signers_sha256.allSatisfy(digest),
              Set(identity.verified_signers_sha256).count == identity.verified_signers_sha256.count else {
            throw LibraryError.inspectFailed("Invalid APK verification result.")
        }
        return identity
    }

    /// package.properties lets apps running in Shiraz see this one through
    /// PackageManager. A failure only hides the app from them.
    public func writeMetadata(for package: String, installer: String?, store: LibraryStore) async {
        try? await store.withInstalledPackage(package) {
            await writeMetadataDuringMutation(for: package, installer: installer, store: store)
        }
    }

    /// Recheck missing fields under ownership: a queued refresh may outlive an update.
    public func refreshMissingMetadata(for package: String, store: LibraryStore) async {
        try? await store.withInstalledPackage(package) {
            guard !store.hasCurrentMetadata(for: package) else { return }
            let installer = store.metadata(for: package)?["installer"].flatMap {
                LibraryStore.isValidPackageName($0) ? $0 : nil
            }
            await writeMetadataDuringMutation(for: package, installer: installer, store: store)
        }
    }

    /// False means ownership was unavailable; the caller may try on a later refresh.
    public func refreshMissingLabel(for package: String, store: LibraryStore) async -> Bool {
        do {
            try await store.withInstalledPackage(package) {
                guard var entry = store.load().first(where: { $0.packageName == package }), entry.label == nil,
                      let value = try? await label(apk: store.apkURL(for: package)) else { return }
                entry.label = value
                try? store.save(entry)
            }
            return true
        } catch { return false }
    }

    private func writeMetadataDuringMutation(for package: String, installer: String?, store: LibraryStore) async {
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
        host: SharedHostClient? = nil,
        onReply: @escaping @Sendable (HostReply) async -> Void = { _ in },
        onQuiesce: @escaping @Sendable (String) async -> Void = { _ in },
        check: (@Sendable (InspectReport) async throws -> Void)? = nil
    ) async throws -> AppEntry {
        if let host, host.registry.path != store.registryDirectory.resolvingSymlinksInPath().standardizedFileURL.path {
            throw LibraryError.inspectFailed("Shared host belongs to a different app library.")
        }
        return try await store.importAPK(from: apk, replacing: replacing, enrich: { entry in
            if let host {
                let lookup = try await host.identity(package: entry.packageName)
                await onReply(lookup)
                guard lookup.ok == true, let identity = lookup.identity, identity.sha256 == entry.sha256 else {
                    throw LibraryError.inspectFailed("APK published, but its runtime identity could not be confirmed. Import it again to recover.")
                }
                if lookup.quiesced == true {
                    let resumed = try await host.resume(package: entry.packageName, sha256: identity.sha256, uid: identity.uid)
                    await onReply(resumed)
                    guard resumed.ok == true else {
                        throw LibraryError.inspectFailed("APK published, but runtime resume failed: " + (resumed.error ?? "Unknown response"))
                    }
                }
            }
            await store.extractIconDuringMutation(for: entry.packageName) { apk, output in
                try await extractIcon(apk: apk, output: output)
            }
            var labelled = entry
            labelled.label = try? await label(apk: store.apkURL(for: entry.packageName))
            try? store.save(labelled)
            await writeMetadataDuringMutation(for: entry.packageName, installer: installer, store: store)
            return labelled
        }) { staged in
            let report = try await inspect(apk: staged)
            if let expected, report.manifest.package != expected {
                throw LibraryError.inspectFailed("The APK is \(report.manifest.package), not the requested \(expected).")
            }
            guard LibraryStore.isValidPackageName(report.manifest.package) else {
                throw LibraryError.invalidPackageName(report.manifest.package)
            }
            let verified = try await verifiedIdentity(apk: staged)
            guard verified.apk_sha256 == report.apkSha256 else {
                throw LibraryError.inspectFailed("APK changed between inspection and verification.")
            }
            let version = try report.manifest.longVersionCode()
            let installed = store.apkURL(for: report.manifest.package)
            if FileManager.default.fileExists(atPath: installed.path) {
                guard replacing else { throw LibraryError.alreadyInLibrary(report.manifest.package) }
                let previous = try await verifiedIdentity(apk: installed)
                guard Set(previous.verified_signers_sha256) == Set(verified.verified_signers_sha256) else {
                    throw LibraryError.inspectFailed("APK signer changed. Signing-key rotation is not supported.")
                }
                let previousReport = try await inspect(apk: installed)
                guard previousReport.apkSha256 == previous.apk_sha256,
                      previousReport.manifest.package == report.manifest.package else {
                    throw LibraryError.inspectFailed("Installed APK identity changed during verification.")
                }
                let previousVersion = try previousReport.manifest.longVersionCode()
                guard version >= previousVersion else {
                    throw LibraryError.inspectFailed("APK downgrade rejected.")
                }
                guard version != previousVersion || verified.apk_sha256 == previous.apk_sha256 else {
                    throw LibraryError.inspectFailed("Changed APK requires a higher version code.")
                }
            }
            try await check?(report)
            if let host {
                let lookup = try await host.identity(package: report.manifest.package)
                await onReply(lookup)
                guard lookup.ok == true, lookup.found != nil else {
                    throw LibraryError.inspectFailed(lookup.error ?? "Cannot read registered package identity.")
                }
                if let identity = lookup.identity, identity.sha256 != report.apkSha256 {
                    await onQuiesce(identity.package)
                    let stopped = try await host.quiesce(package: identity.package, sha256: identity.sha256, uid: identity.uid)
                    await onReply(stopped)
                    guard stopped.ok == true, stopped.quiesced == true else {
                        throw LibraryError.inspectFailed(stopped.error ?? "Cannot stop the previous package generation.")
                    }
                }
            }
            // Enroll the frozen staged bytes before publishing to the library.
            // The registry's generation lease rejects replacement while any
            // registered app or retained background service owns the old APK.
            // Do not retire here: updates must preserve the package UID.
            let result = try await output(["register-package", staged.path,
                                           "--registry", store.registryDirectory.path,
                                           "--expected-package", report.manifest.package,
                                           "--expected-sha256", report.apkSha256], what: "Package registration", timeout: 150)
            let enrollment = try JSONDecoder().decode(Enrollment.self, from: result)
            guard enrollment.package == report.manifest.package,
                  enrollment.apk_sha256 == report.apkSha256,
                  (10000 ... 19999).contains(enrollment.uid), enrollment.version == version else {
                throw LibraryError.inspectFailed("Invalid registered package identity.")
            }
            return report
        }
    }
}
