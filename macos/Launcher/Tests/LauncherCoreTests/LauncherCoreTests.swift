// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import LauncherCore

private let sampleReport = Data("""
{"schema": 1, "apk_sha256": "abc123", "native_abis": ["arm64-v8a"], "dex": [], "unknown": {"x": 1},
 "manifest": {"package": "net.example.app", "version_code": "7", "version_name": "1.2",
  "min_sdk": "21", "target_sdk": null, "permissions": ["android.permission.INTERNET"], "features": []}}
""".utf8)

@Suite struct InspectReportTests {
    @Test func decodesSubsetAndIgnoresUnknownKeys() throws {
        let report = try InspectReport.decode(sampleReport)
        #expect(report.manifest.package == "net.example.app")
        #expect(report.manifest.versionName == "1.2")
        #expect(report.manifest.targetSdk == nil)
        #expect(report.manifest.permissions == ["android.permission.INTERNET"])
        #expect(report.nativeAbis == ["arm64-v8a"])
        #expect(report.apkSha256 == "abc123")
    }

    @Test func rejectsReportWithoutPackage() {
        #expect(throws: DecodingError.self) {
            try InspectReport.decode(Data(#"{"apk_sha256":"a","manifest":{}}"#.utf8))
        }
    }
}

@Suite struct CompatibilityTests {
    private func entry(abis: [String]) throws -> AppEntry {
        var report = try InspectReport.decode(sampleReport)
        report.nativeAbis = abis
        return AppEntry(report: report)
    }

    @Test func javaOnlyAndArm64AreLaunchable() throws {
        #expect(try entry(abis: []).compatibility == .javaOnly)
        #expect(try entry(abis: ["arm64-v8a", "x86_64"]).compatibility == .arm64)
    }

    @Test func otherAbisAreRejectedWithReason() throws {
        let compatibility = try entry(abis: ["x86_64", "armeabi-v7a"]).compatibility
        #expect(!compatibility.isLaunchable)
        guard case .unsupported(let reason) = compatibility else { Issue.record("expected unsupported"); return }
        #expect(reason.contains("arm64-v8a"))
    }
}

@Suite struct SettingsTests {
    @Test func defaultsAreValid() { #expect(AppSettings().validationError == nil) }

    @Test(arguments: [(0, 640), (360, 0), (9000, 100), (8192, 8192)])
    func rejectsOutOfRangeSizes(width: Int, height: Int) {
        #expect(AppSettings(width: width, height: height).validationError != nil)
    }

    @Test func rejectsNonPositiveTimeout() {
        #expect(AppSettings(timeoutSeconds: 0).validationError != nil)
    }

    @Test func buildsRunArguments() {
        let settings = AppSettings(width: 640, height: 360, logLevel: .debug, timeoutSeconds: 90)
        let arguments = RuntimeCommand.run(apk: URL(fileURLWithPath: "/tmp/a b.apk"), settings: settings)
        #expect(arguments == ["run", "/tmp/a b.apk", "--width", "640", "--height", "360",
                              "--log-level", "DEBUG", "--timeout", "90"])
        #expect(!RuntimeCommand.run(apk: URL(fileURLWithPath: "/x.apk"), settings: AppSettings()).contains("--timeout"))
    }
}

@Suite struct LogBufferTests {
    @Test func joinsPartialLinesAcrossChunks() {
        var log = LogBuffer()
        log.append(Data("hel".utf8), stream: .stdout)
        #expect(log.lines.isEmpty)
        log.append(Data("lo\nwor".utf8), stream: .stdout)
        #expect(log.lines.map(\.text) == ["hello"])
        log.flush()
        #expect(log.lines.map(\.text) == ["hello", "wor"])
    }

    @Test func dropsOldestPastCapacityAndKeepsIDsIncreasing() {
        var log = LogBuffer(capacity: 3)
        for i in 0..<10 { log.append(Data("l\(i)\n".utf8), stream: .stderr) }
        #expect(log.lines.map(\.text) == ["l7", "l8", "l9"])
        #expect(log.lines.allSatisfy { $0.isError })
        #expect(log.lines.map(\.id) == [7, 8, 9])
    }

    @Test func truncatesOverlongLinesAndStripsCarriageReturns() {
        var log = LogBuffer(maxLineLength: 5)
        log.append(Data("abcdefghij\nx\r\n".utf8), stream: .stdout)
        #expect(log.lines.map(\.text) == ["abcde…", "x"])
    }
}

@Suite struct LibraryStoreTests {
    private func makeStore() throws -> LibraryStore {
        let directory = FileManager.default.temporaryDirectory.appending(path: "launcher-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return LibraryStore(directory: directory)
    }

    private func writeFile(_ bytes: [UInt8], in store: LibraryStore, name: String = "x.apk") throws -> URL {
        let url = store.directory.appending(path: name)
        try Data(bytes).write(to: url)
        return url
    }

    private let zipBytes: [UInt8] = [0x50, 0x4b, 0x03, 0x04] + [UInt8](repeating: 0, count: 40)

    @Test(arguments: ["net.example.app", "a.b", "org.x_y.Z9"])
    func acceptsPackageNames(name: String) { #expect(LibraryStore.isValidPackageName(name)) }

    @Test(arguments: ["", "app", "../etc.passwd", "a/b.c", "a..b", ".hidden.x", "a.b/", "a.1b", "a b.c"])
    func rejectsPackageNames(name: String) { #expect(!LibraryStore.isValidPackageName(name)) }

    @Test func precheckRejectsNonZipAndTinyFiles() throws {
        let store = try makeStore()
        defer { try? FileManager.default.removeItem(at: store.directory) }
        let text = try writeFile(Array("hello hello hello hello hello".utf8), in: store, name: "t.apk")
        #expect(throws: LibraryError.self) { try LibraryStore.precheck(text) }
        let tiny = try writeFile([0x50, 0x4b], in: store, name: "tiny.apk")
        #expect(throws: LibraryError.self) { try LibraryStore.precheck(tiny) }
        try LibraryStore.precheck(try writeFile(zipBytes, in: store))
    }

    @Test func importCopiesUnchangedAndKeepsSettingsOnReimport() async throws {
        let store = try makeStore()
        defer { try? FileManager.default.removeItem(at: store.directory) }
        let source = try writeFile(zipBytes, in: store)
        let report = try InspectReport.decode(sampleReport)

        let first = try await store.importAPK(from: source) { _ in report }
        #expect(first.packageName == "net.example.app")
        #expect(try Data(contentsOf: store.apkURL(for: first.packageName)) == Data(zipBytes))
        #expect(try Data(contentsOf: source) == Data(zipBytes))

        var changed = first
        changed.settings = AppSettings(width: 800, height: 400, logLevel: .warn)
        try store.save(changed)
        _ = try await store.importAPK(from: source) { _ in report }
        let loaded = store.load()
        #expect(loaded.count == 1)
        #expect(loaded[0].settings.width == 800)
        // No staging files are left behind.
        let leftovers = try FileManager.default.contentsOfDirectory(atPath: store.directory.appending(path: "apps").path)
        #expect(leftovers == ["net.example.app"])
    }

    @Test func failedInspectionLeavesLibraryEmpty() async throws {
        let store = try makeStore()
        defer { try? FileManager.default.removeItem(at: store.directory) }
        let source = try writeFile(zipBytes, in: store)
        await #expect(throws: LibraryError.self) {
            _ = try await store.importAPK(from: source) { _ in throw LibraryError.inspectFailed("bad") }
        }
        #expect(store.load().isEmpty)
        let apps = store.directory.appending(path: "apps").path
        #expect(try FileManager.default.contentsOfDirectory(atPath: apps).isEmpty)
    }

    @Test func rejectsHostilePackageNameFromInspector() async throws {
        let store = try makeStore()
        defer { try? FileManager.default.removeItem(at: store.directory) }
        let source = try writeFile(zipBytes, in: store)
        var hostile = try InspectReport.decode(sampleReport)
        hostile.manifest.package = "../../evil.pkg"
        let report = hostile
        await #expect(throws: LibraryError.invalidPackageName("../../evil.pkg")) {
            _ = try await store.importAPK(from: source) { _ in report }
        }
    }

    @Test func removeDeletesEntry() async throws {
        let store = try makeStore()
        defer { try? FileManager.default.removeItem(at: store.directory) }
        let source = try writeFile(zipBytes, in: store)
        let report = try InspectReport.decode(sampleReport)
        let entry = try await store.importAPK(from: source) { _ in report }
        try store.remove(entry.packageName)
        #expect(store.load().isEmpty)
        #expect(throws: LibraryError.self) { try store.remove("../x") }
    }
}

@Suite struct RuntimeLocationTests {
    @Test func validatesCheckoutMarkers() throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "rt-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root.appending(path: "src/shiraz"), withIntermediateDirectories: true)
        #expect(throws: RuntimeLocation.Invalid.self) { try RuntimeLocation.validate(root: root) }
        try "1.2.3\n".write(to: root.appending(path: "VERSION"), atomically: true, encoding: .utf8)
        try "".write(to: root.appending(path: "src/shiraz/__main__.py"), atomically: true, encoding: .utf8)
        try RuntimeLocation.validate(root: root)
        #expect(RuntimeLocation.version(root: root) == "1.2.3")
        let detected = RuntimeLocation.detectRoot(environment: ["SHIRAZ_ROOT": root.path], executable: nil,
                                                  currentDirectory: URL(filePath: "/"))
        #expect(detected?.standardizedFileURL.path == root.standardizedFileURL.path)
    }

    @Test func environmentPointsPythonAtSources() {
        let location = RuntimeLocation(root: URL(filePath: "/opt/shiraz"), python: URL(filePath: "/usr/bin/python3"))
        #expect(location.environment["PYTHONPATH"] == "/opt/shiraz/src")
        #expect(location.arguments(["inspect", "a.apk"]) == ["-m", "shiraz", "inspect", "a.apk"])
    }
}

@Suite struct ProcessRunnerTests {
    @Test func collectsOutputAndExitStatus() async throws {
        let output = try await ProcessRunner.run(
            executable: URL(filePath: "/bin/sh"),
            arguments: ["-c", "echo out; echo err >&2; exit 3"],
            environment: [:], workingDirectory: nil)
        #expect(String(decoding: output.stdout, as: UTF8.self) == "out\n")
        #expect(output.stderr == "err\n")
        #expect(output.status.kind == .exited(3))
    }

    @Test func timeoutKillsProcessGroup() async throws {
        let started = Date()
        let output = try await ProcessRunner.run(
            executable: URL(filePath: "/bin/sh"),
            arguments: ["-c", "sleep 30 & sleep 30"],
            environment: [:], workingDirectory: nil, timeout: 0.5)
        #expect(!output.status.succeeded)
        #expect(Date().timeIntervalSince(started) < 10)
    }

    @Test func missingExecutableThrows() async {
        await #expect(throws: SpawnError.self) {
            _ = try await ProcessRunner.run(executable: URL(filePath: "/nonexistent/bin"),
                                            arguments: [], environment: [:], workingDirectory: nil)
        }
    }

    @Test func honorsWorkingDirectoryAndEnvironment() async throws {
        let output = try await ProcessRunner.run(
            executable: URL(filePath: "/bin/sh"), arguments: ["-c", "pwd; echo $FOO"],
            environment: ["FOO": "bar"], workingDirectory: URL(filePath: "/tmp"))
        #expect(String(decoding: output.stdout, as: UTF8.self).hasSuffix("/tmp\nbar\n"))
    }
}

/// Drives the real `python3 -m shiraz inspect` on a real APK. Skipped when
/// no checkout (with APKs under context/apks) is found from the test's location.
@Suite struct RealRuntimeIntegrationTests {
    private static var checkout: URL? {
        RuntimeLocation.detectRoot(executable: nil, currentDirectory: URL(filePath: #filePath).deletingLastPathComponent())
    }

    private static var apk: URL? {
        guard let root = checkout else { return nil }
        return root.appending(path: "context/apks/com.dozingcatsoftware.dodge_10.apk")
    }

    @Test(.enabled(if: apk.map { FileManager.default.fileExists(atPath: $0.path) } ?? false))
    func importsRealAPKThroughRuntimeInspect() async throws {
        let root = try #require(Self.checkout)
        let apk = try #require(Self.apk)
        let python = try #require(RuntimeLocation.detectPython())
        let runtime = RuntimeLocation(root: root, python: python)

        let directory = FileManager.default.temporaryDirectory.appending(path: "launcher-real-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LibraryStore(directory: directory)

        let entry = try await store.importAPK(from: apk) { staged in
            let output = try await ProcessRunner.run(
                executable: runtime.python, arguments: runtime.arguments(RuntimeCommand.inspect(apk: staged)),
                environment: runtime.environment, workingDirectory: runtime.root)
            #expect(output.status.succeeded, "\(output.stderr)")
            return try InspectReport.decode(output.stdout)
        }
        #expect(entry.packageName == "com.dozingcatsoftware.dodge")
        #expect(entry.versionName == "1.5.1")
        #expect(entry.compatibility == .javaOnly)
        #expect(entry.sha256.count == 64)
        #expect(try Data(contentsOf: store.apkURL(for: entry.packageName)) == Data(contentsOf: apk))
        #expect(store.load().map(\.packageName) == [entry.packageName])

        await store.extractIcon(for: entry.packageName) { apk, output in
            let result = try await ProcessRunner.run(
                executable: runtime.python, arguments: runtime.arguments(RuntimeCommand.icon(apk: apk, outputDirectory: output)),
                environment: runtime.environment, workingDirectory: runtime.root)
            #expect(result.status.succeeded, "\(result.stderr)")
        }
        let iconError = store.iconError(for: entry.packageName) ?? "none"
        let icon = try #require(store.icon(for: entry.packageName), "icon error: \(iconError)")
        #expect(icon.kind == .bitmap)
        let rendered = try #require(icon.render(directory: store.iconDirectory(for: entry.packageName), pixels: 64))
        #expect(rendered.width == 64)
    }
}

@Suite struct AppIconTests {
    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "launcher-icon-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    /// A solid-colour PNG written with ImageIO.
    private func writePNG(_ url: URL, side: Int, red: CGFloat) throws {
        let context = try #require(CGContext(
            data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(srgbRed: red, green: 0, blue: 0, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: side, height: side))
        let destination = try #require(CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try #require(context.makeImage()), nil)
        #expect(CGImageDestinationFinalize(destination))
    }

    @Test func parsesExtractorColours() {
        #expect(AppIcon.color("#ff336699")?.components?.map { ($0 * 255).rounded() } == [0x33, 0x66, 0x99, 0xff])
        #expect(AppIcon.color("#336699") == nil)
        #expect(AppIcon.color("#zz336699") == nil)
        #expect(AppIcon.color(nil) == nil)
    }

    @Test func rendersBitmapAndAdaptiveIcons() throws {
        let folder = try directory()
        defer { try? FileManager.default.removeItem(at: folder) }
        try writePNG(folder.appending(path: "icon.png"), side: 48, red: 1)
        try Data(#"{"schema":1,"kind":"bitmap","image":"icon.png"}"#.utf8).write(to: folder.appending(path: "icon.json"))
        let bitmap = try AppIcon.load(directory: folder)
        #expect(bitmap.render(directory: folder, pixels: 96)?.width == 96)

        try writePNG(folder.appending(path: "foreground.png"), side: 108, red: 0.5)
        try Data(##"{"kind":"adaptive","foreground":"foreground.png","background_color":"#ff0000ff"}"##.utf8)
            .write(to: folder.appending(path: "icon.json"))
        let adaptive = try AppIcon.load(directory: folder)
        #expect(adaptive.kind == .adaptive)
        #expect(adaptive.backgroundColor == "#ff0000ff")
        #expect(adaptive.render(directory: folder, pixels: 64)?.height == 64)
    }

    @Test func refusesLayerNamesOutsideTheIconFolder() throws {
        let folder = try directory()
        defer { try? FileManager.default.removeItem(at: folder) }
        try writePNG(folder.deletingLastPathComponent().appending(path: "outside-\(UUID().uuidString).png"), side: 8, red: 1)
        for name in ["../x.png", "/etc/hosts", ".hidden.png", ""] {
            #expect(AppIcon.image(named: name, in: folder) == nil)
        }
        let icon = AppIcon(kind: .bitmap, image: "../x.png")
        #expect(icon.render(directory: folder) == nil)
    }

    @Test func failedExtractionIsRecordedNotThrown() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "launcher-test-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LibraryStore(directory: directory)
        #expect(!store.iconWasAttempted(for: "net.example.app"))
        await store.extractIcon(for: "net.example.app") { _, _ in throw LibraryError.inspectFailed("vector icons are not rendered yet") }
        #expect(store.iconWasAttempted(for: "net.example.app"))
        #expect(store.icon(for: "net.example.app") == nil)
        #expect(store.iconError(for: "net.example.app") == "vector icons are not rendered yet")
        // Output that is not a valid icon.json also counts as a failure.
        await store.extractIcon(for: "net.example.app") { _, output in
            try Data("{}".utf8).write(to: output.appending(path: "icon.json"))
        }
        #expect(store.icon(for: "net.example.app") == nil)
        #expect(store.iconError(for: "net.example.app") != nil)
        await store.extractIcon(for: "../evil.pkg") { _, _ in Issue.record("must not run for an invalid package") }
    }
}
