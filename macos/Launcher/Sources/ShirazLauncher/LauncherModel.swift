// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import CryptoKit
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
    /// Requests from this app to the launcher (InstallInbox).
    fileprivate var inbox: URL?
    fileprivate var inboxWatcher: Task<Void, Never>?

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

/// A page pushed in the detail column.
enum DetailRoute: Hashable {
    case app(String)
    case catalog(CatalogApp)
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

/// Sidebar destinations: library scopes, the F-Droid catalog, and places that are not built yet.
enum SidebarItem: Hashable {
    case library(LibraryScope)
    case catalog
    case feature(Feature)
}

@MainActor @Observable
final class LauncherModel {
    private(set) var apps: [AppEntry] = []
    private(set) var sessions: [String: Session] = [:]
    /// Imports in flight (drops, catalog installs, in-app installs).
    private var importCount = 0
    var isImporting: Bool { importCount > 0 }
    /// Rendered APK icons and their tint colours by package.
    private(set) var icons: [String: CGImage] = [:]
    private(set) var accents: [String: CGColor] = [:]
    private(set) var toast: Toast?

    /// Changing place resets the detail stack.
    /// Not-built features bounce back (SidebarView) and leave the stack alone.
    var sidebar: SidebarItem? = .library(.all) {
        didSet {
            guard sidebar != oldValue else { return }
            if case .feature = sidebar { return }
            if case .feature = oldValue { return }
            routes = []
        }
    }
    /// The detail column's one navigation stack, shared by every sidebar place:
    /// SwiftUI keeps navigation state per split-view column, and swapping in a
    /// stack whose path has another element type traps when a page is pushed.
    var routes: [DetailRoute] = []
    /// Library pages on the stack.
    var path: [String] {
        get { routes.compactMap { if case .app(let package) = $0 { package } else { nil } } }
        set { routes = newValue.map(DetailRoute.app) }
    }

    /// Push one app's library page, from anywhere in the window. Back returns
    /// to where the user was (a catalog page, for example).
    func show(_ package: String) { routes.append(.app(package)) }
    var search = ""
    var sort: LibrarySort {
        didSet { UserDefaults.standard.set(sort.rawValue, forKey: "librarySort") }
    }
    var showsGrid: Bool {
        didSet { UserDefaults.standard.set(showsGrid, forKey: "libraryGrid") }
    }
    var showImporter = false
    var pendingRemoval: String?
    /// A running app's request waiting for the user's answer.
    private(set) var consent: ConsentPrompt?

    /// Optional user override of the Shiraz checkout, set in Settings.
    var runtimeRootOverride: String {
        didSet { UserDefaults.standard.set(runtimeRootOverride, forKey: "runtimeRoot") }
    }

    let store: LibraryStore
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
        writeMissingMetadata()
        InstallInbox.removeStale(in: store.sessionsDirectory)
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
        Task {
            let (added, failures) = await addAPKs(apks)
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

    /// Copy, inspect, label and extract icons for each APK. Reports nothing
    /// itself; callers word the result. `expectedPackage` rejects an APK
    /// whose manifest names another package.
    func addAPKs(
        _ apks: [URL], expectedPackage: String? = nil, installer: String? = nil, replacing: Bool = true,
        check: (@Sendable (InspectReport) async throws -> Void)? = nil
    ) async -> (added: [AppEntry], failures: [String]) {
        guard let runtime = runtimeStatus.location else {
            if case .unavailable(let reason) = runtimeStatus { return ([], ["Runtime unavailable: \(reason)"]) }
            return ([], ["Runtime unavailable"])
        }
        importCount += 1
        defer { importCount -= 1 }
        let tools = RuntimeTools(runtime: runtime)
        var failures: [String] = []
        var added: [AppEntry] = []
        for url in apks {
            do {
                added.append(try await tools.add(
                    apk: url, to: store, expected: expectedPackage, installer: installer, replacing: replacing, check: check))
            } catch {
                failures.append("\(url.lastPathComponent): \(error.localizedDescription)")
            }
        }
        apps = store.load()
        reloadIcons()
        return (added, failures)
    }

    /// Entries without current package metadata (imported earlier, or by an
    /// older format) get it in the background; the recorded installer is kept.
    private func writeMissingMetadata() {
        let pending = apps.map(\.packageName).filter { !store.hasCurrentMetadata(for: $0) }
        guard !pending.isEmpty, let runtime = runtimeStatus.location else { return }
        let store = store
        Task {
            for package in pending {
                let installer = store.metadata(for: package)?["installer"].flatMap { LibraryStore.isValidPackageName($0) ? $0 : nil }
                await RuntimeTools(runtime: runtime).writeMetadata(for: package, installer: installer, store: store)
            }
        }
    }

    /// Entries imported before labels were read get theirs in the background.
    private func resolveMissingLabels() {
        let pending = apps.filter { $0.label == nil && !labelAttempted.contains($0.packageName) }.map(\.packageName)
        guard !pending.isEmpty, let runtime = runtimeStatus.location else { return }
        labelAttempted.formUnion(pending)
        let store = store
        Task {
            for package in pending {
                guard let label = try? await RuntimeTools(runtime: runtime).label(apk: store.apkURL(for: package)),
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
                    try await RuntimeTools(runtime: runtime).extractIcon(apk: apk, output: output)
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
            routes.removeAll { $0 == .app(package) }
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
        let inbox = store.sessionsDirectory.appending(path: session.id.uuidString, directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        session.inbox = inbox
        let runEnvironment = RuntimeCommand.runEnvironment(settings: entry.settings)
        let assignments = runEnvironment.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
        session.log.appendNotice("$ " + (assignments + ["python3"] + arguments).joined(separator: " "))
        do {
            let process = try ProcessRunner.spawn(
                executable: runtime.python,
                arguments: arguments,
                environment: runtime.environment
                    .merging(runEnvironment) { _, new in new }
                    .merging(RuntimeCommand.libraryEnvironment(apps: store.appsDirectory, inbox: inbox)) { _, new in new },
                workingDirectory: runtime.root,
                onOutput: { kind, data in
                    Task { @MainActor in session.log.append(data, stream: kind) }
                },
                onExit: { [weak self] status in
                    Task { @MainActor in
                        session.log.flush()
                        session.process = nil
                        session.inboxWatcher?.cancel()
                        if let inbox = session.inbox { try? FileManager.default.removeItem(at: inbox) }
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
            session.inboxWatcher = Task { [weak self] in await self?.watchInbox(session) }
        } catch {
            session.state = .failed(error.localizedDescription)
            session.log.appendNotice(error.localizedDescription)
            present(error)
        }
    }

    // MARK: Requests from running apps

    /// Answer the session's install, launch and remove requests until it ends.
    private func watchInbox(_ session: Session) async {
        guard let inbox = session.inbox else { return }
        while !Task.isCancelled, session.isRunning {
            for request in InstallInbox.pending(in: inbox) {
                await handle(request, from: session, inbox: inbox)
            }
            try? await Task.sleep(for: .milliseconds(300))
        }
    }

    private func handle(_ request: InboxRequest, from session: Session, inbox: URL) async {
        guard let requester = entry(session.package) else {
            try? InstallInbox.answer(id: request.id, in: inbox, ok: false, package: nil, message: "The requesting app left the library.")
            return
        }
        // Work on the launcher's own copy: the app can still write to its inbox.
        var request = request
        var scratch: URL?
        defer { if let scratch { try? FileManager.default.removeItem(at: scratch) } }
        if let apk = request.apk {
            do {
                let directory = try store.makeDownloadDirectory()
                scratch = directory
                let copy = directory.appending(path: "request.apk")
                try FileManager.default.copyItem(at: apk, to: copy)
                request.apk = copy
            } catch {
                try? InstallInbox.answer(id: request.id, in: inbox, ok: false, package: nil, message: "The APK could not be read.")
                return
            }
        }
        let name = requester.displayName
        let actions = InboxActions(
            confirm: { [unowned self] consent in await ask(consent) },
            install: { [unowned self] apk, expected, installer in
                session.log.appendNotice("[\(name) asked to install \(expected ?? "an APK")]")
                let (added, failures) = await addAPKs([apk], expectedPackage: expected, installer: installer, replacing: false)
                if let entry = added.first { return entry }
                throw LibraryError.inspectFailed(Self.reason(failures, apk))
            },
            update: { [unowned self] apk, package, installer in
                session.log.appendNotice("[\(name) asked to update \(package)]")
                return try await update(package, with: apk, installer: installer)
            },
            launch: { [unowned self] package in
                launch(package)
                return sessions[package]?.isRunning == true
            },
            remove: { [unowned self] package in
                remove(package)
                return entry(package) == nil
            })
        switch await InstallInbox.handle(request, requester: requester, library: apps, inbox: inbox, actions: actions) {
        case .refused(let reason):
            session.log.appendNotice("[Refused \(request.kind.rawValue) request: \(reason)]")
            present(failure: "\(name) could not \(request.kind.rawValue) an app", reason)
        case .declined:
            session.log.appendNotice("[Declined \(request.kind.rawValue) request]")
        case .expired:
            session.log.appendNotice("[\(request.kind.rawValue) request expired before it was answered]")
            present(failure: "\(name) stopped waiting", "Nothing was changed. Try again from \(name).")
        case .installed(let added):
            inform("\(name) added \(added.displayName)", "It is now in your library.")
        case .updated(let updated):
            inform("\(name) updated \(updated.displayName)", "Version \(updated.versionName ?? updated.versionCode ?? "?"), checked against the signed F-Droid index.")
        case .installFailed(let reason):
            present(failure: "\(name) could not add an app", reason)
        case .launched, .removed:
            break
        }
    }

    private static func reason(_ failures: [String], _ apk: URL) -> String {
        failures.joined(separator: "\n").replacingOccurrences(of: apk.lastPathComponent + ": ", with: "")
    }

    // MARK: Updates

    /// Replace a library app with `apk` when UpdatePolicy allows it. The
    /// catalog is refreshed once when the APK is newer than the cached index.
    func update(_ package: String, with apk: URL, installer: String?) async throws -> AppEntry {
        guard let installed = entry(package) else { throw LibraryError.inspectFailed("\(package) is not in the library.") }
        guard sessions[package]?.isRunning != true else {
            throw LibraryError.inspectFailed("\(installed.displayName) is running. Close it, then try again.")
        }
        guard let runtime = runtimeStatus.location else { throw LibraryError.inspectFailed("Runtime unavailable.") }
        let tools = RuntimeTools(runtime: runtime)
        let digest = try await Self.sha256(of: apk)
        var listing = await loadCatalogApp(package)
        if listing?.releases.contains(where: { $0.sha256 == digest }) != true {
            _ = try? await tools.output(RuntimeCommand.fdroidSync(cache: store.catalogDirectory), what: "Catalog update", timeout: 300)
            listing = await loadCatalogApp(package)
        }
        let signers = try await tools.signers(apk: store.apkURL(for: package))
        let catalogApp = listing
        let (added, failures) = await addAPKs([apk], expectedPackage: package, installer: installer, replacing: true) { report in
            if let refusal = UpdatePolicy.refusal(
                installed: installed, installedSigners: signers, candidate: AppEntry(report: report), catalog: catalogApp) {
                throw LibraryError.inspectFailed(refusal)
            }
        }
        if let entry = added.first { return entry }
        throw LibraryError.inspectFailed(Self.reason(failures, apk))
    }

    /// Signing certificates of a library app, for UpdatePolicy.
    func installedSigners(_ package: String) async throws -> [String] {
        guard let runtime = runtimeStatus.location else { throw LibraryError.inspectFailed("Runtime unavailable.") }
        return try await RuntimeTools(runtime: runtime).signers(apk: store.apkURL(for: package))
    }

    private func loadCatalogApp(_ package: String) async -> CatalogApp? {
        let url = store.catalogURL
        return await Task.detached(priority: .userInitiated) {
            guard let data = try? Data(contentsOf: url, options: .mappedIfSafe) else { return nil }
            return (try? Catalog.decode(data))?.apps.first { $0.packageName == package }
        }.value
    }

    private nonisolated static func sha256(of url: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            let handle = try FileHandle(forReadingFrom: url)
            defer { try? handle.close() }
            var hasher = SHA256()
            while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
            return hasher.finalize().map { String(format: "%02x", $0) }.joined()
        }.value
    }

    // MARK: Consent

    /// Ask the user about a request; the window comes to the front.
    private func ask(_ request: InboxConsent) async -> Bool {
        let requester = request.requester.displayName
        var title: String
        var message: String
        let action: String
        switch request.kind {
        case .install:
            let facts = await describe(request.apk)
            title = "Allow \(requester) to add \(facts?.name ?? "an app")?"
            message = facts.map { "\($0.package), version \($0.version). Apps from \(requester) are not tested in Shiraz." }
                ?? "The APK could not be inspected; adding it will fail."
            action = "Add"
        case .update:
            let installed = request.package.flatMap(entry)
            let facts = await describe(request.apk)
            title = "Allow \(requester) to update \(installed?.displayName ?? request.package ?? "an app")?"
            message = "Version \(installed?.versionName ?? installed?.versionCode ?? "?") to \(facts?.version ?? "?"). "
                + "The APK is checked against the signed F-Droid index and the installed signer first. App data is kept."
            action = "Update"
        case .remove:
            let installed = request.package.flatMap(entry)
            title = "Allow \(requester) to remove \(installed?.displayName ?? request.package ?? "an app")?"
            message = "The imported APK and its settings are deleted from the library."
            action = "Remove"
        }
        if let pending = consent { pending.resume(false) }
        return await withCheckedContinuation { continuation in
            let prompt = ConsentPrompt(title: title, message: message, action: action,
                                       destructive: request.kind == .remove) { [weak self] allowed in
                self?.consent = nil
                continuation.resume(returning: allowed)
            }
            consent = prompt
            // Close the question when the app stops waiting for it.
            let file = request.requestFile
            Task { [weak self] in
                while self?.consent?.id == prompt.id {
                    if !FileManager.default.fileExists(atPath: file.path) { prompt.resume(false); break }
                    try? await Task.sleep(for: .seconds(1))
                }
            }
        }
    }

    func answerConsent(_ allowed: Bool) { consent?.resume(allowed) }

    private func describe(_ apk: URL?) async -> (name: String, package: String, version: String)? {
        guard let apk, let runtime = runtimeStatus.location else { return nil }
        let tools = RuntimeTools(runtime: runtime)
        guard let report = try? await tools.inspect(apk: apk) else { return nil }
        let label = try? await tools.label(apk: apk)
        let manifest = report.manifest
        return (label ?? manifest.package, manifest.package, manifest.versionName ?? manifest.versionCode ?? "?")
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

/// One question for the user about a running app's request.
struct ConsentPrompt: Identifiable {
    let id = UUID()
    var title: String
    var message: String
    var action: String
    var destructive: Bool
    private let answer: (Bool) -> Void
    private let answered = Answered()

    init(title: String, message: String, action: String, destructive: Bool, answer: @escaping (Bool) -> Void) {
        self.title = title
        self.message = message
        self.action = action
        self.destructive = destructive
        self.answer = answer
    }

    /// Only the first answer counts.
    func resume(_ allowed: Bool) {
        guard !answered.value else { return }
        answered.value = true
        answer(allowed)
    }

    private final class Answered { var value = false }
}
