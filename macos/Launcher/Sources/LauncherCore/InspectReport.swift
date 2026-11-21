// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// The subset of `python3 -m shiraz inspect` output the launcher uses.
/// The CLI prints a much larger report; unknown keys are ignored.
public struct InspectReport: Decodable, Sendable, Equatable {
    public struct Manifest: Decodable, Sendable, Equatable {
        public var package: String
        public var versionCode: String?
        public var versionCodeMajor: String?
        public var versionName: String?
        public var minSdk: String?
        public var targetSdk: String?
        public var permissions: [String]

        enum CodingKeys: String, CodingKey {
            case package
            case versionCode = "version_code"
            case versionCodeMajor = "version_code_major"
            case versionName = "version_name"
            case minSdk = "min_sdk"
            case targetSdk = "target_sdk"
            case permissions
        }

        public init(from decoder: any Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            package = try c.decode(String.self, forKey: .package)
            versionCode = try c.decodeIfPresent(String.self, forKey: .versionCode)
            versionCodeMajor = try c.decodeIfPresent(String.self, forKey: .versionCodeMajor)
            versionName = try c.decodeIfPresent(String.self, forKey: .versionName)
            minSdk = try c.decodeIfPresent(String.self, forKey: .minSdk)
            targetSdk = try c.decodeIfPresent(String.self, forKey: .targetSdk)
            permissions = try c.decodeIfPresent([String].self, forKey: .permissions) ?? []
        }

        func longVersionCode() throws -> UInt64 {
            func part(_ text: String?) throws -> UInt64 {
                let value = text.flatMap { $0.isEmpty ? nil : $0 } ?? "0"
                let hex = value.hasPrefix("0x")
                guard let number = UInt64(hex ? String(value.dropFirst(2)) : value, radix: hex ? 16 : 10),
                      number <= 0x7fffffff else {
                    throw LibraryError.inspectFailed("Invalid or unsupported APK version code.")
                }
                return number
            }
            return try (part(versionCodeMajor) << 32) | part(versionCode)
        }
    }

    public var apkSha256: String
    public var manifest: Manifest
    public var nativeAbis: [String]

    enum CodingKeys: String, CodingKey {
        case apkSha256 = "apk_sha256"
        case manifest
        case nativeAbis = "native_abis"
    }

    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        apkSha256 = try c.decode(String.self, forKey: .apkSha256)
        manifest = try c.decode(Manifest.self, forKey: .manifest)
        nativeAbis = try c.decodeIfPresent([String].self, forKey: .nativeAbis) ?? []
    }

    public static func decode(_ data: Data) throws -> InspectReport {
        try JSONDecoder().decode(InspectReport.self, from: data)
    }
}
