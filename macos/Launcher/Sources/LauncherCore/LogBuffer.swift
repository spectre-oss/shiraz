// SPDX-License-Identifier: GPL-3.0-only
import Foundation

public struct LogLine: Identifiable, Hashable, Sendable {
    public var id: Int
    public var text: String
    public var isError: Bool
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
        var text = raw.unicodeScalars.last == "\r" ? String(raw.unicodeScalars.dropLast()) : raw
        if text.count > maxLineLength { text = String(text.prefix(maxLineLength)) + "…" }
        lines.append(LogLine(id: nextID, text: text, isError: stream == .stderr))
        nextID += 1
        if lines.count > capacity { lines.removeFirst(lines.count - capacity) }
    }

    public var plainText: String { lines.map(\.text).joined(separator: "\n") }
}
