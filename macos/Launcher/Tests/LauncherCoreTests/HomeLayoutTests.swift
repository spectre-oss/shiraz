// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import Testing
@testable import LauncherCore

@Suite struct HomeLayoutTests {
    private func ids(_ layout: HomeLayout) -> [String] { layout.items.map(\.id) }

    @Test func reconcileAppendsNewAndDropsMissing() {
        var layout = HomeLayout(items: [.app("a.b.c"), .app("gone.app"), .app("x.y")])
        layout = layout.reconciled(with: ["x.y", "a.b.c", "new.app"])
        #expect(ids(layout) == ["app:a.b.c", "app:x.y", "app:new.app"])
    }

    @Test func reconcileDissolvesFolderLeftWithOneApp() {
        let folder = HomeFolder(name: "Games", apps: ["g.one", "g.two"])
        let layout = HomeLayout(items: [.app("a.a"), .folder(folder)]).reconciled(with: ["a.a", "g.two"])
        #expect(layout.items == [.app("a.a"), .app("g.two")])
    }

    @Test func reconcileRemovesDuplicates() {
        let folder = HomeFolder(name: "F", apps: ["a.a", "b.b", "c.c"])
        let layout = HomeLayout(items: [.app("a.a"), .folder(folder)]).reconciled(with: ["a.a", "b.b", "c.c"])
        #expect(layout.packages == ["a.a", "b.b", "c.c"])
        #expect(layout.folder(containing: "a.a") == nil)
    }

    @Test func reconcileKeepsDockOrderAndFollowsFavorites() {
        let layout = HomeLayout(dock: ["b.b", "gone.x", "a.a"])
            .reconciled(with: ["a.a", "b.b", "c.c"], favorites: ["a.a", "c.c", "b.b"])
        #expect(layout.dock == ["b.b", "a.a", "c.c"])
        let unfavorited = layout.reconciled(with: ["a.a", "b.b", "c.c"], favorites: ["a.a"])
        #expect(unfavorited.dock == ["a.a"])
    }

    @Test func moveBeforeAndAfter() {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c")])
        layout.move("app:c.c", before: "app:a.a")
        #expect(ids(layout) == ["app:c.c", "app:a.a", "app:b.b"])
        layout.move("app:c.c", after: "app:b.b")
        #expect(ids(layout) == ["app:a.a", "app:b.b", "app:c.c"])
        layout.move("app:a.a", before: nil)
        #expect(ids(layout) == ["app:b.b", "app:c.c", "app:a.a"])
        layout.move("app:a.a", before: "app:a.a")
        #expect(ids(layout) == ["app:b.b", "app:c.c", "app:a.a"])
    }

    @Test func combineMakesFolderInTargetsPlace() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c")])
        let combined = layout.combine("app:a.a", onto: "app:c.c")
        let id = try #require(combined)
        #expect(layout.items.count == 2)
        #expect(layout.items[0] == .app("b.b"))
        let folder = try #require(layout.folder(id: id))
        #expect(folder.apps == ["c.c", "a.a"])
        #expect(folder.name == "Folder")
    }

    @Test func combineOntoFolderJoinsIt() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c")])
        let combined = layout.combine("app:a.a", onto: "app:b.b")
        let id = try #require(combined)
        let folderID = "folder:\(id.uuidString)"
        let joined = layout.combine("app:c.c", onto: folderID)
        #expect(joined == id)
        #expect(layout.folder(id: id)?.apps == ["b.b", "a.a", "c.c"])
        #expect(layout.items.count == 1)
    }

    @Test func foldersCannotBeDroppedIntoAnything() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c")])
        let combined = layout.combine("app:a.a", onto: "app:b.b")
        let id = try #require(combined)
        let intoApp = layout.combine("folder:\(id.uuidString)", onto: "app:c.c")
        let ontoSelf = layout.combine("app:c.c", onto: "app:c.c")
        #expect(intoApp == nil && ontoSelf == nil)
        #expect(layout.items.count == 2)
    }

    @Test func removeFromFolderPlacesAppAfterItAndDissolves() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c"), .app("d.d")])
        let combined = layout.combine("app:a.a", onto: "app:b.b")
        let id = try #require(combined)
        layout.combine("app:c.c", onto: "folder:\(id.uuidString)")
        layout.removeFromFolder("a.a")
        #expect(layout.folder(id: id)?.apps == ["b.b", "c.c"])
        #expect(layout.packages == ["b.b", "c.c", "a.a", "d.d"])
        layout.removeFromFolder("b.b")
        #expect(layout.items == [.app("c.c"), .app("b.b"), .app("a.a"), .app("d.d")])
    }

    @Test func renameIsTrimmedAndBounded() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b")])
        let combined = layout.combine("app:a.a", onto: "app:b.b")
        let id = try #require(combined)
        layout.rename(folder: id, to: "  Games\nand more  ")
        #expect(layout.folder(id: id)?.name == "Games and more")
        layout.rename(folder: id, to: "   ")
        #expect(layout.folder(id: id)?.name == "Folder")
        layout.rename(folder: id, to: String(repeating: "x", count: 200))
        #expect(layout.folder(id: id)?.name.count == HomeLayout.folderNameLimit)
    }

    @Test func moveInFolderAndDock() throws {
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b"), .app("c.c")], dock: ["a.a", "b.b", "c.c"])
        let combined = layout.combine("app:a.a", onto: "app:b.b")
        let id = try #require(combined)
        layout.combine("app:c.c", onto: "folder:\(id.uuidString)")
        layout.moveInFolder(id, "c.c", before: "b.b")
        #expect(layout.folder(id: id)?.apps == ["c.c", "b.b", "a.a"])
        layout.moveInDock("c.c", before: "a.a")
        #expect(layout.dock == ["c.c", "a.a", "b.b"])
        layout.moveInDock("c.c", before: nil)
        #expect(layout.dock == ["a.a", "b.b", "c.c"])
    }

    @Test func searchFlattensFolders() throws {
        var layout = HomeLayout(items: [.app("org.osmand"), .app("org.calc"), .app("net.map")])
        #expect(layout.combine("app:net.map", onto: "app:org.osmand") != nil)
        let names = ["org.osmand": "OsmAnd~", "org.calc": "Calculator", "net.map": "Maps"]
        #expect(layout.matching("map", name: { names[$0] ?? $0 }) == ["net.map"])
        #expect(layout.matching("org", name: { names[$0] ?? $0 }) == ["org.osmand", "org.calc"])
        #expect(layout.matching(" ", name: { $0 }).count == 3)
    }

    @Test func decodeFallsBackToEmpty() throws {
        #expect(HomeLayout.decode(nil) == HomeLayout())
        #expect(HomeLayout.decode(Data("not json".utf8)) == HomeLayout())
        var future = HomeLayout(items: [.app("a.a")])
        future.version = HomeLayout.version + 1
        #expect(HomeLayout.decode(try future.encoded()) == HomeLayout())
    }

    @Test func roundTripsThroughStore() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "home-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LibraryStore(directory: directory)
        #expect(store.loadHome() == HomeLayout())
        var layout = HomeLayout(items: [.app("a.a"), .app("b.b")], dock: ["b.b"], wallpaper: .builtIn("dusk"))
        layout.combine("app:a.a", onto: "app:b.b", name: "Pair")
        try store.saveHome(layout)
        #expect(store.loadHome() == layout)
    }

    @Test func wallpaperImportCopiesAndValidates() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "wall-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let store = LibraryStore(directory: directory.appending(path: "lib"))
        let picture = directory.appending(path: "beach.PNG")
        try Data([0x89, 0x50, 0x4e, 0x47]).write(to: picture)
        let first = try store.importWallpaper(from: picture)
        let url = try #require(store.wallpaperURL(first))
        #expect(url.pathExtension == "png")
        #expect(FileManager.default.fileExists(atPath: picture.path))
        let second = try store.importWallpaper(from: picture)
        #expect(store.wallpaperURL(first) == nil)
        #expect(store.wallpaperURL(second) != nil)

        let text = directory.appending(path: "notes.txt")
        try Data("x".utf8).write(to: text)
        #expect(throws: LibraryError.self) { try store.importWallpaper(from: text) }
        #expect(store.wallpaperURL(.image("../../etc/passwd")) == nil)
        #expect(store.wallpaperURL(.builtIn("contours")) == nil)
    }
}

@Suite struct ContinueRowTests {
    private func app(_ package: String, played: TimeInterval?) throws -> AppEntry {
        let report = try InspectReport.decode(Data("""
        {"apk_sha256": "a", "native_abis": [], "manifest": {"package": "\(package)", "permissions": []}}
        """.utf8))
        var entry = AppEntry(report: report)
        entry.lastLaunchedAt = played.map { Date(timeIntervalSince1970: $0) }
        return entry
    }

    @Test func runningFirstThenRecentNeverPlayedLeftOut() throws {
        let apps = [try app("a.old", played: 10), try app("b.new", played: 30), try app("c.never", played: nil),
                    try app("d.run", played: 5), try app("e.mid", played: 20)]
        let picked = ContinueRow.pick(apps, running: ["d.run"]).map(\.packageName)
        #expect(picked == ["d.run", "b.new", "e.mid"])
        #expect(ContinueRow.pick(apps, running: [], limit: 0).isEmpty)
    }
}

@Suite struct HostIdentityTests {
    @Test func matchesRuntimeBundleIdentifier() {
        // python3: "org.shiraz.apk." + hashlib.sha256(b"net.osmand.plus").hexdigest()[:24]
        #expect(HostIdentity.bundleIdentifier(for: "net.osmand.plus") == "org.shiraz.apk.a8f94bfabe6ff349dfa3bba9")
    }
}
