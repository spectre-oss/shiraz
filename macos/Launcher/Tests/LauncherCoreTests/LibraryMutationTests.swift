// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import Testing
@testable import LauncherCore

@Suite struct LibraryMutationTests {
    @Test func failedPublicationRetainsCandidateForExplicitRetry() async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "library-recovery-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(directory: root)
        try FileManager.default.createDirectory(at: store.appsDirectory, withIntermediateDirectories: true)
        let source = root.appending(path: "input.apk")
        let bytes = Data([0x50, 0x4b, 0x03, 0x04]) + Data(repeating: 0, count: 32)
        try bytes.write(to: source)
        let report = try InspectReport.decode(Data("""
        {"apk_sha256":"fixture","manifest":{"package":"org.example.app"}}
        """.utf8))
        let destination = store.appsDirectory.appending(path: "org.example.app")
        let obstacle = Data("existing non-directory".utf8)
        try obstacle.write(to: destination)
        var recovery: URL?
        do {
            _ = try await store.importAPK(from: source) { _ in report }
            Issue.record("Publication over a non-directory succeeded")
        } catch LibraryError.publicationFailed(let package, let path, _) {
            #expect(package == "org.example.app")
            recovery = URL(fileURLWithPath: path)
        }
        let retained = try #require(recovery)
        #expect(try Data(contentsOf: retained) == bytes)
        #expect(try Data(contentsOf: destination) == obstacle)
        #expect(store.load().isEmpty)
        try FileManager.default.removeItem(at: destination)
        let entry = try await store.importAPK(from: retained) { _ in report }
        #expect(entry.sha256 == "fixture")
        #expect(store.load().first?.sha256 == "fixture")
        #expect(try Data(contentsOf: store.apkURL(for: entry.packageName)) == bytes)
        // Explicit retry does not delete the recovery source supplied by caller.
        #expect(try Data(contentsOf: retained) == bytes)
    }

    @Test func importExcludesOtherImportsAndRemovalAcrossAwait() async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "library-lock-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appending(path: "input.apk")
        try (Data([0x50, 0x4b, 0x03, 0x04]) + Data(repeating: 0, count: 32)).write(to: source)
        let report = try InspectReport.decode(Data("""
        {"apk_sha256":"fixture","manifest":{"package":"org.example.app"}}
        """.utf8))
        let store = LibraryStore(directory: root.appending(path: "library"))
        let peer = LibraryStore(directory: store.directory)
        _ = try await store.importAPK(from: source) { _ in report }
        // Abort after exercising contention: throwing must release the lock too.
        do {
            _ = try await store.importAPK(from: source) { _ in
                await #expect(throws: LibraryError.busy) {
                    try await peer.importAPK(from: source) { _ in
                        Issue.record("Competing import reached validation")
                        return report
                    }
                }
                #expect(throws: LibraryError.busy) { try peer.remove("org.example.app") }
                let process = try await ProcessRunner.run(
                    executable: try #require(RuntimeLocation.detectPython()),
                    arguments: ["-c", """
                    import fcntl, sys
                    with open(sys.argv[1], 'a') as lock:
                        try:
                            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                        except BlockingIOError:
                            sys.exit(0)
                        sys.exit(1)
                    """, store.directory.appending(path: ".install.lock").path],
                    environment: ProcessInfo.processInfo.environment, workingDirectory: root)
                #expect(process.status.succeeded, "Another process bypassed the lock")
                #expect(store.load().count == 1)
                throw LibraryError.inspectFailed("fixture rejection")
            }
            Issue.record("Rejected import unexpectedly succeeded")
        } catch {
            #expect(error as? LibraryError == .inspectFailed("fixture rejection"), "Unexpected error: \(error)")
        }
        let enriched = try await peer.importAPK(from: source, enrich: { entry in
            #expect(store.load().count == 1)
            #expect(throws: LibraryError.busy) { try store.remove("org.example.app") }
            await #expect(throws: LibraryError.busy) {
                try await store.importAPK(from: source) { _ in report }
            }
            var result = entry
            result.label = "Enriched"
            do { try peer.save(result) } catch { Issue.record("Enrichment save failed: \(error)") }
            return result
        }) { _ in report }
        #expect(enriched.label == "Enriched")
        #expect(store.load().first?.label == "Enriched")
        let metadata = Data("metadataVersion=2\n".utf8)
        try store.saveMetadata(metadata, for: "org.example.app")
        await store.extractIcon(for: "org.example.app") { _, _ in
            #expect(throws: LibraryError.busy) { try peer.remove("org.example.app") }
            await peer.extractIcon(for: "org.example.app") { _, _ in Issue.record("Busy refresh ran") }
            let tools = RuntimeTools(runtime: RuntimeLocation(root: root, python: URL(fileURLWithPath: "/nonexistent")))
            await tools.writeMetadata(for: "org.example.app", installer: nil, store: peer)
            #expect(!(await tools.refreshMissingLabel(for: "org.example.app", store: peer)))
            #expect(try Data(contentsOf: store.metadataURL(for: "org.example.app")) == metadata)
            throw LibraryError.inspectFailed("controlled icon failure")
        }
        #expect(store.iconError(for: "org.example.app") == "controlled icon failure")
        let unavailable = RuntimeTools(runtime: RuntimeLocation(root: root, python: URL(fileURLWithPath: "/nonexistent")))
        await unavailable.refreshMissingMetadata(for: "org.example.app", store: store)
        #expect(try Data(contentsOf: store.metadataURL(for: "org.example.app")) == metadata)
        do {
            try await store.remove("org.example.app", after: { entry in
                #expect(entry.packageName == "org.example.app")
                #expect(throws: LibraryError.busy) { try peer.remove("org.example.app") }
                await #expect(throws: LibraryError.busy) {
                    try await peer.importAPK(from: source) { _ in report }
                }
                throw LibraryError.inspectFailed("retirement failed")
            })
            Issue.record("Failed retirement removed the app")
        } catch {
            #expect(error as? LibraryError == .inspectFailed("retirement failed"))
        }
        #expect(store.load().count == 1)
        #expect(try Data(contentsOf: store.apkURL(for: "org.example.app")) == Data(contentsOf: source))
        try await peer.remove("org.example.app", after: { entry in
            #expect(entry.label == "Enriched")
            #expect(throws: LibraryError.busy) { try store.remove("org.example.app") }
        })
        await store.extractIcon(for: "org.example.app") { _, _ in Issue.record("Removed package refreshed") }
        try await store.withInstalledPackage("org.example.app") { Issue.record("Removed package metadata refreshed") }
        #expect(!FileManager.default.fileExists(atPath: store.iconDirectory(for: "org.example.app").path))
        #expect(peer.load().isEmpty)
    }
}
