// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import Foundation
import LauncherCore
import Observation

/// One launch of one app: its log and lifecycle.
@MainActor @Observable
final class Session: Identifiable {
    enum State: Equatable {
        case running(pid: Int32)
        case finished(String)
        case failed(String)
    }

    let id = UUID()
    let package: String
    let startedAt = Date()
    var state: State
    var log = LogBuffer()
    fileprivate var process: RunningProcess?
    fileprivate var stopRequested = false

    init(package: String, state: State) {
        self.package = package
        self.state = state
    }

    var isRunning: Bool {
        if case .running = state { return true }
        return false
    }
}

@MainActor @Observable
final class LauncherModel {
    private(set) var apps: [AppEntry] = []
    private(set) var sessions: [String: Session] = [:]
    private(set) var isImporting = false
    /// Rendered APK icons by package; absent means the monogram is shown.
    private(set) var icons: [String: CGImage] = [:]
    var selection: String?
    var alert: String?
    var showImporter = false

    /// Optional user override of the Shiraz checkout, set in Settings.
    var runtimeRootOverride: String {
        didSet { UserDefaults.standard.set(runtimeRootOverride, forKey: "runtimeRoot") }
    }

    private let store: LibraryStore

    init(store: LibraryStore = LibraryStore(directory: LibraryStore.defaultDirectory)) {
        self.store = store
        runtimeRootOverride = UserDefaults.standard.string(forKey: "runtimeRoot") ?? ""
        apps = store.load()
        selection = apps.first?.id
        reloadIcons()
        extractMissingIcons()
        // Runtimes live in their own process groups, so quitting the launcher
        // must stop them. The notification is observed by name to avoid AppKit.
        NotificationCenter.default.addObserver(
            forName: Notification.Name("NSApplicationWillTerminateNotification"),
            object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.stopAll() }
        }
    }

    // MARK: Runtime

    enum RuntimeStatus {
        case ready(RuntimeLocation, version: String?)
        case unavailable(String)

        var location: RuntimeLocation? {
            if case .ready(let location, _) = self { location } else { nil }
        }
    }

    var runtimeStatus: RuntimeStatus {
        let override = runtimeRootOverride.trimmingCharacters(in: .whitespaces)
        let root: URL
        if override.isEmpty {
            guard let found = RuntimeLocation.detectRoot() else {
                return .unavailable("No Shiraz checkout found. Choose one in Settings, or set SHIRAZ_ROOT.")
            }
            root = found
        } else {
            root = URL(fileURLWithPath: (override as NSString).expandingTildeInPath)
            do { try RuntimeLocation.validate(root: root) } catch {
                return .unavailable(error.localizedDescription)
            }
        }
        guard let python = RuntimeLocation.detectPython() else {
            return .unavailable("python3 not found. Install it (for example with Homebrew) or set SHIRAZ_PYTHON.")
        }
        return .ready(RuntimeLocation(root: root, python: python), version: RuntimeLocation.version(root: root))
    }

    // MARK: Library

    func entry(_ package: String) -> AppEntry? { apps.first { $0.packageName == package } }

    func importAPKs(_ urls: [URL]) {
        let apks = urls.filter { $0.pathExtension.lowercased() == "apk" }
        guard !apks.isEmpty else {
            if !urls.isEmpty { alert = "Only .apk files can be added." }
            return
        }
        guard let runtime = runtimeStatus.location else {
            if case .unavailable(let reason) = runtimeStatus { alert = reason }
            return
        }
        isImporting = true
        Task {
            var failures: [String] = []
            for url in apks {
                do {
                    let entry = try await store.importAPK(from: url) { staged in
                        try await Self.inspect(apk: staged, runtime: runtime)
                    }
                    selection = entry.id
                    await store.extractIcon(for: entry.packageName) { apk, output in
                        try await Self.extractIcon(apk: apk, output: output, runtime: runtime)
                    }
                } catch {
                    failures.append("\(url.lastPathComponent): \(error.localizedDescription)")
                }
            }
            apps = store.load()
            reloadIcons()
            isImporting = false
            if !failures.isEmpty { alert = failures.joined(separator: "\n") }
        }
    }

    private nonisolated static func inspect(apk: URL, runtime: RuntimeLocation) async throws -> InspectReport {
        let output = try await ProcessRunner.run(
            executable: runtime.python,
            arguments: runtime.arguments(RuntimeCommand.inspect(apk: apk)),
            environment: runtime.environment,
            workingDirectory: runtime.root)
        guard output.status.succeeded else {
            let message = output.stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            throw LibraryError.inspectFailed(message.isEmpty ? "Inspection \(output.status)." : message)
        }
        return try InspectReport.decode(output.stdout)
    }

    private nonisolated static func extractIcon(apk: URL, output: URL, runtime: RuntimeLocation) async throws {
        let result = try await ProcessRunner.run(
            executable: runtime.python,
            arguments: runtime.arguments(RuntimeCommand.icon(apk: apk, outputDirectory: output)),
            environment: runtime.environment,
            workingDirectory: runtime.root)
        guard result.status.succeeded else {
            let message = result.stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            throw LibraryError.inspectFailed(message.isEmpty ? "Icon extraction \(result.status)." : message)
        }
    }

    func iconError(_ package: String) -> String? { store.iconError(for: package) }

    private func reloadIcons() {
        var rendered: [String: CGImage] = [:]
        for app in apps {
            if let icon = store.icon(for: app.packageName),
               let image = icon.render(directory: store.iconDirectory(for: app.packageName)) {
                rendered[app.packageName] = image
            }
        }
        icons = rendered
    }

    /// Apps imported before icon support, or before a runtime was available,
    /// get their icon extracted once in the background.
    private func extractMissingIcons() {
        let pending = apps.map(\.packageName).filter { !store.iconWasAttempted(for: $0) }
        guard !pending.isEmpty, let runtime = runtimeStatus.location else { return }
        let store = store
        Task {
            for package in pending {
                await store.extractIcon(for: package) { apk, output in
                    try await Self.extractIcon(apk: apk, output: output, runtime: runtime)
                }
            }
            reloadIcons()
        }
    }

    func remove(_ package: String) {
        stop(package)
        do {
            try store.remove(package)
            sessions[package] = nil
            icons[package] = nil
            apps = store.load()
            if selection == package { selection = apps.first?.id }
        } catch {
            alert = error.localizedDescription
        }
    }

    func updateSettings(_ settings: AppSettings, for package: String) {
        guard var entry = entry(package), entry.settings != settings else { return }
        entry.settings = settings
        do {
            try store.save(entry)
            if let index = apps.firstIndex(where: { $0.packageName == package }) { apps[index] = entry }
        } catch {
            alert = error.localizedDescription
        }
    }

    // MARK: Running

    func launch(_ package: String) {
        guard let entry = entry(package), sessions[package]?.isRunning != true else { return }
        let session = Session(package: package, state: .failed(""))
        sessions[package] = session

        func fail(_ message: String) {
            session.state = .failed(message)
            session.log.appendNotice(message)
        }
        guard entry.compatibility.isLaunchable else {
            if case .unsupported(let why) = entry.compatibility { fail(why) }
            return
        }
        if let problem = entry.settings.validationError { return fail(problem) }
        guard let runtime = runtimeStatus.location else {
            if case .unavailable(let reason) = runtimeStatus { fail(reason) }
            return
        }

        let arguments = runtime.arguments(RuntimeCommand.run(apk: store.apkURL(for: package), settings: entry.settings))
        session.log.appendNotice("$ python3 \(arguments.joined(separator: " "))")
        do {
            let process = try ProcessRunner.spawn(
                executable: runtime.python,
                arguments: arguments,
                environment: runtime.environment,
                workingDirectory: runtime.root,
                onOutput: { kind, data in
                    Task { @MainActor in session.log.append(data, stream: kind) }
                },
                onExit: { status in
                    Task { @MainActor in
                        session.log.flush()
                        session.process = nil
                        if session.stopRequested {
                            session.state = .finished("Stopped")
                        } else if status.succeeded {
                            session.state = .finished("Closed")
                        } else {
                            session.state = .failed("The runtime \(status).")
                        }
                        session.log.appendNotice("[\(session.state.summary)]")
                    }
                })
            session.process = process
            session.state = .running(pid: process.pid)
        } catch {
            fail(error.localizedDescription)
        }
    }

    func stop(_ package: String) {
        guard let session = sessions[package], session.isRunning else { return }
        session.stopRequested = true
        session.process?.terminate()
    }

    func stopAll() {
        for package in sessions.keys { stop(package) }
    }
}

extension Session.State {
    var summary: String {
        switch self {
        case .running(let pid): "Running (pid \(pid))"
        case .finished(let text): text
        case .failed(let text): text.isEmpty ? "Failed" : text
        }
    }
}
