// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import Foundation
import LauncherCore
import Observation

/// One launch of one app: its log and lifecycle.
@MainActor @Observable
final class Session: Identifiable {
    enum State: Equatable {
        case starting
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
    fileprivate var relaunchAfterExit = false

    init(package: String, state: State) {
        self.package = package
        self.state = state
    }

    var isRunning: Bool {
        switch state {
        case .starting, .running: true
        default: false
        }
    }
}

/// A short message over the window. A new one replaces the old one.
struct Toast: Identifiable, Equatable {
    enum Style { case info, failure, notImplemented }
    let id = UUID()
    var title: String
    var message: String?
    var style: Style
}

enum LibraryScope: String, CaseIterable, Identifiable, Hashable {
    case all, running, recent, favorites
    var id: String { rawValue }
    var title: String {
        switch self {
        case .all: "All Apps"
        case .running: "Running"
        case .recent: "Recently Played"
        case .favorites: "Favorites"
        }
    }
    var symbol: String {
        switch self {
        case .all: "square.grid.2x2"
        case .running: "play.circle"
        case .recent: "clock"
        case .favorites: "star"
        }
    }
}

enum LibrarySort: String, CaseIterable, Identifiable {
    case name, recentlyPlayed, recentlyAdded, size
    var id: String { rawValue }
    var title: String {
        switch self {
        case .name: "Name"
        case .recentlyPlayed: "Recently Played"
        case .recentlyAdded: "Date Added"
        case .size: "Size"
        }
    }
}

/// Sidebar destinations: library scopes plus places that are not built yet.
enum SidebarItem: Hashable {
    case library(LibraryScope)
    case feature(Feature)
}

@MainActor @Observable
final class LauncherModel {
    private(set) var apps: [AppEntry] = []
    private(set) var sessions: [String: Session] = [:]
    private(set) var isImporting = false
    /// Rendered APK icons and their tint colours by package.
    private(set) var icons: [String: CGImage] = [:]
    private(set) var accents: [String: CGColor] = [:]
    private(set) var toast: Toast?

    var sidebar: SidebarItem? = .library(.all)
    var path: [String] = []
    var search = ""
    var sort: LibrarySort {
        didSet { UserDefaults.standard.set(sort.rawValue, forKey: "librarySort") }
    }
    var showsGrid: Bool {
        didSet { UserDefaults.standard.set(showsGrid, forKey: "libraryGrid") }
    }
    var showImporter = false
    var pendingRemoval: String?

    /// Optional user override of the Shiraz checkout, set in Settings.
    var runtimeRootOverride: String {
        didSet { UserDefaults.standard.set(runtimeRootOverride, forKey: "runtimeRoot") }
    }

    private let store: LibraryStore
    private var toastTask: Task<Void, Never>?
    /// Packages whose label lookup already failed this session.
    private var labelAttempted: Set<String> = []

    init(store: LibraryStore = LibraryStore(directory: LibraryStore.defaultDirectory)) {
        self.store = store
        runtimeRootOverride = UserDefaults.standard.string(forKey: "runtimeRoot") ?? ""
        sort = LibrarySort(rawValue: UserDefaults.standard.string(forKey: "librarySort") ?? "") ?? .name
        showsGrid = UserDefaults.standard.object(forKey: "libraryGrid") as? Bool ?? true
        apps = store.load()
        reloadIcons()
        extractMissingIcons()
        resolveMissingLabels()
        // Runtimes live in their own process groups, so quitting the launcher
        // must stop them. The notification is observed by name to avoid AppKit.
        NotificationCenter.default.addObserver(
            forName: Notification.Name("NSApplicationWillTerminateNotification"),
            object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.stopAll() }
        }
    }

    // MARK: Messages

    /// The one place errors reach the user. Unimplemented features arrive
    /// here as `NotImplementedError`.
    func present(_ error: any Error) {
        if let missing = error as? NotImplementedError {
            show(Toast(title: "Not implemented", message: missing.localizedDescription, style: .notImplemented))
        } else {
            show(Toast(title: "Something went wrong", message: error.localizedDescription, style: .failure))
        }
    }

    func present(failure title: String, _ message: String? = nil) {
        show(Toast(title: title, message: message, style: .failure))
    }

    func inform(_ title: String, _ message: String? = nil) {
        show(Toast(title: title, message: message, style: .info))
    }

    /// Run an unimplemented feature's control. Always reports the error.
    func attempt(_ feature: Feature) {
        do { try feature.perform() } catch { present(error) }
    }

    func dismissToast() {
        toastTask?.cancel()
        toast = nil
    }

    private func show(_ next: Toast) {
        toast = next
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(next.style == .failure ? 6 : 3.5))
            guard !Task.isCancelled else { return }
            self?.toast = nil
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

    var runningCount: Int { sessions.values.filter(\.isRunning).count }

    func count(_ scope: LibraryScope) -> Int { apps.filter { matches($0, scope) }.count }

    private func matches(_ app: AppEntry, _ scope: LibraryScope) -> Bool {
        switch scope {
        case .all: true
        case .running: sessions[app.packageName]?.isRunning == true
        case .recent: app.lastLaunchedAt != nil
        case .favorites: app.isFavorite
        }
    }

    func visibleApps(in scope: LibraryScope) -> [AppEntry] {
        let query = search.trimmingCharacters(in: .whitespaces)
        let filtered = apps.filter { app in
            matches(app, scope) && (query.isEmpty
                || app.displayName.localizedCaseInsensitiveContains(query)
                || app.packageName.localizedCaseInsensitiveContains(query))
        }
        let order = scope == .recent ? .recentlyPlayed : sort
        return filtered.sorted { a, b in
            switch order {
            case .name:
                return a.displayName.localizedCaseInsensitiveCompare(b.displayName) == .orderedAscending
            case .recentlyPlayed:
                return (a.lastLaunchedAt ?? .distantPast) > (b.lastLaunchedAt ?? .distantPast)
            case .recentlyAdded:
                return a.importedAt > b.importedAt
            case .size:
                return (apkSize(a.packageName) ?? 0) > (apkSize(b.packageName) ?? 0)
            }
        }
    }

    func apkSize(_ package: String) -> Int64? { store.apkSize(for: package) }

    func apkURL(_ package: String) -> URL { store.apkURL(for: package) }

    func importAPKs(_ urls: [URL]) {
        let apks = urls.filter { $0.pathExtension.lowercased() == "apk" }
        guard !apks.isEmpty else {
            if !urls.isEmpty { present(failure: "Only APK files can be added") }
            return
        }
        guard let runtime = runtimeStatus.location else {
            if case .unavailable(let reason) = runtimeStatus { present(failure: "Runtime unavailable", reason) }
            return
        }
        isImporting = true
        Task {
            var failures: [String] = []
            var added: [AppEntry] = []
            for url in apks {
                do {
                    let entry = try await store.importAPK(from: url) { staged in
                        try await Self.inspect(apk: staged, runtime: runtime)
                    }
                    await store.extractIcon(for: entry.packageName) { apk, output in
                        try await Self.extractIcon(apk: apk, output: output, runtime: runtime)
                    }
                    var labelled = entry
                    labelled.label = try? await Self.label(apk: store.apkURL(for: entry.packageName), runtime: runtime)
                    try? store.save(labelled)
                    added.append(labelled)
                } catch {
                    failures.append("\(url.lastPathComponent): \(error.localizedDescription)")
                }
            }
            apps = store.load()
            reloadIcons()
            isImporting = false
            if !failures.isEmpty {
                present(failure: failures.count == 1 ? "Could not add APK" : "Could not add \(failures.count) APKs",
                        failures.joined(separator: "\n"))
            } else if added.count == 1, let entry = added.first {
                inform("Added \(entry.displayName)")
            } else if added.count > 1 {
                inform("Added \(added.count) apps")
            }
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

    private nonisolated static func label(apk: URL, runtime: RuntimeLocation) async throws -> String {
        struct Output: Decodable { var label: String }
        let result = try await ProcessRunner.run(
            executable: runtime.python,
            arguments: runtime.arguments(RuntimeCommand.label(apk: apk)),
            environment: runtime.environment,
            workingDirectory: runtime.root)
        guard result.status.succeeded else { throw LibraryError.inspectFailed("Label lookup \(result.status).") }
        return try JSONDecoder().decode(Output.self, from: result.stdout).label
    }

    /// Entries imported before labels were read get theirs in the background.
    private func resolveMissingLabels() {
        let pending = apps.filter { $0.label == nil && !labelAttempted.contains($0.packageName) }.map(\.packageName)
        guard !pending.isEmpty, let runtime = runtimeStatus.location else { return }
        labelAttempted.formUnion(pending)
        let store = store
        Task {
            for package in pending {
                guard let label = try? await Self.label(apk: store.apkURL(for: package), runtime: runtime),
                      var entry = entry(package) else { continue }
                entry.label = label
                save(entry)
            }
            apps = store.load()
        }
    }

    private func reloadIcons() {
        var rendered: [String: CGImage] = [:]
        var tints: [String: CGColor] = [:]
        for app in apps {
            if let icon = store.icon(for: app.packageName),
               let image = icon.render(directory: store.iconDirectory(for: app.packageName)) {
                rendered[app.packageName] = image
                tints[app.packageName] = AppIcon.accentColor(of: image)
            }
        }
        icons = rendered
        accents = tints
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

    func requestRemoval(_ package: String) { pendingRemoval = package }

    func remove(_ package: String) {
        let name = entry(package)?.displayName ?? package
        stop(package)
        do {
            try store.remove(package)
            sessions[package] = nil
            icons[package] = nil
            accents[package] = nil
            apps = store.load()
            path.removeAll { $0 == package }
            inform("Removed \(name)")
        } catch {
            present(error)
        }
    }

    func toggleFavorite(_ package: String) {
        guard var entry = entry(package) else { return }
        entry.isFavorite.toggle()
        save(entry)
    }

    func updateSettings(_ settings: AppSettings, for package: String) {
        guard var entry = entry(package), entry.settings != settings else { return }
        entry.settings = settings
        save(entry)
    }

    private func save(_ entry: AppEntry) {
        do {
            try store.save(entry)
            if let index = apps.firstIndex(where: { $0.packageName == entry.packageName }) { apps[index] = entry }
        } catch {
            present(error)
        }
    }

    // MARK: Running

    func launch(_ package: String) {
        guard var entry = entry(package), sessions[package]?.isRunning != true else { return }
        guard entry.compatibility.isLaunchable else {
            if case .unsupported(let why) = entry.compatibility { present(failure: "Cannot launch \(entry.displayName)", why) }
            return
        }
        if let problem = entry.settings.validationError {
            return present(failure: "Check \(entry.displayName)'s settings", problem)
        }
        guard let runtime = runtimeStatus.location else {
            if case .unavailable(let reason) = runtimeStatus { present(failure: "Runtime unavailable", reason) }
            return
        }
        let session = Session(package: package, state: .starting)
        sessions[package] = session
        entry.lastLaunchedAt = Date()
        save(entry)

        let name = entry.displayName
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
                onExit: { [weak self] status in
                    Task { @MainActor in
                        session.log.flush()
                        session.process = nil
                        if session.stopRequested {
                            session.state = .finished("Stopped")
                        } else if status.succeeded {
                            session.state = .finished("Closed")
                        } else {
                            session.state = .failed("The runtime \(status).")
                            self?.present(failure: "\(name) quit unexpectedly", "The runtime \(status). See its console.")
                        }
                        session.log.appendNotice("[\(session.state.summary)]")
                        if session.relaunchAfterExit { self?.launch(package) }
                    }
                })
            session.process = process
            session.state = .running(pid: process.pid)
        } catch {
            session.state = .failed(error.localizedDescription)
            session.log.appendNotice(error.localizedDescription)
            present(error)
        }
    }

    func stop(_ package: String) {
        guard let session = sessions[package], session.isRunning else { return }
        session.stopRequested = true
        session.process?.terminate()
    }

    func restart(_ package: String) {
        guard let session = sessions[package], session.isRunning else { return launch(package) }
        session.relaunchAfterExit = true
        stop(package)
    }

    func toggleRun(_ package: String) {
        if sessions[package]?.isRunning == true { stop(package) } else { launch(package) }
    }

    func clearLog(_ package: String) {
        sessions[package]?.log = LogBuffer()
    }

    func stopAll() {
        for package in sessions.keys { stop(package) }
    }
}

extension Session.State {
    var summary: String {
        switch self {
        case .starting: "Starting"
        case .running: "Running"
        case .finished(let text): text
        case .failed(let text): text.isEmpty ? "Failed" : text
        }
    }
}
