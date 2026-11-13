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

    @Test func nativeCodeMapsToGuestLoader() {
        #expect(RuntimeCommand.runEnvironment(settings: AppSettings()) == ["SHIRAZ_GUEST_LOADER": "0"])
        #expect(RuntimeCommand.runEnvironment(settings: AppSettings(nativeCode: true)) == ["SHIRAZ_GUEST_LOADER": "1"])
        #expect(!RuntimeCommand.run(apk: URL(fileURLWithPath: "/x.apk"), settings: AppSettings(nativeCode: true))
            .contains { $0.contains("GUEST") })
    }

    @Test func settingsWithoutNativeCodeStillDecode() throws {
        let old = #"{"width":360,"height":640,"logLevel":"INFO"}"#
        let settings = try JSONDecoder().decode(AppSettings.self, from: Data(old.utf8))
        #expect(settings == AppSettings())
        let round = try JSONDecoder().decode(AppSettings.self, from: JSONEncoder().encode(AppSettings(nativeCode: true)))
        #expect(round.nativeCode)
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

@Test func logLevelReadsSeverityFromContent() {
    #expect(LogLine.level(of: "E/ShirazHost( 1969): Could not create image space") == .error)
    #expect(LogLine.level(of: "W/Thing(1): careful") == .warning)
    #expect(LogLine.level(of: "I/wolfssljni( 1969): Unable to set FIPS callback") == .info)
    #expect(LogLine.level(of: "D/x(1): y") == .debug)
    #expect(LogLine.level(of: "SHIRAZ_UNSUPPORTED PR_SET_DUMPABLE: core dumps disabled") == .warning)
    #expect(LogLine.level(of: "SHIRAZ_RUNTIME version=0.338.0") == .info)
    #expect(LogLine.level(of: "Traceback (most recent call last):") == .error)
    #expect(LogLine.level(of: "RuntimeError: boom") == .error)
    #expect(LogLine.level(of: "java.lang.IllegalStateException: bad") == .error)
}

@Test func everyUnimplementedFeatureThrows() {
    for feature in Feature.allCases {
        #expect(throws: NotImplementedError(feature: feature)) { try feature.perform() }
        #expect(!feature.title.isEmpty)
    }
}

@Test func logBufferDropsControlCharacters() {
    var log = LogBuffer()
    log.append(Data("\u{0}E/Host( 1): bad\r\nok\tdone\n".utf8), stream: .stderr)
    #expect(log.lines.map(\.text) == ["E/Host( 1): bad", "ok\tdone"])
    #expect(log.lines.first?.level == .error)
}

@Suite struct CatalogTests {
    private let sample = Data("""
    {"repo": {"name": "F-Droid", "address": "https://f-droid.org/repo", "timestamp": 1794404913394},
     "apps": [
      {"packageName": "org.example.app", "name": "Example", "summary": "Does things", "description": "Long",
       "license": "GPL-3.0-only", "categories": ["Games", "Science"], "iconURL": "https://f-droid.org/repo/i.png",
       "screenshots": ["https://f-droid.org/s.png", "http://insecure.example/s.png"], "lastUpdated": 1700000000000,
       "versionName": "2.0", "versionCode": 20, "size": 4096, "nativecode": [], "minSdk": "21",
       "compatible": true, "reason": null, "webURL": "https://f-droid.org/packages/org.example.app/",
       "sourceCode": "javascript:alert(1)", "antiFeatures": ["Ads"]},
      {"packageName": "org.example.x86", "name": "", "categories": ["Games"], "versionCode": 5, "nativecode": ["x86_64"],
       "compatible": false, "reason": "Native code only for x86_64", "minSdk": 24},
      {"packageName": "../evil", "name": "Evil", "compatible": true},
      {"name": "No package"}
     ]}
    """.utf8)

    @Test func decodesLenientlyAndDropsBadApps() throws {
        let catalog = try Catalog.decode(sample)
        #expect(catalog.apps.map(\.packageName) == ["org.example.app", "org.example.x86"])
        #expect(catalog.repo.timestamp == Date(timeIntervalSince1970: 1_794_404_913.394))
        let app = catalog.apps[0]
        #expect(app.minSdk == 21)
        #expect(app.screenshots == [URL(string: "https://f-droid.org/s.png")!])
        #expect(app.sourceCode == nil)
        #expect(app.iconURL?.host == "f-droid.org")
        #expect(catalog.apps[1].name == "org.example.x86")
        #expect(catalog.apps[1].minSdk == 24)
        #expect(catalog.categories == ["Games", "Science"])
    }

    @Test func statusComparesVersionCodes() throws {
        let app = try Catalog.decode(sample).apps[0]
        var report = try InspectReport.decode(sampleReport)
        report.manifest.package = "org.example.app"
        var entry = AppEntry(report: report)
        #expect(app.status(in: []) == .notInLibrary)
        entry.versionCode = "7"
        #expect(app.status(in: [entry]) == .updateAvailable(from: "1.2"))
        entry.versionCode = "20"
        #expect(app.status(in: [entry]) == .inLibrary)
        entry.versionCode = nil
        #expect(app.status(in: [entry]) == .inLibrary)
    }

    @Test func searchMatchesNameSummaryAndPackage() throws {
        let app = try Catalog.decode(sample).apps[0]
        #expect(app.matches(""))
        #expect(app.matches("does"))
        #expect(app.matches("ORG.EXAMPLE"))
        #expect(!app.matches("telegram"))
    }

    /// A real `shiraz fdroid sync` output, when SHIRAZ_TEST_CATALOG points at one.
    @Test(.enabled(if: ProcessInfo.processInfo.environment["SHIRAZ_TEST_CATALOG"] != nil))
    func decodesRealCatalog() throws {
        let path = try #require(ProcessInfo.processInfo.environment["SHIRAZ_TEST_CATALOG"])
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let raw = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let count = (raw["apps"] as? [Any])?.count ?? 0
        let catalog = try Catalog.decode(data)
        #expect(count > 1000)
        #expect(catalog.apps.count == count)
        #expect(catalog.apps.contains { $0.compatible && $0.iconURL != nil && $0.versionCode != nil && $0.size != nil })
    }

    @Test func fdroidCommands() {
        let cache = URL(fileURLWithPath: "/lib/fdroid")
        #expect(RuntimeCommand.fdroidSync(cache: cache, maxAge: -5) == ["fdroid", "sync", "--cache", "/lib/fdroid", "--max-age", "0"])
        #expect(RuntimeCommand.fdroidFetch(package: "org.a.b", cache: cache, output: URL(fileURLWithPath: "/tmp/d"))
            == ["fdroid", "fetch", "org.a.b", "--cache", "/lib/fdroid", "--output", "/tmp/d"])
    }

    @Test func downloadDirectoriesAreFreshAndInsideTheCatalog() throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "catalog-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(directory: root)
        let a = try store.makeDownloadDirectory(), b = try store.makeDownloadDirectory()
        #expect(a != b)
        #expect(a.path.hasPrefix(store.catalogDirectory.path))
        #expect(try FileManager.default.contentsOfDirectory(atPath: a.path).isEmpty)
    }
}

@Suite struct InstallInboxTests {
    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "inbox-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func app(_ package: String, permissions: [String] = [], abis: [String] = []) throws -> AppEntry {
        var report = try InspectReport.decode(sampleReport)
        report.manifest.package = package
        report.manifest.permissions = permissions
        report.nativeAbis = abis
        return AppEntry(report: report)
    }

    @Test func javaPropertiesRoundTrip() {
        // As java.util.Properties.store(Writer) writes it, with the date comment.
        let stored = "#Thu Nov 13 12:00:00 IRST 2026\nkind=install\ninstaller=org.fdroid.fdroid\nexpected=org.example.app\nmessage=a\\:b\\=c\\\n  continued \\u00e9\n"
        let parsed = JavaProperties.parse(stored)
        #expect(parsed["kind"] == "install")
        #expect(parsed["expected"] == "org.example.app")
        #expect(parsed["message"] == "a:b=ccontinued é")
        let values = ["message": " leading, ünïcode\nline=2: #x", "package": "org.a.b", "status": "failed"]
        #expect(JavaProperties.parse(JavaProperties.format(values)) == values)
    }

    @Test func readsCompleteRequestsAndAnswersMalformedOnes() throws {
        let inbox = try directory()
        defer { try? FileManager.default.removeItem(at: inbox) }
        try Data("PK\u{3}\u{4}".utf8).write(to: inbox.appending(path: "a1.apk"))
        try "kind=install\ninstaller=org.fdroid.fdroid\nexpected=org.example.app\n".write(to: inbox.appending(path: "a1.request"), atomically: true, encoding: .utf8)
        try "kind=launch\npackage=../escape\n".write(to: inbox.appending(path: "b2.request"), atomically: true, encoding: .utf8)
        try "kind=install\n".write(to: inbox.appending(path: "c3.request.part"), atomically: true, encoding: .utf8)
        try "kind=install\n".write(to: inbox.appending(path: "bad id.request"), atomically: true, encoding: .utf8)
        let requests = InstallInbox.pending(in: inbox)
        #expect(requests.map(\.id) == ["a1"])
        #expect(requests[0].kind == .install && requests[0].expected == "org.example.app")
        #expect(requests[0].apk?.lastPathComponent == "a1.apk")
        let answer = try String(contentsOf: inbox.appending(path: "b2.result"), encoding: .utf8)
        #expect(JavaProperties.parse(answer)["status"] == "failed")
        #expect(!FileManager.default.fileExists(atPath: inbox.appending(path: "b2.request").path))

        try InstallInbox.answer(id: "a1", in: inbox, ok: true, package: "org.example.app", message: nil)
        #expect(JavaProperties.parse(try String(contentsOf: inbox.appending(path: "a1.result"), encoding: .utf8))
            == ["status": "ok", "package": "org.example.app"])
        #expect(!FileManager.default.fileExists(atPath: inbox.appending(path: "a1.apk").path))
        #expect(InstallInbox.pending(in: inbox).isEmpty)
    }

    @Test func policyChecksPermissionsAndLibrary() throws {
        let apk = URL(fileURLWithPath: "/inbox/x.apk")
        let store = try app("org.fdroid.fdroid", permissions: [InstallInbox.installPermission, InstallInbox.deletePermission])
        let plain = try app("org.example.game")
        let x86 = try app("org.example.x86", abis: ["x86_64"])
        let library = [store, plain, x86]
        func request(_ kind: InboxRequest.Kind, package: String? = nil, expected: String? = nil, apk: URL? = nil) -> InboxRequest {
            InboxRequest(id: "r", kind: kind, installer: "org.fdroid.fdroid", package: package, expected: expected, apk: apk)
        }
        #expect(InstallInbox.decide(request(.install, expected: "org.new.app", apk: apk), requester: store, library: library)
            == .install(apk: apk, expected: "org.new.app"))
        if case .refuse = InstallInbox.decide(request(.install, expected: "org.new.app", apk: apk), requester: plain, library: library) {} else {
            Issue.record("An app without REQUEST_INSTALL_PACKAGES installed something")
        }
        if case .refuse = InstallInbox.decide(request(.install, expected: "org.example.game", apk: apk), requester: store, library: library) {} else {
            Issue.record("An app replaced a library package")
        }
        if case .refuse = InstallInbox.decide(request(.install, expected: "org.new.app"), requester: store, library: library) {} else {
            Issue.record("Install without an APK was accepted")
        }
        #expect(InstallInbox.decide(request(.launch, package: "org.example.game"), requester: plain, library: library) == .launch("org.example.game"))
        if case .refuse = InstallInbox.decide(request(.launch, package: "org.example.x86"), requester: plain, library: library) {} else {
            Issue.record("Launched an unsupported app")
        }
        #expect(InstallInbox.decide(request(.remove, package: "org.example.game"), requester: store, library: library) == .remove("org.example.game"))
        if case .refuse = InstallInbox.decide(request(.remove, package: "org.example.game"), requester: plain, library: library) {} else {
            Issue.record("An app without REQUEST_DELETE_PACKAGES removed something")
        }
        if case .refuse = InstallInbox.decide(request(.remove, package: "org.fdroid.fdroid"), requester: store, library: library) {} else {
            Issue.record("An app removed itself")
        }
    }

    @Test func importCanRefuseReplacing() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(directory: root)
        let apk = root.appending(path: "in.apk")
        try Data([0x50, 0x4b, 0x03, 0x04] + [UInt8](repeating: 0, count: 64)).write(to: apk)
        let report = try InspectReport.decode(sampleReport)
        _ = try await store.importAPK(from: apk) { _ in report }
        await #expect(throws: LibraryError.alreadyInLibrary("net.example.app")) {
            _ = try await store.importAPK(from: apk, replacing: false) { _ in report }
        }
        #expect(RuntimeCommand.metadata(apk: apk, installer: "org.fdroid.fdroid").suffix(2) == ["--installer", "org.fdroid.fdroid"])
        #expect(RuntimeCommand.libraryEnvironment(apps: store.appsDirectory, inbox: root)["SHIRAZ_LAUNCHER_APPS"] == store.appsDirectory.path)
    }
}

@Suite struct InboxHandlerTests {
    private func app(_ package: String, permissions: [String] = []) throws -> AppEntry {
        var report = try InspectReport.decode(sampleReport)
        report.manifest.package = package
        report.manifest.permissions = permissions
        return AppEntry(report: report)
    }

    private func result(_ id: String, in inbox: URL) throws -> [String: String] {
        JavaProperties.parse(try String(contentsOf: inbox.appending(path: id + ".result"), encoding: .utf8))
    }

    @Test func handleActsAndAnswers() async throws {
        let inbox = FileManager.default.temporaryDirectory.appending(path: "handle-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: inbox) }
        let fdroid = try app("org.fdroid.fdroid", permissions: [InstallInbox.installPermission, InstallInbox.deletePermission])
        let added = try app("org.example.new")
        let apk = inbox.appending(path: "i1.apk")
        try Data("PK".utf8).write(to: apk)
        var installed: [String] = []
        let actions = InboxActions(
            install: { url, expected, installer in
                installed.append("\(url.lastPathComponent) \(expected ?? "-") \(installer)")
                return added
            },
            launch: { _ in false },
            remove: { _ in true })

        let install = InboxRequest(id: "i1", kind: .install, installer: "spoofed.name", package: nil, expected: "org.example.new", apk: apk)
        #expect(await InstallInbox.handle(install, requester: fdroid, library: [fdroid], inbox: inbox, actions: actions) == .installed(added))
        // The installer recorded is the session owner, not what the request claims.
        #expect(installed == ["i1.apk org.example.new org.fdroid.fdroid"])
        #expect(try result("i1", in: inbox) == ["status": "ok", "package": "org.example.new"])

        let launch = InboxRequest(id: "l1", kind: .launch, installer: nil, package: "org.example.new", expected: nil, apk: nil)
        #expect(await InstallInbox.handle(launch, requester: fdroid, library: [fdroid, added], inbox: inbox, actions: actions)
            == .launched("org.example.new", false))
        #expect(try result("l1", in: inbox)["status"] == "failed")

        let other = try app("org.example.plain")
        let refused = InboxRequest(id: "r1", kind: .remove, installer: nil, package: "org.example.new", expected: nil, apk: nil)
        guard case .refused = await InstallInbox.handle(refused, requester: other, library: [other, added], inbox: inbox, actions: actions) else {
            Issue.record("A remove without REQUEST_DELETE_PACKAGES was not refused"); return
        }
        #expect(try result("r1", in: inbox)["status"] == "failed")
    }

    @Test func removesOnlyStaleSessionDirectories() throws {
        let sessions = FileManager.default.temporaryDirectory.appending(path: "sessions-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: sessions) }
        let old = sessions.appending(path: "old"), live = sessions.appending(path: "live")
        for url in [old, live] { try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true) }
        try FileManager.default.setAttributes([.modificationDate: Date(timeIntervalSinceNow: -3 * 86_400)], ofItemAtPath: old.path)
        InstallInbox.removeStale(in: sessions)
        #expect(!FileManager.default.fileExists(atPath: old.path))
        #expect(FileManager.default.fileExists(atPath: live.path))
    }

    /// Serves a real inbox with the real runtime, for an app run by hand with
    /// SHIRAZ_LAUNCHER_APPS=<library>/apps SHIRAZ_LAUNCHER_INBOX=<inbox>.
    /// SHIRAZ_TEST_INBOX_REQUESTER is the installer APK; it is added to the
    /// library first, then SHIRAZ_TEST_INBOX_SEED (APK paths separated by ':').
    /// Stops after a remove request or SHIRAZ_TEST_INBOX_SECONDS.
    @Test(.enabled(if: ProcessInfo.processInfo.environment["SHIRAZ_TEST_INBOX"] != nil))
    func servesRealInbox() async throws {
        let env = ProcessInfo.processInfo.environment
        let inbox = URL(fileURLWithPath: try #require(env["SHIRAZ_TEST_INBOX"]))
        let store = LibraryStore(directory: URL(fileURLWithPath: try #require(env["SHIRAZ_TEST_LIBRARY"])))
        let requesterAPK = URL(fileURLWithPath: try #require(env["SHIRAZ_TEST_INBOX_REQUESTER"]))
        let seconds = env["SHIRAZ_TEST_INBOX_SECONDS"].flatMap(Double.init) ?? 900
        let root = try #require(RuntimeLocation.detectRoot(
            executable: nil, currentDirectory: URL(filePath: #filePath).deletingLastPathComponent()))
        let tools = RuntimeTools(runtime: RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython())))
        try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        let requester = try await tools.add(apk: requesterAPK, to: store)
        for path in (env["SHIRAZ_TEST_INBOX_SEED"] ?? "").split(separator: ":") {
            _ = try await tools.add(apk: URL(fileURLWithPath: String(path)), to: store)
        }
        print("inbox ready: \(requester.packageName) in \(store.appsDirectory.path)")

        var log: [String] = []
        let actions = InboxActions(
            install: { apk, expected, installer in
                try await tools.add(apk: apk, to: store, expected: expected, installer: installer, replacing: false)
            },
            launch: { package in log.append("launch \(package)"); return true },
            remove: { package in (try? store.remove(package)) != nil })
        let deadline = Date(timeIntervalSinceNow: seconds)
        var done = false
        while !done, Date() < deadline {
            for request in InstallInbox.pending(in: inbox) {
                let outcome = await InstallInbox.handle(request, requester: requester, library: store.load(), inbox: inbox, actions: actions)
                print("inbox \(request.id) \(request.kind.rawValue): \(outcome)")
                log.append("\(request.kind.rawValue) \(outcome)")
                if request.kind == .remove { done = true }
            }
            try await Task.sleep(for: .milliseconds(300))
        }
        #expect(log.contains { $0.hasPrefix("install installed") }, "\(log)")
    }
}
