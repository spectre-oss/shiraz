// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// A folder on the home screen: two or more library apps under one name.
public struct HomeFolder: Codable, Hashable, Sendable, Identifiable {
    public var id: UUID
    public var name: String
    public var apps: [String]

    public init(id: UUID = UUID(), name: String, apps: [String]) {
        self.id = id
        self.name = name
        self.apps = apps
    }
}

/// One place in the home grid.
public enum HomeItem: Codable, Hashable, Sendable, Identifiable {
    case app(String)
    case folder(HomeFolder)

    /// Stable across edits: the package, or the folder's UUID.
    public var id: String {
        switch self {
        case .app(let package): "app:\(package)"
        case .folder(let folder): "folder:\(folder.id.uuidString)"
        }
    }

    public var packages: [String] {
        switch self {
        case .app(let package): [package]
        case .folder(let folder): folder.apps
        }
    }
}

/// The home screen background.
public enum Wallpaper: Codable, Hashable, Sendable {
    /// Drawn by the launcher (see `Wallpaper.builtIns`).
    case builtIn(String)
    /// A picture the user chose, copied into the library (LibraryStore.wallpaperDirectory).
    case image(String)

    public static let builtIns = ["contours", "dusk", "plain"]
    public static let `default` = Wallpaper.builtIn("contours")
}

/// The user's arrangement of the home screen: grid order, folders, dock
/// order and wallpaper. Which apps exist comes from the library; this only
/// remembers where they go. `reconciled(with:)` keeps the two in step when
/// apps are added or removed behind the layout's back.
public struct HomeLayout: Codable, Hashable, Sendable {
    public static let version = 1
    /// More than this does not fit the dock in the narrowest window.
    public static let dockLimit = 6
    public static let folderNameLimit = 40

    public var version: Int
    public var items: [HomeItem]
    /// Order of the dock. Membership is the app's favorite flag.
    public var dock: [String]
    public var wallpaper: Wallpaper

    public init(items: [HomeItem] = [], dock: [String] = [], wallpaper: Wallpaper = .default) {
        version = Self.version
        self.items = items
        self.dock = dock
        self.wallpaper = wallpaper
    }

    /// Every package placed in the grid, folders included, in order.
    public var packages: [String] { items.flatMap(\.packages) }

    public func item(id: String) -> HomeItem? { items.first { $0.id == id } }

    public func folder(id: UUID) -> HomeFolder? {
        for case .folder(let folder) in items where folder.id == id { return folder }
        return nil
    }

    /// The folder holding `package`, if any.
    public func folder(containing package: String) -> HomeFolder? {
        for case .folder(let folder) in items where folder.apps.contains(package) { return folder }
        return nil
    }

    // MARK: Library changes

    /// Drop packages that left the library (and duplicates), add new ones at
    /// the end in the order given, and dissolve folders left with one app.
    /// `favorites` is the dock's membership; their stored order is kept and
    /// new favorites join at the end.
    public func reconciled(with library: [String], favorites: [String] = []) -> HomeLayout {
        let known = Set(library)
        var seen = Set<String>()
        var kept: [HomeItem] = []
        for item in items {
            switch item {
            case .app(let package):
                if known.contains(package), seen.insert(package).inserted { kept.append(item) }
            case .folder(var folder):
                folder.apps = folder.apps.filter { known.contains($0) && seen.insert($0).inserted }
                if let only = folder.apps.first, folder.apps.count == 1 {
                    kept.append(.app(only))
                } else if !folder.apps.isEmpty {
                    kept.append(.folder(folder))
                }
            }
        }
        for package in library where seen.insert(package).inserted { kept.append(.app(package)) }

        let wanted = Set(favorites)
        var docked: [String] = []
        for package in dock where wanted.contains(package) && !docked.contains(package) { docked.append(package) }
        for package in favorites where !docked.contains(package) { docked.append(package) }

        var next = self
        next.items = kept
        next.dock = docked
        return next
    }

    // MARK: Arranging

    /// Move the item `id` to sit just before `target` (or to the end when
    /// `target` is nil). Moving onto itself changes nothing.
    public mutating func move(_ id: String, before target: String?) {
        guard id != target, let from = items.firstIndex(where: { $0.id == id }) else { return }
        let item = items.remove(at: from)
        if let target, let to = items.firstIndex(where: { $0.id == target }) {
            items.insert(item, at: to)
        } else {
            items.append(item)
        }
    }

    /// Move `id` to just after `target`.
    public mutating func move(_ id: String, after target: String) {
        guard id != target, items.contains(where: { $0.id == target }) else { return }
        guard let index = items.firstIndex(where: { $0.id == target }) else { return }
        let next = index + 1 < items.count ? items[index + 1].id : nil
        if next == id { return }
        move(id, before: next)
    }

    /// Drop the app item `id` onto `target`. Onto an app it makes a folder in
    /// the target's place; onto a folder it joins that folder. A folder
    /// cannot be dropped into anything. Returns the folder that now holds the
    /// app, or nil when nothing changed.
    @discardableResult
    public mutating func combine(_ id: String, onto target: String, name: String = "Folder") -> UUID? {
        guard id != target,
              let from = items.firstIndex(where: { $0.id == id }),
              case .app(let package) = items[from],
              let to = items.firstIndex(where: { $0.id == target }) else { return nil }
        switch items[to] {
        case .app(let other):
            let folder = HomeFolder(name: Self.clean(name), apps: [other, package])
            items[to] = .folder(folder)
            items.remove(at: from)
            return folder.id
        case .folder(var folder):
            folder.apps.append(package)
            items[to] = .folder(folder)
            items.remove(at: from)
            return folder.id
        }
    }

    /// Take `package` out of its folder and place it right after the folder.
    /// A folder left with one app becomes that app.
    public mutating func removeFromFolder(_ package: String) {
        guard let index = items.firstIndex(where: {
            if case .folder(let folder) = $0 { folder.apps.contains(package) } else { false }
        }), case .folder(var folder) = items[index] else { return }
        folder.apps.removeAll { $0 == package }
        if folder.apps.count == 1, let only = folder.apps.first {
            items[index] = .app(only)
        } else if folder.apps.isEmpty {
            items.remove(at: index)
            items.insert(.app(package), at: index)
            return
        } else {
            items[index] = .folder(folder)
        }
        items.insert(.app(package), at: index + 1)
    }

    /// Reorder inside a folder: `package` goes before `target` (or last).
    public mutating func moveInFolder(_ folderID: UUID, _ package: String, before target: String?) {
        guard let index = items.firstIndex(where: { if case .folder(let f) = $0 { f.id == folderID } else { false } }),
              case .folder(var folder) = items[index],
              package != target, let from = folder.apps.firstIndex(of: package) else { return }
        folder.apps.remove(at: from)
        if let target, let to = folder.apps.firstIndex(of: target) {
            folder.apps.insert(package, at: to)
        } else {
            folder.apps.append(package)
        }
        items[index] = .folder(folder)
    }

    public mutating func rename(folder folderID: UUID, to name: String) {
        guard let index = items.firstIndex(where: { if case .folder(let f) = $0 { f.id == folderID } else { false } }),
              case .folder(var folder) = items[index] else { return }
        folder.name = Self.clean(name)
        items[index] = .folder(folder)
    }

    /// Dock reorder: `package` goes before `target` (or last).
    public mutating func moveInDock(_ package: String, before target: String?) {
        guard package != target, let from = dock.firstIndex(of: package) else { return }
        dock.remove(at: from)
        if let target, let to = dock.firstIndex(of: target) {
            dock.insert(package, at: to)
        } else {
            dock.append(package)
        }
    }

    /// Trimmed, one line, bounded; "Folder" when nothing is left.
    static func clean(_ name: String) -> String {
        let line = name.split(whereSeparator: \.isNewline).joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
        return line.isEmpty ? "Folder" : String(line.prefix(folderNameLimit))
    }

    // MARK: Search

    /// Grid packages, folders flattened, whose name or package contains `query`.
    public func matching(_ query: String, name: (String) -> String) -> [String] {
        let query = query.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return packages }
        return packages.filter {
            name($0).localizedCaseInsensitiveContains(query) || $0.localizedCaseInsensitiveContains(query)
        }
    }

    // MARK: Storage

    /// A missing, unreadable or newer-format file gives an empty layout;
    /// `reconciled(with:)` then lays the library out by name.
    public static func decode(_ data: Data?) -> HomeLayout {
        guard let data, let layout = try? JSONDecoder().decode(HomeLayout.self, from: data),
              layout.version <= version else { return HomeLayout() }
        return layout
    }

    public func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(self)
    }
}

/// The Continue row: what the user was doing last.
public enum ContinueRow {
    /// Running apps first (latest launch first), then the most recently
    /// played, up to `limit`. Apps never launched are not included.
    public static func pick(_ apps: [AppEntry], running: Set<String>, limit: Int = 3) -> [AppEntry] {
        let played = apps.filter { running.contains($0.packageName) || $0.lastLaunchedAt != nil }
        return played.sorted { a, b in
            let ra = running.contains(a.packageName), rb = running.contains(b.packageName)
            if ra != rb { return ra }
            return (a.lastLaunchedAt ?? .distantPast) > (b.lastLaunchedAt ?? .distantPast)
        }
        .prefix(max(0, limit))
        .map { $0 }
    }
}
