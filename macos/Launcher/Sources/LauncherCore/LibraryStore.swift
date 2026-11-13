// SPDX-License-Identifier: GPL-3.0-only
import Foundation

public enum LibraryError: Error, LocalizedError, Sendable, Equatable {
    case notAnAPK(String)
    case tooLarge(Int64)
    case invalidPackageName(String)
    case inspectFailed(String)
    case alreadyInLibrary(String)

    public var errorDescription: String? {
        switch self {
        case .notAnAPK(let reason): "Not an APK: \(reason)"
        case .tooLarge(let bytes): "APK is \(bytes / 1_048_576) MiB, over the \(LibraryStore.maxAPKBytes / 1_048_576) MiB limit."
        case .invalidPackageName(let name): "Invalid package name: \(name)"
        case .inspectFailed(let message): message
        case .alreadyInLibrary(let package): "\(package) is already in the library."
        }
    }
}

/// Imported APKs and their settings, under one app-support directory:
///
///     apps/<package>/app.apk     unmodified copy of the dropped file
///     apps/<package>/entry.json  parsed metadata and per-app settings
///     apps/<package>/icon/       launcher icon extracted by `shiraz icon` (see AppIcon)
public struct LibraryStore: Sendable {
    public static let maxAPKBytes: Int64 = 2 * 1024 * 1024 * 1024

    public var directory: URL

    public init(directory: URL) { self.directory = directory }

    /// `SHIRAZ_LAUNCHER_LIBRARY` points the launcher at another library,
    /// for development and UI testing without touching the user's apps.
    public static var defaultDirectory: URL {
        if let path = ProcessInfo.processInfo.environment["SHIRAZ_LAUNCHER_LIBRARY"], !path.isEmpty {
            return URL(fileURLWithPath: path, isDirectory: true)
        }
        return URL.applicationSupportDirectory.appending(path: "Shiraz Launcher", directoryHint: .isDirectory)
    }

    /// The verified F-Droid catalog and its download scratch space.
    public var catalogDirectory: URL { directory.appending(path: "fdroid", directoryHint: .isDirectory) }

    public var catalogURL: URL { catalogDirectory.appending(path: "catalog.json") }

    /// A fresh, empty directory for one catalog download. The caller removes it.
    public func makeDownloadDirectory() throws -> URL {
        let url = catalogDirectory.appending(path: "downloads", directoryHint: .isDirectory)
            .appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    /// One directory per imported package. Apps running in Shiraz read it
    /// (package.properties, app.apk) to see what else is installed.
    public var appsDirectory: URL { directory.appending(path: "apps", directoryHint: .isDirectory) }

    /// Per-session request directories (see InstallInbox).
    public var sessionsDirectory: URL { directory.appending(path: "sessions", directoryHint: .isDirectory) }

    /// Package facts from `shiraz metadata`, the PackageManager view other apps get.
    public func metadataURL(for package: String) -> URL {
        appsDirectory.appending(path: package, directoryHint: .isDirectory).appending(path: "package.properties")
    }

    public func hasMetadata(for package: String) -> Bool {
        FileManager.default.fileExists(atPath: metadataURL(for: package).path)
    }

    public func saveMetadata(_ data: Data, for package: String) throws {
        guard Self.isValidPackageName(package) else { throw LibraryError.invalidPackageName(package) }
        try data.write(to: metadataURL(for: package), options: .atomic)
    }

    /// Package names become directory names, so reject anything that could
    /// escape the library or collide with staging files.
    public static func isValidPackageName(_ name: String) -> Bool {
        guard name.count <= 255 else { return false }
        return name.wholeMatch(of: /[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+/) != nil
    }

    public func apkURL(for package: String) -> URL {
        appsDirectory.appending(path: package, directoryHint: .isDirectory).appending(path: "app.apk")
    }

    public func apkSize(for package: String) -> Int64? {
        (try? apkURL(for: package).resourceValues(forKeys: [.fileSizeKey]).fileSize).map(Int64.init)
    }

    public func iconDirectory(for package: String) -> URL {
        appsDirectory.appending(path: package, directoryHint: .isDirectory).appending(path: "icon", directoryHint: .isDirectory)
    }

    /// The extracted icon, or nil when none was extracted or it failed.
    public func icon(for package: String) -> AppIcon? {
        try? AppIcon.load(directory: iconDirectory(for: package))
    }

    /// True once extraction ran for this package, whether it succeeded or
    /// recorded an error. Re-importing the APK clears it.
    public func iconWasAttempted(for package: String) -> Bool {
        let directory = iconDirectory(for: package)
        return [AppIcon.manifestName, AppIcon.errorName].contains {
            FileManager.default.fileExists(atPath: directory.appending(path: $0).path)
        }
    }

    public func iconError(for package: String) -> String? {
        (try? String(contentsOf: iconDirectory(for: package).appending(path: AppIcon.errorName), encoding: .utf8))?
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Replace the icon directory with a fresh extraction. A failure is
    /// recorded in error.txt rather than thrown: a missing icon never blocks
    /// importing or launching.
    public func extractIcon(
        for package: String,
        extract: @Sendable (_ apk: URL, _ output: URL) async throws -> Void
    ) async {
        guard Self.isValidPackageName(package) else { return }
        let fileManager = FileManager.default
        let directory = iconDirectory(for: package)
        try? fileManager.removeItem(at: directory)
        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            try await extract(apkURL(for: package), directory)
            _ = try AppIcon.load(directory: directory)
        } catch {
            try? fileManager.removeItem(at: directory)
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            try? Data(error.localizedDescription.utf8).write(to: directory.appending(path: AppIcon.errorName))
        }
    }

    private func entryURL(for package: String) -> URL {
        appsDirectory.appending(path: package, directoryHint: .isDirectory).appending(path: "entry.json")
    }

    public func load() -> [AppEntry] {
        let fileManager = FileManager.default
        let names = (try? fileManager.contentsOfDirectory(atPath: appsDirectory.path)) ?? []
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return names.compactMap { name -> AppEntry? in
            guard Self.isValidPackageName(name),
                  let data = try? Data(contentsOf: entryURL(for: name)),
                  let entry = try? decoder.decode(AppEntry.self, from: data),
                  entry.packageName == name,
                  fileManager.fileExists(atPath: apkURL(for: name).path) else { return nil }
            return entry
        }
        .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    public func save(_ entry: AppEntry) throws {
        guard Self.isValidPackageName(entry.packageName) else { throw LibraryError.invalidPackageName(entry.packageName) }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(entry).write(to: entryURL(for: entry.packageName), options: .atomic)
    }

    public func remove(_ package: String) throws {
        guard Self.isValidPackageName(package) else { throw LibraryError.invalidPackageName(package) }
        try FileManager.default.removeItem(at: appsDirectory.appending(path: package, directoryHint: .isDirectory))
    }

    /// Cheap checks before anything parses the file: regular file, size
    /// limit, and the ZIP local-header magic. The runtime's `inspect`
    /// performs the full bounded archive validation.
    public static func precheck(_ url: URL) throws {
        let values = try url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey])
        guard values.isRegularFile == true else { throw LibraryError.notAnAPK("not a regular file") }
        let size = Int64(values.fileSize ?? 0)
        guard size > 22 else { throw LibraryError.notAnAPK("file is too small") }
        guard size <= maxAPKBytes else { throw LibraryError.tooLarge(size) }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        guard try handle.read(upToCount: 4) == Data([0x50, 0x4b, 0x03, 0x04]) else {
            throw LibraryError.notAnAPK("not a ZIP archive")
        }
    }

    /// Copy `source` into the library. `inspect` parses the staged copy, so
    /// the original file is never modified and never executed.
    /// `replacing: false` refuses a package that is already in the library.
    public func importAPK(
        from source: URL,
        replacing: Bool = true,
        inspect: @Sendable (URL) async throws -> InspectReport
    ) async throws -> AppEntry {
        let source = source.resolvingSymlinksInPath()
        try Self.precheck(source)

        let fileManager = FileManager.default
        try fileManager.createDirectory(at: appsDirectory, withIntermediateDirectories: true)
        let staged = appsDirectory.appending(path: ".staging-\(UUID().uuidString).apk")
        try fileManager.copyItem(at: source, to: staged)
        defer { try? fileManager.removeItem(at: staged) }

        let report: InspectReport
        do {
            report = try await inspect(staged)
        } catch let error as LibraryError {
            throw error
        } catch {
            throw LibraryError.inspectFailed(error.localizedDescription)
        }
        guard Self.isValidPackageName(report.manifest.package) else {
            throw LibraryError.invalidPackageName(report.manifest.package)
        }

        // Re-importing the same package keeps the user's settings.
        var entry = AppEntry(report: report)
        if let existing = load().first(where: { $0.packageName == entry.packageName }) {
            guard replacing else { throw LibraryError.alreadyInLibrary(entry.packageName) }
            entry.settings = existing.settings
        }
        let target = apkURL(for: entry.packageName)
        try fileManager.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fileManager.fileExists(atPath: target.path) {
            _ = try fileManager.replaceItemAt(target, withItemAt: staged)
        } else {
            try fileManager.moveItem(at: staged, to: target)
        }
        try save(entry)
        return entry
    }
}
