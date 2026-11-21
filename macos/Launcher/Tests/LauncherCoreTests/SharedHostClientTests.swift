// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import Testing
@testable import LauncherCore

@Suite struct SharedHostClientTests {
    @Test(.enabled(if: ProcessInfo.processInfo.environment["SHIRAZ_TEST_SHARED_HOST_REGISTRY"] != nil))
    func signedAppLaunchCompletionAndStop() async throws {
        let env = ProcessInfo.processInfo.environment
        let registry = URL(fileURLWithPath: try #require(env["SHIRAZ_TEST_SHARED_HOST_REGISTRY"]))
        let package = try #require(env["SHIRAZ_TEST_SHARED_HOST_PACKAGE"])
        let digest = try #require(env["SHIRAZ_TEST_SHARED_HOST_SHA256"])
        let root = try #require(RuntimeLocation.detectRoot(executable: nil,
                                                           currentDirectory: URL(filePath: #filePath).deletingLastPathComponent()))
        let runtime = RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython()))
        let library = FileManager.default.temporaryDirectory.appending(path: "host-library-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: library.appending(path: "apps"), withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: library) }
        let store = LibraryStore(directory: library)
        let tools = RuntimeTools(runtime: runtime)
        var entry = try await tools.add(apk: registry.appending(path: "apks/\(digest).apk"), to: store)
        entry.settings = AppSettings(width: 480, height: 800, logLevel: .debug, timeoutSeconds: 20)
        try store.save(entry)
        let host = try await SharedHostClient.connect(runtime: runtime, registry: store.registryDirectory, library: library)
        do {
            let imported = try await host.identity(package: package)
            #expect(imported.ok == true && imported.found == true)
            #expect(imported.identity?.sha256 == digest)
            #expect(try await host.status().running == [])
            let launch = try await tools.launchRegistered(package: package, store: store, host: host)
            #expect(launch.ok == true)
            let inbox = try #require(launch.inbox)
            #expect(FileManager.default.fileExists(atPath: inbox))
            let deadline = Date(timeIntervalSinceNow: 30)
            var completed = false
            while Date() < deadline {
                let reply = try await host.status()
                if let event = reply.finished?.first {
                    #expect(event.package == package && event.launch_id == launch.launch_id)
                    #expect(event.exit_status == 0)
                    // Broker-owned jobs have separate process logs. This event closes the app process.
                    let appLog = try String(contentsOfFile: event.stdout, encoding: .utf8)
                    for marker in ["BACKGROUND_APK_ACTIVITY_DESTROYED", "SHIRAZ_BACKGROUND_ENTERED",
                                   "BACKGROUND_APK_WORK_AFTER_ACTIVITY", "BACKGROUND_APK_SERVICE_DESTROYED",
                                   "SHIRAZ_RUNTIME_CLEANUP_COMPLETE"] {
                        #expect(appLog.contains(marker))
                    }
                    #expect(try String(contentsOfFile: event.stdout, encoding: .utf8).contains("BACKGROUND_APK_INBOX=" + inbox))
                    #expect(!FileManager.default.fileExists(atPath: inbox))
                    completed = true
                    break
                }
                try await Task.sleep(for: .milliseconds(50))
            }
            #expect(completed)
            var stale = entry
            stale.sha256 = String(repeating: "0", count: 64)
            try store.save(stale)
            await #expect(throws: (any Error).self) {
                try await tools.launchRegistered(package: package, store: store, host: host)
            }
            #expect(try await host.status().running == [])
            try store.save(entry)
            let second = try await tools.launchRegistered(package: package, store: store, host: host)
            let token = try #require(second.launch_id)
            let secondInbox = try #require(second.inbox)
            #expect(secondInbox != inbox)
            #expect(token != launch.launch_id)
            let stopped = try await host.stop(package: package, launchID: token)
            #expect(stopped.stopped == true)
            _ = try await host.status()
            let lookup = try await host.identity(package: package)
            #expect(lookup.ok == true && lookup.found == true)
            let enrollment = try #require(lookup.identity)
            #expect(enrollment.package == package && enrollment.sha256 == digest)
            let third = try await tools.launchRegistered(package: package, store: store, host: host)
            #expect(third.ok == true)
            let staleRetirement = try await host.retire(package: package, sha256: String(repeating: "0", count: 64), uid: enrollment.uid)
            #expect(staleRetirement.ok == false)
            let retired = try await host.retire(package: package, sha256: digest, uid: enrollment.uid)
            #expect(retired.ok == true && retired.retired == true)
            let afterRetirement = try await host.status()
            #expect(afterRetirement.running == [])
            #expect(afterRetirement.finished?.first?.launch_id == third.launch_id)
            let missing = try await host.identity(package: package)
            #expect(missing.ok == true && missing.found == false && missing.identity == nil)
            let rejected = try await host.start(package: package, sha256: digest)
            #expect(rejected.ok == false)
            let fourth = try await tools.launchRegistered(package: package, store: store, host: host)
            #expect(fourth.ok == true)
            let current = try #require(try await host.identity(package: package).identity)
            let quiesced = try await host.quiesce(package: package, sha256: current.sha256, uid: current.uid)
            #expect(quiesced.ok == true && quiesced.quiesced == true)
            #expect(try await host.identity(package: package).quiesced == true)
            // Explicit reimport repairs a published generation left quiesced.
            let recovered = try await tools.add(apk: registry.appending(path: "apks/\(digest).apk"), to: store, host: host)
            #expect(recovered.sha256 == digest)
            #expect(try await host.identity(package: package).quiesced == false)
            #expect(try await host.identity(package: package).identity?.uid == current.uid)
            let fifth = try await tools.launchRegistered(package: package, store: store, host: host)
            #expect(fifth.ok == true)
            let updateAPK = URL(fileURLWithPath: try #require(env["SHIRAZ_TEST_SHARED_HOST_UPDATE_APK"]))
            let updated = try await tools.add(apk: updateAPK, to: store, host: host)
            #expect(updated.sha256 != digest && updated.versionCode == "2")
            let updatedIdentity = try await host.identity(package: package)
            #expect(updatedIdentity.identity?.uid == current.uid)
            #expect(updatedIdentity.identity?.sha256 == updated.sha256)
            #expect(updatedIdentity.quiesced == false)
            #expect(try await host.status().running == [])
            let sixth = try await tools.launchRegistered(package: package, store: store, host: host)
            #expect(sixth.ok == true)
            var updateExecuted = false
            let updateDeadline = Date(timeIntervalSinceNow: 30)
            while Date() < updateDeadline {
                let status = try await host.status()
                if let event = status.finished?.first(where: { $0.launch_id == sixth.launch_id }) {
                    #expect(event.exit_status == 0)
                    // Broker-owned jobs have separate process logs. This event closes the app process.
                    let appLog = try String(contentsOfFile: event.stdout, encoding: .utf8)
                    for marker in ["BACKGROUND_APK_ACTIVITY_DESTROYED", "SHIRAZ_BACKGROUND_ENTERED",
                                   "BACKGROUND_APK_WORK_AFTER_ACTIVITY", "BACKGROUND_APK_SERVICE_DESTROYED",
                                   "SHIRAZ_RUNTIME_CLEANUP_COMPLETE"] {
                        #expect(appLog.contains(marker))
                    }
                    updateExecuted = true
                    break
                }
                try await Task.sleep(for: .milliseconds(50))
            }
            #expect(updateExecuted)
            #expect(try await tools.launchRegistered(package: package, store: store, host: host).ok == true)
            try await tools.removeRegistered(package: package, store: store, host: host, onReply: { _ in })
            #expect(store.load().isEmpty)
            #expect(!FileManager.default.fileExists(atPath: store.apkURL(for: package).path))
            #expect(try await host.identity(package: package).found == false)
            #expect(try await host.status().running == [])
            _ = try await host.shutdown()
            #expect(!FileManager.default.fileExists(atPath: secondInbox))
        } catch { await host.close(); throw error }
    }

    @Test func realHostStatusAndRejectedLaunch() async throws {
        let root = try #require(RuntimeLocation.detectRoot(executable: nil,
                                                           currentDirectory: URL(filePath: #filePath).deletingLastPathComponent()))
        let directory = FileManager.default.temporaryDirectory.appending(path: "host-client-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runtime = RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython()))
        let host = try await SharedHostClient.connect(runtime: runtime, registry: directory)
        await #expect(throws: (any Error).self) {
            try await RuntimeTools(runtime: runtime).launchRegistered(package: "org.missing.app",
                                                                      store: LibraryStore(directory: directory), host: host)
        }
        let unknown = try await host.identity(package: "org.missing.app")
        #expect(unknown.ok == true && unknown.found == false && unknown.identity == nil)
        let status = try await host.status()
        #expect(status.ok == true && status.running == [])
        await #expect(throws: (any Error).self) {
            try await host.retire(package: "org.missing.app", sha256: String(repeating: "a", count: 64), uid: 9999)
        }
        await #expect(throws: (any Error).self) {
            try await host.retire(package: "org.missing.app", sha256: "invalid", uid: 10000)
        }
        let absent = try await host.retire(package: "org.missing.app", sha256: String(repeating: "a", count: 64), uid: 10000)
        #expect(absent.ok == true && absent.retired == false)
        let rejected = try await host.start(package: "org.missing.app", sha256: String(repeating: "a", count: 64))
        #expect(rejected.ok == false && rejected.error != nil)
        let shutdown = try await host.shutdown()
        #expect(shutdown.ok == true)
        await #expect(throws: (any Error).self) { try await host.status() }
    }

    @Test(arguments: ["malformed", "oversized", "timeout", "cancel"])
    func badOrMissingReplyClosesConnection(_ mode: String) async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "host-fake-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let module = root.appending(path: "src/shiraz")
        try FileManager.default.createDirectory(at: module, withIntermediateDirectories: true)
        try """
        import sys
        print('{"ready":true,"protocol":1}', flush=True)
        sys.stdin.buffer.readline()
        mode = '\(mode)'
        if mode == 'malformed':
            print('{"ok":true}', flush=True)
        elif mode == 'oversized':
            print('x' * 140000, flush=True)
        sys.stdin.buffer.read()
        """.write(to: module.appending(path: "__main__.py"), atomically: true, encoding: .utf8)
        let runtime = RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython()))
        let host = try await SharedHostClient.connect(runtime: runtime, registry: root.appending(path: "registry"), timeout: .seconds(2))
        if mode == "cancel" {
            let request = Task { try await host.status() }
            try await Task.sleep(for: .milliseconds(50))
            await #expect(throws: (any Error).self) { try await host.status() }
            request.cancel()
            await #expect(throws: (any Error).self) { try await request.value }
        } else {
            await #expect(throws: (any Error).self) { try await host.status() }
        }
        await #expect(throws: (any Error).self) { try await host.status() }
        await host.close()
    }
}
