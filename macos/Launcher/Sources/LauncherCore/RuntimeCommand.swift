// SPDX-License-Identifier: GPL-3.0-only
import CryptoKit
import Foundation

/// Builds `shiraz` command-line arguments from launcher state.
public enum RuntimeCommand {
    public static func inspect(apk: URL) -> [String] {
        ["inspect", apk.path]
    }

    /// SHA-256 digests of the APK's signing certificates (read, not verified).
    public static func signer(apk: URL) -> [String] {
        ["signer", apk.path]
    }

    public static func label(apk: URL) -> [String] {
        ["label", apk.path]
    }

    public static func icon(apk: URL, outputDirectory: URL) -> [String] {
        ["icon", apk.path, "--output-dir", outputDirectory.path]
    }

    /// Download and verify the f-droid.org index into `<cache>/catalog.json`.
    /// A catalog younger than `maxAge` seconds is reused without a download.
    public static func fdroidSync(cache: URL, maxAge: Int = 0) -> [String] {
        ["fdroid", "sync", "--cache", cache.path, "--max-age", String(max(0, maxAge))]
    }

    /// Download one catalog APK into `output`; the runtime checks its SHA-256
    /// against the signed index before keeping it.
    public static func fdroidFetch(package: String, cache: URL, output: URL) -> [String] {
        ["fdroid", "fetch", package, "--cache", cache.path, "--output", output.path]
    }

    /// PackageManager facts for the library's package.properties.
    public static func metadata(apk: URL, installer: String?) -> [String] {
        ["metadata", apk.path] + (installer.map { ["--installer", $0] } ?? [])
    }

    public static func run(apk: URL, settings: AppSettings) -> [String] {
        var arguments = [
            "run", apk.path,
            "--width", String(settings.width),
            "--height", String(settings.height),
            "--log-level", settings.logLevel.rawValue,
        ]
        if let timeout = settings.timeoutSeconds {
            arguments += ["--timeout", String(timeout)]
        }
        if !settings.sandbox {
            arguments.append("--no-sandbox")
        }
        return arguments
    }

    /// Environment for `run`, merged over the runtime's own. Always set
    /// explicitly so a value inherited by the launcher never applies.
    public static func runEnvironment(settings: AppSettings) -> [String: String] {
        ["SHIRAZ_GUEST_LOADER": settings.nativeCode ? "1" : "0"]
    }

    /// In-tree MoltenVK ICD path when bootstrapped under `<runtimeRoot>/.local/lib`.
    public static func vulkanEnvironment(runtimeRoot: URL) -> [String: String] {
        let icd = runtimeRoot.appendingPathComponent(".local/lib/libMoltenVK.dylib").standardizedFileURL
        guard FileManager.default.isReadableFile(atPath: icd.path) else { return [:] }
        return ["SHIRAZ_VULKAN_ICD": icd.path]
    }

    /// Library and request directories for one session (LauncherInbox.java).
    public static func libraryEnvironment(apps: URL, inbox: URL) -> [String: String] {
        ["SHIRAZ_LAUNCHER_APPS": apps.path, "SHIRAZ_LAUNCHER_INBOX": inbox.path]
    }
}

/// The macOS identity the runtime gives a running APK
/// (`src/shiraz/darwin_app.py`, `prepare_app_bundle`): its Dock entry and
/// windows belong to an app with this bundle identifier.
public enum HostIdentity {
    public static func bundleIdentifier(for package: String) -> String {
        let digest = SHA256.hash(data: Data(package.utf8)).map { String(format: "%02x", $0) }.joined()
        return "org.shiraz.apk." + digest.prefix(24)
    }
}
