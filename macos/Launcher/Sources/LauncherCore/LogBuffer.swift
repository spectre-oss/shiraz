// SPDX-License-Identifier: GPL-3.0-only
import Foundation

public struct LogLine: Identifiable, Hashable, Sendable {
    public var id: Int
    public var text: String
    /// True when the line came from stderr. The runtime writes all of its
    /// diagnostics there, so this is not a severity; see `level`.
    public var isError: Bool

    public enum Level: Int, Comparable, Sendable {
        case debug, info, warning, error
        public static func < (a: Level, b: Level) -> Bool { a.rawValue < b.rawValue }
    }

    /// Severity read from the line itself: Android logcat priority
    /// (`E/Tag(`), Shiraz markers, and Python failures.
    public var level: Level { Self.level(of: text) }

    public static func level(of text: String) -> Level {
        let trimmed = text.drop { $0 == " " }
        if trimmed.count > 2, trimmed.dropFirst().first == "/" {
            switch trimmed.first {
            case "E", "F", "A": return .error
            case "W": return .warning
            case "D", "V": return .debug
            case "I": return .info
            default: break
            }
        }
        if trimmed.hasPrefix("Traceback") || trimmed.hasPrefix("FATAL")
            || trimmed.hasPrefix("SHIRAZ_ERROR") || trimmed.contains("Exception:")
            || trimmed.range(of: #"^[A-Za-z_.]*Error\b"#, options: .regularExpression) != nil {
            return .error
        }
        if trimmed.hasPrefix("SHIRAZ_UNSUPPORTED") || trimmed.hasPrefix("SHIRAZ_HOST_UNAVAILABLE")
            || trimmed.hasPrefix("WARNING") || trimmed.hasPrefix("Warning:") {
            return .warning
        }
        return .info
    }
}

/// Bounded, line-oriented log. Splits chunks on newlines, keeps partial
/// lines until they complete, and drops the oldest lines past `capacity`.
public struct LogBuffer: Sendable {
    public let capacity: Int
    public let maxLineLength: Int
    public private(set) var lines: [LogLine] = []
    private var nextID = 0
    private var partial: [StreamKind: String] = [:]

    public init(capacity: Int = 2000, maxLineLength: Int = 4096) {
        self.capacity = capacity
        self.maxLineLength = maxLineLength
    }

    public mutating func append(_ data: Data, stream: StreamKind) {
        // Split on unicode scalars: "\r\n" is one Character in Swift, so
        // searching Characters for "\n" would never find a CRLF line end.
        let text = (partial.removeValue(forKey: stream) ?? "") + String(decoding: data, as: UTF8.self)
        var current = String.UnicodeScalarView()
        for scalar in text.unicodeScalars {
            if scalar == "\n" {
                push(String(current), stream: stream)
                current = String.UnicodeScalarView()
            } else {
                current.append(scalar)
            }
        }
        let rest = String(current)
        if rest.count > maxLineLength {
            push(rest, stream: stream)
        } else if !rest.isEmpty {
            partial[stream] = rest
        }
    }

    /// Emit unfinished lines, for example when the process exits.
    public mutating func flush() {
        for (stream, text) in partial where !text.isEmpty { push(text, stream: stream) }
        partial = [:]
    }

    public mutating func appendNotice(_ text: String) {
        push(text, stream: .stdout)
    }

    private mutating func push(_ raw: String, stream: StreamKind) {
        // Drop control characters (NUL padding, stray CR, escape bytes) that
        // would otherwise render as garbage and hide logcat prefixes; keep tabs.
        var scalars = String.UnicodeScalarView()
        scalars.append(contentsOf: raw.unicodeScalars.filter { $0 == "\t" || !($0.value < 0x20 || $0.value == 0x7F) })
        var text = String(scalars)
        if text.count > maxLineLength { text = String(text.prefix(maxLineLength)) + "…" }
        lines.append(LogLine(id: nextID, text: text, isError: stream == .stderr))
        nextID += 1
        if lines.count > capacity { lines.removeFirst(lines.count - capacity) }
    }

    public var plainText: String { lines.map(\.text).joined(separator: "\n") }
}
