// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import CryptoKit
import Foundation
import ImageIO
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
    fileprivate var launchID: String?
    fileprivate var stdoutReader: FileHandle?
    fileprivate var stderrReader: FileHandle?
    fileprivate var stopRequested = false
    fileprivate var relaunchAfterExit = false
    fileprivate let acknowledgement = LaunchAcknowledgement()
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
        case .all: "Home"
        case .running: "Running"
        case .recent: "Recently Played"
        case .favorites: "Favorites"
        }
    }
    var symbol: String {
        switch self {
        case .all: "house"
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
    private(set) var apps: [AppEntry] = [] {
        didSet { if apps != oldValue { reconcileHome() } }
    }
    private(set) var sessions: [String: Session] = [:]
    /// Imports in flight (drops, catalog installs, in-app installs).
    private var importCount = 0
    var isImporting: Bool { importCount > 0 }
    /// Rendered APK icons and their tint colours by package.
    private(set) var icons: [String: CGImage] = [:]
    private(set) var accents: [String: CGColor] = [:]
    /// Packages whose icon is an adaptive icon, already masked to a squircle.
    private(set) var adaptiveIcons: Set<String> = []
    /// Grid order, folders, dock order and wallpaper (LibraryStore.homeURL).
    private(set) var home = HomeLayout()
    /// The user's wallpaper picture, decoded once.
    private(set) var wallpaperImage: CGImage?
    /// The home item being dragged, so drop targets know what moves.
    var homeDragging: String?
    /// App page section to open on the next push (Open console).
    var requestedSection: AppSection?
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
    private struct ImportRequest {
        let apk: URL
        let runtime: RuntimeLocation
        let expected: String?
        let installer: String?
        let replacing: Bool
        let check: (@Sendable (InspectReport) async throws -> Void)?
        let completion: CheckedContinuation<AppEntry, any Error>
    }
    private enum HostCommand { case start(Session, RuntimeLocation, HostLaunchRequester?), stop(Session), remove(String, RuntimeLocation), importAPK(ImportRequest) }
    private var removingPackages: Set<String> = []
    private var updateStops: Set<String> = []
    private var removalWaiters: [String: [CheckedContinuation<Bool, Never>]] = [:]
    private var hostCommands: [HostCommand] = []
    private var sharedHost: SharedHostClient?
    private var hostRuntime: RuntimeLocation?
    private var hostPump: Task<Void, Never>?

    init(store: LibraryStore = LibraryStore(directory: LibraryStore.defaultDirectory)) {
        self.store = store
        runtimeRootOverride = UserDefaults.standard.string(forKey: "runtimeRoot") ?? ""
        sort = LibrarySort(rawValue: UserDefaults.standard.string(forKey: "librarySort") ?? "") ?? .name
        showsGrid = UserDefaults.standard.object(forKey: "libraryGrid") as? Bool ?? true
        home = store.loadHome()
        apps = store.load()
        reconcileHome()
        loadWallpaperImage()
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

    /// Console Copy: the lines on screen (filter applied), one per line.
    func copyLog(_ lines: [String]) {
        guard !lines.isEmpty else { return }
        Task {
            do {
                try await Clipboard().copy(lines.joined(separator: "\n"))
                inform(lines.count == 1 ? "Copied 1 line" : "Copied \(lines.count) lines")
            } catch {
                present(error)
            }
        }
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
        // Drags inside the window (home icons) carry text, not files.
        let urls = urls.filter(\.isFileURL)
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
        var failures: [String] = []
        var added: [AppEntry] = []
        for url in apks {
            do {
                guard hostCommands.count < 32 else { throw LibraryError.busy }
                let imported: AppEntry = try await withCheckedThrowingContinuation { completion in
                    hostCommands.append(.importAPK(ImportRequest(apk: url, runtime: runtime,
                                                                 expected: expectedPackage, installer: installer, replacing: replacing,
                                                                 check: check, completion: completion)))
                    ensureHostPump()
                }
                added.append(imported)
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
                await RuntimeTools(runtime: runtime).refreshMissingMetadata(for: package, store: store)
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
                if !(await RuntimeTools(runtime: runtime).refreshMissingLabel(for: package, store: store)) {
                    labelAttempted.remove(package)
                }
            }
            apps = store.load()
        }
    }

    private func reloadIcons() {
        var rendered: [String: CGImage] = [:]
        var tints: [String: CGColor] = [:]
        var adaptive: Set<String> = []
        for app in apps {
            if let icon = store.icon(for: app.packageName),
               let image = icon.render(directory: store.iconDirectory(for: app.packageName)) {
                rendered[app.packageName] = image
                tints[app.packageName] = AppIcon.accentColor(of: image)
                if icon.kind == .adaptive { adaptive.insert(app.packageName) }
            }
        }
        icons = rendered
        accents = tints
        adaptiveIcons = adaptive
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

    // MARK: Home screen

    /// Keep the layout in step with the library; save only real changes.
    private func reconcileHome() {
        let favorites = apps.filter(\.isFavorite).map(\.packageName)
        // A first layout follows the library's name order.
        let next = home.reconciled(with: apps.map(\.packageName), favorites: favorites)
        if next != home { updateHome { $0 = next } }
    }

    /// Apply an edit to the layout and persist it.
    func updateHome(_ edit: (inout HomeLayout) -> Void) {
        var next = home
        edit(&next)
        guard next != home else { return }
        home = next
        do { try store.saveHome(next) } catch { present(error) }
    }

    /// Favorites in dock order.
    var dockApps: [AppEntry] { home.dock.compactMap(entry) }

    func setWallpaper(_ wallpaper: Wallpaper) {
        updateHome { $0.wallpaper = wallpaper }
        loadWallpaperImage()
    }

    func importWallpaper(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let wallpaper = try store.importWallpaper(from: url)
            guard let picture = store.wallpaperURL(wallpaper).flatMap(Self.decodeImage) else {
                throw LibraryError.notAnImage("it could not be decoded")
            }
            updateHome { $0.wallpaper = wallpaper }
            wallpaperImage = picture
        } catch {
            present(error)
        }
    }

    private func loadWallpaperImage() {
        wallpaperImage = store.wallpaperURL(home.wallpaper).flatMap(Self.decodeImage)
    }

    private nonisolated static func decodeImage(_ url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        // Wallpapers are drawn at window size; a 4K-wide thumbnail is plenty.
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: 3840,
            kCGImageSourceCreateThumbnailWithTransform: true,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    /// Dock membership is the favorite flag; Add to dock appends it.
    func toggleDock(_ package: String) {
        guard let app = entry(package) else { return }
        if !app.isFavorite, home.dock.count >= HomeLayout.dockLimit {
            return present(failure: "The dock is full", "Remove an app from the dock first (at most \(HomeLayout.dockLimit)).")
        }
        toggleFavorite(package)
    }

    /// Push the app page at its Console section.
    func showConsole(_ package: String) {
        requestedSection = .console
        show(package)
    }

    /// When the current session started, while it runs.
    func runningSince(_ package: String) -> Date? {
        guard let session = sessions[package], session.isRunning else { return nil }
        return session.startedAt
    }

    func remove(_ package: String) {
        guard !removingPackages.contains(package) else { return }
        guard let runtime = runtimeStatus.location else {
            present(failure: "Runtime unavailable", "Package removal requires the shared host.")
            return
        }
        guard hostCommands.count < 32 else {
            present(failure: "Too many pending app operations.")
            return
        }
        removingPackages.insert(package)
        sessions[package]?.relaunchAfterExit = false
        hostCommands.append(.remove(package, runtime))
        ensureHostPump()
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

    func launch(_ package: String, requester: HostLaunchRequester? = nil) {
        guard !removingPackages.contains(package), var entry = entry(package), sessions[package]?.isRunning != true else { return }
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

        guard hostCommands.count < 32 else {
            session.state = .failed("Too many pending app operations.")
            session.acknowledgement.finish(false)
            return
        }
        session.log.appendNotice("Starting through the shared Android host.")
        hostCommands.append(.start(session, runtime, requester))
        ensureHostPump()
    }

    private func ensureHostPump() {
        guard hostPump == nil else { return }
        hostPump = Task { [weak self] in await self?.runHostPump() }
    }

    private func launchAndWait(_ package: String, requester: Session) async -> Bool {
        // Reusing an existing activity needs a separately authorized delivery path.
        guard sessions[package]?.isRunning != true, requester.isRunning,
              !requester.stopRequested, let token = requester.launchID else { return false }
        launch(package, requester: HostLaunchRequester(package: requester.package, launchID: token))
        guard let session = sessions[package], session.isRunning, !session.stopRequested else { return false }
        if case .running = session.state { return true }
        return await session.acknowledgement.wait()
    }

    /// One command owner avoids overlapping requests on the private host pipe.
    private func runHostPump() async {
        defer { hostPump = nil }
        do {
            while !Task.isCancelled {
                if !hostCommands.isEmpty {
                    let command = hostCommands.removeFirst()
                    switch command {
                    case .importAPK(let request):
                        defer {
                            for package in updateStops where sessions[package]?.isRunning == true {
                                sessions[package]?.stopRequested = false
                            }
                            updateStops.removeAll()
                        }
                        do {
                            if sharedHost == nil {
                                sharedHost = try await SharedHostClient.connect(runtime: request.runtime,
                                                                                registry: store.registryDirectory, library: store.directory)
                                hostRuntime = request.runtime
                            }
                            guard hostRuntime == request.runtime, let host = sharedHost else {
                                throw SpawnError(message: "Restart the launcher to use a different runtime.")
                            }
                            let imported = try await RuntimeTools(runtime: request.runtime).add(
                                apk: request.apk, to: store, expected: request.expected,
                                installer: request.installer, replacing: request.replacing, host: host,
                                onReply: { [weak self] reply in await self?.acceptCompletions(reply) },
                                onQuiesce: { [weak self] package in await self?.prepareUpdateStop(package) },
                                check: request.check)
                            request.completion.resume(returning: imported)
                        } catch { request.completion.resume(throwing: error) }
                    case .start(let session, let runtime, let requester):
                        defer {
                            if case .running = session.state {
                                session.acknowledgement.finish(!session.stopRequested)
                            } else {
                                session.acknowledgement.finish(false)
                            }
                        }
                        if sharedHost == nil {
                            sharedHost = try await SharedHostClient.connect(runtime: runtime,
                                                                            registry: store.registryDirectory, library: store.directory)
                            hostRuntime = runtime
                        }
                        guard hostRuntime == runtime, let host = sharedHost else {
                            throw SpawnError(message: "Restart the launcher to use a different runtime.")
                        }
                        // Embedded launch: the app's frames and input go through the stage.
                        let embed = try? EmbedHub.shared.prepareLaunch(session.package, root: store.directory)
                        do {
                            let reply = try await RuntimeTools(runtime: runtime).launchRegistered(
                                package: session.package, store: store, host: host, requester: requester, embed: embed)
                            acceptCompletions(reply)
                            guard reply.ok == true, let token = reply.launch_id, let pid = reply.pid,
                                  let inbox = reply.inbox, let stdout = reply.stdout, let stderr = reply.stderr else {
                                throw SpawnError(message: reply.error ?? "Incomplete shared-host launch response.")
                            }
                            session.launchID = token
                            session.state = .running(pid: Int32(pid))
                            session.inbox = URL(fileURLWithPath: inbox)
                            session.stdoutReader = try? FileHandle(forReadingFrom: URL(fileURLWithPath: stdout))
                            session.stderrReader = try? FileHandle(forReadingFrom: URL(fileURLWithPath: stderr))
                            session.inboxWatcher = Task { [weak self] in await self?.watchInbox(session) }
                            if embed != nil, requester == nil { showRunning(session.package) }
                            if session.stopRequested {
                                acceptCompletions(try await host.stop(package: session.package, launchID: token))
                            }
                        } catch {
                            if embed != nil { EmbedHub.shared.end(session.package) }
                            session.state = .failed(error.localizedDescription)
                            session.log.appendNotice(error.localizedDescription)
                            present(error)
                        }
                    case .remove(let package, let runtime):
                        defer {
                            removingPackages.remove(package)
                            for waiter in removalWaiters.removeValue(forKey: package) ?? [] {
                                waiter.resume(returning: entry(package) == nil)
                            }
                        }
                        do {
                            if sharedHost == nil {
                                sharedHost = try await SharedHostClient.connect(runtime: runtime,
                                                                                registry: store.registryDirectory, library: store.directory)
                                hostRuntime = runtime
                            }
                            guard hostRuntime == runtime, let host = sharedHost else {
                                throw SpawnError(message: "Restart the launcher to use a different runtime.")
                            }
                            let name = entry(package)?.displayName ?? package
                            sessions[package]?.stopRequested = true
                            try await RuntimeTools(runtime: runtime).removeRegistered(package: package, store: store,
                                                                                      host: host, onReply: { [weak self] reply in await self?.acceptCompletions(reply) })
                            if let session = sessions[package] {
                                drainHostLogs(session, final: true)
                                session.inboxWatcher?.cancel()
                            }
                            sessions[package] = nil
                            icons[package] = nil
                            accents[package] = nil
                            apps = store.load()
                            routes.removeAll { $0 == .app(package) }
                            inform("Removed \(name)")
                        } catch {
                            sessions[package]?.stopRequested = false
                            present(error)
                        }
                    case .stop(let session):
                        if let host = sharedHost, let token = session.launchID, session.isRunning {
                            let reply = try await host.stop(package: session.package, launchID: token)
                            acceptCompletions(reply)
                            if reply.ok != true { throw SpawnError(message: reply.error ?? "Cannot stop the app.") }
                        }
                    }
                } else if let host = sharedHost {
                    let reply = try await host.status()
                    guard reply.ok == true else { throw SpawnError(message: reply.error ?? "Shared host status failed.") }
                    acceptCompletions(reply)
                    for session in sessions.values where session.isRunning { drainHostLogs(session) }
                    try await Task.sleep(for: .milliseconds(250))
                } else { return }
            }
        } catch {
            await sharedHost?.close()
            sharedHost = nil; hostRuntime = nil
            for command in hostCommands {
                if case .importAPK(let request) = command { request.completion.resume(throwing: error) }
            }
            hostCommands.removeAll()
            removingPackages.removeAll()
            for waiters in removalWaiters.values {
                for waiter in waiters { waiter.resume(returning: false) }
            }
            removalWaiters.removeAll()
            for session in sessions.values where session.isRunning {
                session.acknowledgement.finish(false)
                drainHostLogs(session, final: true)
                session.inboxWatcher?.cancel()
                session.state = .failed("Shared host disconnected: " + error.localizedDescription)
            }
            if !Task.isCancelled { present(error) }
        }
    }

    private func drainHostLogs(_ session: Session, final: Bool = false) {
        for (handle, kind) in [(session.stdoutReader, StreamKind.stdout), (session.stderrReader, StreamKind.stderr)] {
            guard let handle else { continue }
            for _ in 0 ..< (final ? 16 : 1) {
                guard let data = try? handle.read(upToCount: 65536), !data.isEmpty else { break }
                session.log.append(data, stream: kind)
            }
            if final { try? handle.close() }
        }
        if final {
            session.stdoutReader = nil; session.stderrReader = nil
            session.log.flush()
        }
    }

    private func prepareUpdateStop(_ package: String) {
        updateStops.insert(package)
        sessions[package]?.stopRequested = true
        sessions[package]?.relaunchAfterExit = false
    }

    private func acceptCompletions(_ reply: HostReply) {
        for event in reply.finished ?? [] {
            guard let session = sessions[event.package], session.launchID == event.launch_id else { continue }
            drainHostLogs(session, final: true)
            session.inboxWatcher?.cancel()
            EmbedHub.shared.end(event.package)
            session.state = session.stopRequested ? .finished("Stopped") :
                (event.exit_status == 0 ? .finished("Closed") : .failed("The runtime exited with code \(event.exit_status)."))
            session.log.appendNotice("[\(session.state.summary)]")
            if event.exit_status != 0 && !session.stopRequested { present(failure: "App quit unexpectedly", "See its console for details.") }
            if session.relaunchAfterExit { launch(event.package) }
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
                await launchAndWait(package, requester: session)
            },
            remove: { [unowned self] package in
                remove(package)
                guard removingPackages.contains(package) else { return entry(package) == nil }
                return await withCheckedContinuation { continuation in
                    removalWaiters[package, default: []].append(continuation)
                }
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
        guard let session = sessions[package], session.isRunning, !session.stopRequested else { return }
        session.stopRequested = true
        if session.launchID != nil {
            hostCommands.append(.stop(session))
            ensureHostPump()
        }
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
