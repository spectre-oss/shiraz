// SPDX-License-Identifier: GPL-3.0-only
import Foundation

public enum LogLevel: String, Codable, CaseIterable, Sendable, Identifiable {
    case verbose = "VERBOSE", debug = "DEBUG", info = "INFO", warn = "WARN"
    case error = "ERROR", assert = "ASSERT", silent = "SILENT"
    public var id: String { rawValue }
}

/// Per-app launch settings. Every field maps to a flag or environment
/// variable the `shiraz run` command already accepts; nothing here is stored
/// without being used.
public struct AppSettings: Codable, Hashable, Sendable {
    public static let maxDimension = 8192
    public static let maxPixels = 16 * 1024 * 1024

    public var width: Int
    public var height: Int
    public var logLevel: LogLevel
    /// Kill the runtime after this many seconds. `nil` means no limit.
    public var timeoutSeconds: Int?
    /// Load the APK's arm64-v8a libraries through the experimental guest ELF
    /// loader (`SHIRAZ_GUEST_LOADER=1`). Off by default while the loader is
    /// unfinished; without it, native calls fail with UnsatisfiedLinkError.
    public var nativeCode: Bool

    public init(width: Int = 360, height: Int = 640, logLevel: LogLevel = .info, timeoutSeconds: Int? = nil,
                nativeCode: Bool = false) {
        self.width = width
        self.height = height
        self.logLevel = logLevel
        self.timeoutSeconds = timeoutSeconds
        self.nativeCode = nativeCode
    }

    enum CodingKeys: String, CodingKey { case width, height, logLevel, timeoutSeconds, nativeCode }

    // entry.json files written before `nativeCode` existed still decode (as off).
    public init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        width = try c.decode(Int.self, forKey: .width)
        height = try c.decode(Int.self, forKey: .height)
        logLevel = try c.decode(LogLevel.self, forKey: .logLevel)
        timeoutSeconds = try c.decodeIfPresent(Int.self, forKey: .timeoutSeconds)
        nativeCode = try c.decodeIfPresent(Bool.self, forKey: .nativeCode) ?? false
    }

    /// Mirrors the limits `shiraz run` enforces, so the UI can refuse early.
    public var validationError: String? {
        if width < 1 || height < 1 { return "Width and height must be at least 1." }
        if width > Self.maxDimension || height > Self.maxDimension {
            return "Width and height must not exceed \(Self.maxDimension)."
        }
        if width * height > Self.maxPixels { return "The window may not exceed 16 million pixels." }
        if let timeoutSeconds, timeoutSeconds < 1 { return "Timeout must be at least 1 second." }
        return nil
    }
}
