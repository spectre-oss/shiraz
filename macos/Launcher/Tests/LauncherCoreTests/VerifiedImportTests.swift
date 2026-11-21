// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import Testing
@testable import LauncherCore

@Suite struct VerifiedImportTests {
    @Test func versionCodesIncludeMajorAndRejectOutOfRangeValues() throws {
        func manifest(_ code: String?, _ major: String?) throws -> InspectReport.Manifest {
            var fields = ["package": "org.example.app"]
            fields["version_code"] = code
            fields["version_code_major"] = major
            return try JSONDecoder().decode(InspectReport.Manifest.self, from: JSONSerialization.data(withJSONObject: fields))
        }
        #expect(try manifest(nil, nil).longVersionCode() == 0)
        #expect(try manifest("0x2", "1").longVersionCode() == 4_294_967_298)
        #expect(try manifest("2147483647", "2147483647").longVersionCode() == 0x7fffffff7fffffff)
        #expect(try manifest("0", "1").longVersionCode() > manifest("2147483647", "0").longVersionCode())
        for invalid in ["-1", "2147483648", "0x80000000", "9223372036854775808", "garbage"] {
            #expect(throws: (any Error).self) { try manifest(invalid, "0").longVersionCode() }
            #expect(throws: (any Error).self) { try manifest("0", invalid).longVersionCode() }
        }
    }

    @Test(.enabled(if: ProcessInfo.processInfo.environment["SHIRAZ_TEST_VERIFIED_IMPORT_APK"] != nil))
    func importsSignedAPKWithRealVerifier() async throws {
        let apk = URL(fileURLWithPath: try #require(ProcessInfo.processInfo.environment["SHIRAZ_TEST_VERIFIED_IMPORT_APK"]))
        let root = try #require(RuntimeLocation.detectRoot(executable: nil,
                                                           currentDirectory: URL(filePath: #filePath).deletingLastPathComponent()))
        let directory = FileManager.default.temporaryDirectory.appending(path: "verified-real-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let tools = RuntimeTools(runtime: RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython())))
        let store = LibraryStore(directory: directory)
        let report = try await tools.inspect(apk: apk)
        let entry = try await tools.add(apk: apk, to: store)
        #expect(entry.sha256 == report.apkSha256)
        #expect(entry.packageName == report.manifest.package)
        #expect(try Data(contentsOf: store.apkURL(for: entry.packageName)) == Data(contentsOf: apk))
        let repeated = try await tools.add(apk: apk, to: store)
        #expect(repeated.sha256 == entry.sha256)
        #expect(store.load().count == 1)
    }

    // Controlled helper responses test publication policy, not cryptography.
    @Test(arguments: ["invalid", "mismatch", "empty", "other", "downgrade", "same-version", "bad-version", "busy", "bad-enrollment"])
    func rejectsUnverifiedReplacementAndPreservesInstalledAPK(_ failure: String) async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "verified-import-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let module = root.appending(path: "src/shiraz")
        try FileManager.default.createDirectory(at: module, withIntermediateDirectories: true)
        try """
        import hashlib, json, pathlib, sys
        command, path = sys.argv[1:3]
        data = pathlib.Path(path).read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        if command == 'inspect':
            version = '3' if data.endswith((b'updated', b'busy', b'bad-enrollment')) else ('1' if data.endswith(b'downgrade') else ('invalid' if data.endswith(b'bad-version') else '2'))
            print(json.dumps(dict(apk_sha256=sha, manifest=dict(package='org.example.app', version_code=version))))
        elif command == 'verify-signature':
            if data.endswith(b'invalid'):
                sys.exit(1)
            print(json.dumps(dict(apk_sha256=('0'*64 if data.endswith(b'mismatch') else sha),
                verified_signers_sha256=([] if data.endswith(b'empty') else [('b' if data.endswith(b'other') else 'a')*64]))))
        elif command == 'register-package':
            if data.endswith(b'busy'):
                print('Package generation is in use or being replaced', file=sys.stderr)
                sys.exit(1)
            print(json.dumps(dict(package='org.example.app', uid=10000,
                apk_sha256=('0'*64 if data.endswith(b'bad-enrollment') else sha),
                version=3 if data.endswith(b'updated') else 2)))
        elif command == 'label':
            print('{"label":"Fixture"}')
        elif command == 'metadata':
            print('metadataVersion=2')
            if '--installer' in sys.argv:
                print('installer=' + sys.argv[sys.argv.index('--installer') + 1])
        """.write(to: module.appending(path: "__main__.py"), atomically: true, encoding: .utf8)
        let tools = RuntimeTools(runtime: RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython())))
        let store = LibraryStore(directory: root.appending(path: "library"))
        let candidate = root.appending(path: "input.apk")
        let original = Data([0x50, 0x4b, 0x03, 0x04]) + Data(repeating: 0, count: 32) + Data("first".utf8)
        try original.write(to: candidate)
        _ = try await tools.add(apk: candidate, to: store)
        let savedEntry = try Data(contentsOf: store.apkURL(for: "org.example.app"))
        let savedDigest = store.load().first?.sha256
        try (Data([0x50, 0x4b, 0x03, 0x04]) + Data(repeating: 0, count: 32) + Data(failure.utf8)).write(to: candidate)
        await #expect(throws: (any Error).self) { try await tools.add(apk: candidate, to: store) }
        #expect(try Data(contentsOf: store.apkURL(for: "org.example.app")) == savedEntry)
        #expect(store.load().count == 1)
        #expect(store.load().first?.sha256 == savedDigest)
        #expect(try FileManager.default.contentsOfDirectory(atPath: store.appsDirectory.path).allSatisfy { !$0.hasPrefix(".staging-") })
        let updated = Data([0x50, 0x4b, 0x03, 0x04]) + Data(repeating: 0, count: 32) + Data("updated".utf8)
        try updated.write(to: candidate)
        _ = try await tools.add(apk: candidate, to: store)
        #expect(try Data(contentsOf: store.apkURL(for: "org.example.app")) == updated)
        var missing = try #require(store.load().first)
        missing.label = nil
        try store.save(missing)
        #expect(await tools.refreshMissingLabel(for: "org.example.app", store: store))
        #expect(store.load().first?.label == "Fixture")
        missing.label = "Newer label"
        try store.save(missing)
        #expect(await tools.refreshMissingLabel(for: "org.example.app", store: store))
        #expect(store.load().first?.label == "Newer label")
        try store.saveMetadata(Data("metadataVersion=1\ninstaller=org.previous.store\n".utf8), for: "org.example.app")
        await tools.refreshMissingMetadata(for: "org.example.app", store: store)
        #expect(store.hasCurrentMetadata(for: "org.example.app"))
        #expect(store.metadata(for: "org.example.app")?["installer"] == "org.previous.store")
    }
}
