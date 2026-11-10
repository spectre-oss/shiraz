// SPDX-License-Identifier: GPL-3.0-only
import Foundation

public enum LogLevel: String, Codable, CaseIterable, Sendable, Identifiable {
    case verbose = "VERBOSE", debug = "DEBUG", info = "INFO", warn = "WARN"
    case error = "ERROR", assert = "ASSERT", silent = "SILENT"
    public var id: String { rawValue }
}

/// Per-app launch settings. Every field maps to a flag the `shiraz run`
/// command already accepts; nothing here is stored without being used.
public struct AppSettings: Codable, Hashable, Sendable {
    public static let maxDimension = 8192
    public static let maxPixels = 16 * 1024 * 1024

    public var width: Int
    public var height: Int
    public var logLevel: LogLevel
    /// Kill the runtime after this many seconds. `nil` means no limit.
    public var timeoutSeconds: Int?

    public init(width: Int = 360, height: Int = 640, logLevel: LogLevel = .info, timeoutSeconds: Int? = nil) {
        self.width = width
        self.height = height
        self.logLevel = logLevel
        self.timeoutSeconds = timeoutSeconds
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
