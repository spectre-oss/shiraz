// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// When an APK may replace an app already in the library.
///
/// The new APK must be one the signed F-Droid index lists for that package
/// (its SHA-256 is in the catalog), so F-Droid has verified its signature.
/// The installed copy's signing certificate must be the one the index names
/// for that release, so an update keeps the signer, as on Android. The
/// installed certificate is read, not verified: that APK is already trusted.
public enum UpdatePolicy {
    public static func refusal(
        installed: AppEntry, installedSigners: [String], candidate: AppEntry, catalog: CatalogApp?
    ) -> String? {
        let name = installed.displayName
        guard candidate.packageName == installed.packageName else {
            return "The APK is \(candidate.packageName), not \(installed.packageName)."
        }
        if let new = candidate.versionCode.flatMap({ Int($0) }), let old = installed.versionCode.flatMap({ Int($0) }),
           new <= old {
            return "\(name) \(installed.versionName ?? String(old)) is already installed; this APK is not newer."
        }
        guard let catalog, catalog.packageName == installed.packageName else {
            return "\(name) is not in the F-Droid catalog, so the update cannot be checked."
        }
        let digest = candidate.sha256.lowercased()
        guard let release = catalog.releases.first(where: { $0.sha256 == digest }) else {
            return "This \(name) APK is not one the signed F-Droid index lists."
        }
        guard installedSigners.map({ $0.lowercased() }).contains(release.signer) else {
            return "The installed \(name) was signed by someone else. Remove it first to install F-Droid's build."
        }
        return nil
    }
}
