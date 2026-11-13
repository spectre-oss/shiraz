// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import LauncherCore
import Observation

/// The F-Droid catalog page: the verified index from `shiraz fdroid sync`,
/// filters, and installs through `shiraz fdroid fetch` into the library.
@MainActor @Observable
final class CatalogModel {
    enum Phase: Equatable {
        case idle
        case loading
        case loaded
        case failed(String)
    }

    enum Scope: String, CaseIterable, Identifiable {
        case runnable, all, library
        var id: String { rawValue }
        var title: String {
            switch self {
            case .runnable: "Fits This Mac"
            case .all: "All"
            case .library: "In Library"
            }
        }
    }

    enum Sort: String, CaseIterable, Identifiable {
        case name, updated, added
        var id: String { rawValue }
        var title: String {
            switch self {
            case .name: "Name"
            case .updated: "Recently Updated"
            case .added: "Newest"
            }
        }
    }

    enum InstallStep: Equatable { case downloading, adding }

    /// A catalog younger than this is reused when the page opens.
    static let refreshInterval = 6 * 60 * 60

    private(set) var catalog: Catalog?
    private(set) var phase: Phase = .idle
    private(set) var isRefreshing = false
    private(set) var catalogDate: Date?
    private(set) var installing: [String: InstallStep] = [:]
    var scope: Scope = .runnable
    var sort: Sort = .name
    var category: String?

    private let launcher: LauncherModel

    init(launcher: LauncherModel) { self.launcher = launcher }

    // MARK: Loading

    /// Show what is on disk at once, then refresh it when it is stale.
    func open() {
        guard phase == .idle else { return }
        phase = .loading
        let url = launcher.store.catalogURL
        Task {
            if let cached = await Self.read(url) {
                apply(cached, from: url)
            }
            await refresh(maxAge: Self.refreshInterval)
        }
    }

    /// Download and verify the index again. `maxAge` 0 always downloads.
    func refresh(maxAge: Int = 0) async {
        guard !isRefreshing else { return }
        guard let runtime = launcher.runtimeStatus.location else {
            if case .unavailable(let reason) = launcher.runtimeStatus { fail("Runtime unavailable. \(reason)") }
            return
        }
        isRefreshing = true
        defer { isRefreshing = false }
        let store = launcher.store
        do {
            _ = try await RuntimeTools(runtime: runtime).output(
                RuntimeCommand.fdroidSync(cache: store.catalogDirectory, maxAge: maxAge),
                what: "Catalog update", timeout: 300)
            guard let fresh = await Self.read(store.catalogURL) else {
                throw LibraryError.inspectFailed("The downloaded catalog could not be read.")
            }
            apply(fresh, from: store.catalogURL)
        } catch {
            fail(error.localizedDescription)
        }
    }

    private func apply(_ fresh: Catalog, from url: URL) {
        catalog = fresh
        catalogDate = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
        phase = .loaded
    }

    /// With a catalog on screen a failed refresh is a toast; without one it is the page.
    private func fail(_ message: String) {
        if catalog == nil {
            phase = .failed(message)
        } else {
            launcher.present(failure: "Could not update the F-Droid catalog", message)
        }
    }

    private nonisolated static func read(_ url: URL) async -> Catalog? {
        await Task.detached(priority: .userInitiated) {
            guard let data = try? Data(contentsOf: url, options: .mappedIfSafe) else { return nil }
            return try? Catalog.decode(data)
        }.value
    }

    // MARK: Browsing

    func visibleApps(search: String) -> [CatalogApp] {
        guard let catalog else { return [] }
        let library = launcher.apps
        let filtered = catalog.apps.filter { app in
            switch scope {
            case .runnable: guard app.compatible else { return false }
            case .all: break
            case .library: guard app.status(in: library) != .notInLibrary else { return false }
            }
            if let category, !app.categories.contains(category) { return false }
            return app.matches(search)
        }
        switch sort {
        case .name: return filtered
        case .updated: return filtered.sorted { ($0.lastUpdated ?? .distantPast) > ($1.lastUpdated ?? .distantPast) }
        case .added: return filtered.sorted { ($0.added ?? .distantPast) > ($1.added ?? .distantPast) }
        }
    }

    func status(_ app: CatalogApp) -> CatalogStatus { app.status(in: launcher.apps) }

    /// Push an app's catalog page on the detail stack.
    func showDetails(_ app: CatalogApp) { launcher.routes.append(.catalog(app)) }

    func openInLibrary(_ package: String) {
        launcher.show(package)
    }

    // MARK: Installing

    func install(_ app: CatalogApp) {
        let package = app.packageName
        guard installing[package] == nil else { return }
        guard app.compatible else {
            return launcher.present(failure: "\(app.name) cannot run in Shiraz", app.reason)
        }
        guard let runtime = launcher.runtimeStatus.location else {
            if case .unavailable(let reason) = launcher.runtimeStatus { launcher.present(failure: "Runtime unavailable", reason) }
            return
        }
        let wasInLibrary = status(app) != .notInLibrary
        installing[package] = .downloading
        let store = launcher.store
        Task {
            defer { installing[package] = nil }
            let directory: URL
            do {
                directory = try store.makeDownloadDirectory()
            } catch {
                return launcher.present(failure: "Could not install \(app.name)", error.localizedDescription)
            }
            defer { try? FileManager.default.removeItem(at: directory) }
            do {
                let output = try await RuntimeTools(runtime: runtime).output(
                    RuntimeCommand.fdroidFetch(package: package, cache: store.catalogDirectory, output: directory),
                    what: "Download", timeout: 1800)
                let fetched = try JSONDecoder().decode(FetchedAPK.self, from: output)
                let apk = URL(fileURLWithPath: fetched.path).resolvingSymlinksInPath()
                guard fetched.packageName == package,
                      apk.deletingLastPathComponent().path == directory.resolvingSymlinksInPath().path else {
                    throw LibraryError.inspectFailed("The runtime reported an unexpected download.")
                }
                installing[package] = .adding
                var check: (@Sendable (InspectReport) async throws -> Void)?
                if wasInLibrary, let installed = launcher.entry(package) {
                    let signers = try await launcher.installedSigners(package)
                    check = { report in
                        if let refusal = UpdatePolicy.refusal(
                            installed: installed, installedSigners: signers, candidate: AppEntry(report: report), catalog: app) {
                            throw LibraryError.inspectFailed(refusal)
                        }
                    }
                }
                let (added, failures) = await launcher.addAPKs([apk], expectedPackage: package, check: check)
                if let entry = added.first {
                    launcher.inform(wasInLibrary ? "Updated \(entry.displayName)" : "Added \(entry.displayName)",
                                    "From F-Droid, SHA-256 checked against the signed index.")
                } else {
                    launcher.present(failure: "Could not add \(app.name)", failures.joined(separator: "\n"))
                }
            } catch {
                launcher.present(failure: "Could not download \(app.name)", error.localizedDescription)
            }
        }
    }
}
