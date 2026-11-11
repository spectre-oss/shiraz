// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// What the macOS runtime can do with an APK's native code.
/// Mirrors docs/macos_final_goal.md: arm64-v8a only, no translation.
public enum Compatibility: Hashable, Sendable {
    case javaOnly
    case arm64
    case unsupported(String)

    public var isLaunchable: Bool {
        if case .unsupported = self { return false }
        return true
    }
}

public struct AppEntry: Codable, Identifiable, Hashable, Sendable {
    public var id: String { packageName }
    public var packageName: String
    public var versionName: String?
    public var versionCode: String?
    public var minSdk: String?
    public var targetSdk: String?
    public var nativeAbis: [String]
    public var permissions: [String]
    public var sha256: String
    public var importedAt: Date
    public var settings: AppSettings
    // Optional so entry.json files written before these fields still decode.
    public var favorite: Bool?
    /// The APK's `android:label` in the default locale, from `shiraz label`.
    public var label: String?
    public var lastLaunchedAt: Date?

    public var isFavorite: Bool {
        get { favorite ?? false }
        set { favorite = newValue ? true : nil }
    }

    public init(report: InspectReport, importedAt: Date = Date(), settings: AppSettings = AppSettings()) {
        packageName = report.manifest.package
        versionName = report.manifest.versionName
        versionCode = report.manifest.versionCode
        minSdk = report.manifest.minSdk
        targetSdk = report.manifest.targetSdk
        nativeAbis = report.nativeAbis
        permissions = report.manifest.permissions
        sha256 = report.apkSha256
        self.importedAt = importedAt
        self.settings = settings
    }

    public var compatibility: Compatibility {
        if nativeAbis.isEmpty { return .javaOnly }
        if nativeAbis.contains("arm64-v8a") { return .arm64 }
        return .unsupported("Only \(nativeAbis.joined(separator: ", ")) native code. The macOS runtime needs arm64-v8a.")
    }

    /// The APK's label, or the package name's last component until it is known.
    public var displayName: String {
        if let label, !label.isEmpty { return label }
        let tail = packageName.split(separator: ".").last.map(String.init) ?? packageName
        return tail.prefix(1).uppercased() + tail.dropFirst()
    }
}
