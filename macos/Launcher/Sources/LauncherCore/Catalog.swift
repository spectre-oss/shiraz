// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// `catalog.json` written by `python3 -m shiraz fdroid sync`: the verified
/// f-droid.org index reduced to one APK per app for this host.
/// Decoding is lenient per field: an odd value drops that field, not the app.
public struct Catalog: Decodable, Sendable, Equatable {
    public struct Repo: Decodable, Sendable, Equatable {
        public var name: String?
        public var address: String?
        /// Index publication time.
        public var timestamp: Date?

        enum CodingKeys: String, CodingKey { case name, address, timestamp }

        public init(from decoder: any Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = try? c.decodeIfPresent(String.self, forKey: .name)
            address = try? c.decodeIfPresent(String.self, forKey: .address)
            timestamp = (try? c.decodeIfPresent(Int64.self, forKey: .timestamp)).flatMap(Catalog.date(milliseconds:))
        }
    }

    public var repo: Repo
    public var apps: [CatalogApp]

    enum CodingKeys: String, CodingKey { case repo, apps }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        repo = try c.decode(Repo.self, forKey: .repo)
        // One malformed app must not hide the other few thousand.
        apps = try c.decode([Lenient<CatalogApp>].self, forKey: .apps).compactMap(\.value)
    }

    public static func decode(_ data: Data) throws -> Catalog {
        try JSONDecoder().decode(Catalog.self, from: data)
    }

    static func date(milliseconds: Int64?) -> Date? {
        guard let milliseconds, milliseconds > 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(milliseconds) / 1000)
    }

    /// Categories in use, by name.
    public var categories: [String] {
        Set(apps.flatMap(\.categories)).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }
}

private struct Lenient<Value: Decodable>: Decodable {
    var value: Value?
    init(from decoder: any Decoder) throws { value = try? Value(from: decoder) }
}

public struct CatalogApp: Decodable, Identifiable, Hashable, Sendable {
    public var id: String { packageName }
    public var packageName: String
    public var name: String
    public var summary: String
    public var description: String
    public var license: String?
    public var authorName: String?
    public var categories: [String]
    public var antiFeatures: [String]
    public var iconURL: URL?
    public var screenshots: [URL]
    public var webURL: URL?
    public var sourceCode: URL?
    public var lastUpdated: Date?
    public var added: Date?
    public var versionName: String?
    public var versionCode: Int?
    public var size: Int64?
    public var nativeCode: [String]
    public var minSdk: Int?
    /// The ABI and API level fit this host. Not a claim that the app works.
    public var compatible: Bool
    /// Why no APK fits, when `compatible` is false.
    public var reason: String?
    /// Listed APKs, newest first, for checking updates against the signed index.
    public var releases: [CatalogRelease]

    enum CodingKeys: String, CodingKey {
        case packageName, name, summary, description, license, authorName, categories, antiFeatures
        case iconURL, screenshots, webURL, sourceCode, lastUpdated, added, versionName, versionCode
        case size, nativeCode = "nativecode", minSdk, compatible, reason, releases
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        packageName = try c.decode(String.self, forKey: .packageName)
        guard LibraryStore.isValidPackageName(packageName) else {
            throw DecodingError.dataCorruptedError(forKey: .packageName, in: c, debugDescription: "Invalid package name")
        }
        let label = (try? c.decodeIfPresent(String.self, forKey: .name))
        name = label.flatMap { $0.isEmpty ? nil : $0 } ?? packageName
        summary = (try? c.decodeIfPresent(String.self, forKey: .summary)) ?? ""
        description = (try? c.decodeIfPresent(String.self, forKey: .description)) ?? ""
        license = (try? c.decodeIfPresent(String.self, forKey: .license))
        authorName = (try? c.decodeIfPresent(String.self, forKey: .authorName))
        categories = (try? c.decodeIfPresent([String].self, forKey: .categories)) ?? []
        antiFeatures = (try? c.decodeIfPresent([String].self, forKey: .antiFeatures)) ?? []
        iconURL = Self.https((try? c.decodeIfPresent(String.self, forKey: .iconURL)))
        screenshots = ((try? c.decodeIfPresent([String].self, forKey: .screenshots)) ?? []).compactMap(Self.https)
        webURL = Self.https((try? c.decodeIfPresent(String.self, forKey: .webURL)))
        sourceCode = Self.https((try? c.decodeIfPresent(String.self, forKey: .sourceCode)))
        lastUpdated = Catalog.date(milliseconds: (try? c.decodeIfPresent(Int64.self, forKey: .lastUpdated)))
        added = Catalog.date(milliseconds: (try? c.decodeIfPresent(Int64.self, forKey: .added)))
        versionName = (try? c.decodeIfPresent(String.self, forKey: .versionName))
        versionCode = (try? c.decodeIfPresent(Int.self, forKey: .versionCode))
        size = (try? c.decodeIfPresent(Int64.self, forKey: .size))
        nativeCode = (try? c.decodeIfPresent([String].self, forKey: .nativeCode)) ?? []
        minSdk = Self.integer(c, .minSdk)
        compatible = (try? c.decodeIfPresent(Bool.self, forKey: .compatible)) ?? false
        reason = (try? c.decodeIfPresent(String.self, forKey: .reason))
        releases = ((try? c.decodeIfPresent([Lenient<CatalogRelease>].self, forKey: .releases)) ?? []).compactMap(\.value)
    }

    /// Remote images and links are shown only over HTTPS.
    private static func https(_ string: String?) -> URL? {
        guard let string, string.count <= 2048, let url = URL(string: string), url.scheme == "https", url.host != nil else {
            return nil
        }
        return url
    }

    /// The index stores minSdkVersion as a number or a numeric string.
    private static func integer(_ c: KeyedDecodingContainer<CodingKeys>, _ key: CodingKeys) -> Int? {
        if let value = try? c.decodeIfPresent(Int.self, forKey: key) { return value }
        return ((try? c.decodeIfPresent(String.self, forKey: key))).flatMap { Int($0) }
    }

    /// Where this catalog app stands against the library.
    public func status(in library: [AppEntry]) -> CatalogStatus {
        guard let entry = library.first(where: { $0.packageName == packageName }) else { return .notInLibrary }
        if let available = versionCode, let current = entry.versionCode.flatMap({ Int($0) }), available > current {
            return .updateAvailable(from: entry.versionName ?? String(current))
        }
        return .inLibrary
    }

    /// Case-insensitive match on name, summary, package name and author.
    public func matches(_ query: String) -> Bool {
        let query = query.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return true }
        return [name, summary, packageName, authorName ?? ""].contains { $0.localizedCaseInsensitiveContains(query) }
    }
}

/// One APK the signed index lists: its SHA-256 and the SHA-256 of its
/// signing certificate.
public struct CatalogRelease: Decodable, Hashable, Sendable {
    public var versionCode: Int
    public var sha256: String
    public var signer: String

    public init(versionCode: Int, sha256: String, signer: String) {
        self.versionCode = versionCode
        self.sha256 = sha256
        self.signer = signer
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        versionCode = try c.decode(Int.self, forKey: .versionCode)
        sha256 = try c.decode(String.self, forKey: .sha256).lowercased()
        signer = try c.decode(String.self, forKey: .signer).lowercased()
        guard Self.isDigest(sha256), Self.isDigest(signer) else {
            throw DecodingError.dataCorruptedError(forKey: .sha256, in: c, debugDescription: "Not a SHA-256 digest")
        }
    }

    enum CodingKeys: String, CodingKey { case versionCode, sha256, signer }

    static func isDigest(_ text: String) -> Bool { text.wholeMatch(of: /[0-9a-f]{64}/) != nil }
}

public enum CatalogStatus: Equatable, Sendable {
    case notInLibrary
    case inLibrary
    case updateAvailable(from: String)
}

/// `python3 -m shiraz fdroid fetch` prints this after the SHA-256 check.
public struct FetchedAPK: Decodable, Sendable, Equatable {
    public var packageName: String
    public var path: String
    public var sha256: String
    public var size: Int64
    public var versionName: String?
}
