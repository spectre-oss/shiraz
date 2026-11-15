// SPDX-License-Identifier: GPL-3.0-only
import Darwin
import Foundation

/// The two files one embedded launch shares with the runtime: the live input
/// stream (`SHIRAZ_LIVE_INPUT`, include/shiraz/live_input.hpp) and the frame
/// file (`SHIRAZ_EMBED_FRAME`, include/shiraz/frame_export.hpp). Both live in
/// an owner-only directory created fresh for the launch.
public struct EmbedPaths: Sendable, Equatable {
    public let directory: URL
    public init(directory: URL) { self.directory = directory }
    public var input: URL { directory.appending(path: "input") }
    public var frame: URL { directory.appending(path: "frame") }

    public var environment: [String: String] {
        ["SHIRAZ_LIVE_INPUT": input.path, "SHIRAZ_EMBED_FRAME": frame.path]
    }

    /// Create `<root>/<UUID>/` (0700) with empty 0600 files. Never reuses a directory.
    public static func create(in root: URL) throws -> EmbedPaths {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let paths = EmbedPaths(directory: root.appending(path: UUID().uuidString, directoryHint: .isDirectory))
        guard mkdir(paths.directory.path, 0o700) == 0 else { throw EmbedError.create(errno) }
        for file in [paths.input, paths.frame] {
            let fd = open(file.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0o600)
            guard fd >= 0 else { throw EmbedError.create(errno) }
            close(fd)
        }
        return paths
    }

    public func remove() { try? FileManager.default.removeItem(at: directory) }
}

public enum EmbedError: Error, Equatable, LocalizedError {
    case create(Int32)
    case open(Int32)
    case notPrivate
    case write(Int32)

    public var errorDescription: String? {
        switch self {
        case .create(let code): "Cannot create the embedding files: \(String(cString: strerror(code)))"
        case .open(let code): "Cannot open the embedding files: \(String(cString: strerror(code)))"
        case .notPrivate: "The embedding files are not private to this user."
        case .write(let code): "Cannot send input to the app: \(String(cString: strerror(code)))"
        }
    }
}

/// One command line of the live input stream. Bounds match live_input.hpp.
public enum LiveCommand: Equatable, Sendable {
    case move(Int, Int), down(Int, Int), up(Int, Int)
    case wheel(x: Int, y: Int, dx: Float, dy: Float)
    case text(String)
    case keyDown(String), keyUp(String)
    case focus(Bool)
    case resize(Int, Int)
    case embed(Bool)
    case close

    static let maxCoordinate = 8192

    private static func clamp(_ value: Int) -> Int { min(max(value, 0), maxCoordinate) }
    private static func clampDelta(_ value: Float) -> Float { value.isFinite ? min(max(value, -128), 128) : 0 }

    /// `std::quoted` form: wrapped in quotes, `"` and `\` escaped.
    static func quoted(_ value: String) -> String {
        "\"" + value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"") + "\""
    }

    /// The line, without its newline, or nil when the command cannot be sent
    /// (empty or oversized text, control characters, impossible sizes).
    public var line: String? {
        switch self {
        case .move(let x, let y): "move \(Self.clamp(x)) \(Self.clamp(y))"
        case .down(let x, let y): "down \(Self.clamp(x)) \(Self.clamp(y))"
        case .up(let x, let y): "up \(Self.clamp(x)) \(Self.clamp(y))"
        case .wheel(let x, let y, let dx, let dy):
            "wheel \(Self.clamp(x)) \(Self.clamp(y)) \(Self.clampDelta(dx)) \(Self.clampDelta(dy))"
        case .text(let value):
            value.isEmpty || value.utf8.count > 31 || value.unicodeScalars.contains { $0.properties.generalCategory == .control }
                ? nil : "text " + Self.quoted(value)
        case .keyDown(let name):
            Self.validKey(name) ? "keydown " + Self.quoted(name) : nil
        case .keyUp(let name):
            Self.validKey(name) ? "keyup " + Self.quoted(name) : nil
        case .focus(let on): "focus \(on ? 1 : 0)"
        case .resize(let width, let height):
            width > 0 && height > 0 && width <= Self.maxCoordinate && height <= Self.maxCoordinate
                && width * height <= 16 * 1024 * 1024 ? "resize \(width) \(height)" : nil
        case .embed(let on): "embed \(on ? 1 : 0)"
        case .close: "close"
        }
    }

    private static func validKey(_ name: String) -> Bool {
        !name.isEmpty && name.utf8.count <= 32 && name.unicodeScalars.allSatisfy { $0.isASCII && $0.value >= 0x20 && $0.value != 0x7F }
    }

    /// Typed text as `text` commands of at most 31 UTF-8 bytes, split on
    /// character boundaries. Newlines and tabs become Return and Tab presses.
    public static func typing(_ value: String) -> [LiveCommand] {
        var commands: [LiveCommand] = []
        var chunk = ""
        func flush() { if !chunk.isEmpty { commands.append(.text(chunk)); chunk = "" } }
        for character in value {
            if character == "\n" || character == "\r" || character == "\r\n" || character == "\t" {
                flush()
                let key = character == "\t" ? "Tab" : "Return"
                commands += [.keyDown(key), .keyUp(key)]
                continue
            }
            let piece = String(character)
            guard !piece.unicodeScalars.contains(where: { $0.properties.generalCategory == .control }),
                  piece.utf8.count <= 31 else { continue }
            if chunk.utf8.count + piece.utf8.count > 31 { flush() }
            chunk += piece
        }
        flush()
        return commands
    }
}

/// Appends commands to the live input file. The runtime reads at most 8 MiB
/// in total; a long session is ended by the launcher before that.
public final class LiveInputWriter: @unchecked Sendable {
    private let fd: Int32
    private let lock = NSLock()
    public private(set) var bytesWritten = 0
    public static let limit = 8 * 1024 * 1024

    public init(url: URL) throws {
        fd = open(url.path, O_WRONLY | O_APPEND | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { throw EmbedError.open(errno) }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG, info.st_uid == geteuid(),
              info.st_mode & 0o077 == 0 else {
            close(fd)
            throw EmbedError.notPrivate
        }
    }

    deinit { close(fd) }

    /// Send commands in order. Unsendable commands are skipped; returns how many were written.
    @discardableResult
    public func send(_ commands: [LiveCommand]) throws -> Int {
        let lines = commands.compactMap(\.line)
        guard !lines.isEmpty else { return 0 }
        let data = Data((lines.joined(separator: "\n") + "\n").utf8)
        lock.lock()
        defer { lock.unlock() }
        guard bytesWritten + data.count <= Self.limit else { throw EmbedError.write(EFBIG) }
        let written = data.withUnsafeBytes { write(fd, $0.baseAddress, $0.count) }
        guard written == data.count else { throw EmbedError.write(written < 0 ? errno : EIO) }
        bytesWritten += data.count
        return lines.count
    }

    @discardableResult
    public func send(_ command: LiveCommand) throws -> Int { try send([command]) }
}

/// One frame copied out of the shared file: BGRA rows of `stride` bytes.
public struct EmbeddedFrame: Sendable {
    public let width: Int
    public let height: Int
    public let stride: Int
    public let revision: UInt64
    public let detached: Bool
    public let pixels: Data
}

/// Reads frames the runtime publishes (include/shiraz/frame_export.hpp).
public final class FrameReader: @unchecked Sendable {
    public static let magic: UInt32 = 0x465A_4853
    public static let version: UInt32 = 1
    public static let header = 4096
    public static let maxPixels = 16 * 1024 * 1024
    public static let capacity = header + maxPixels * 4
    public static let detachedFlag: UInt32 = 1

    private let fd: Int32
    private var map: UnsafeRawPointer?
    private var lastRevision: UInt64 = 0

    public init(url: URL) throws {
        fd = open(url.path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { throw EmbedError.open(errno) }
        var info = stat()
        guard fstat(fd, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG, info.st_uid == geteuid(),
              info.st_mode & 0o077 == 0 else {
            close(fd)
            throw EmbedError.notPrivate
        }
    }

    deinit {
        if let map { munmap(UnsafeMutableRawPointer(mutating: map), Self.capacity) }
        close(fd)
    }

    /// Map once the runtime has sized the file; until then there is no frame.
    private func mapped() -> UnsafeRawPointer? {
        if let map { return map }
        var info = stat()
        guard fstat(fd, &info) == 0, Int(info.st_size) == Self.capacity else { return nil }
        let pointer = mmap(nil, Self.capacity, PROT_READ, MAP_SHARED, fd, 0)
        guard let pointer, pointer != MAP_FAILED else { return nil }
        map = UnsafeRawPointer(pointer)
        return map
    }

    /// The newest frame when it changed since the last call, else nil.
    public func next() -> EmbeddedFrame? {
        guard let base = mapped() else { return nil }
        // An unlocked peek only decides whether to take the lock.
        guard base.loadUnaligned(fromByteOffset: 8, as: UInt64.self) != lastRevision else { return nil }
        while flock(fd, LOCK_SH) != 0 { if errno != EINTR { return nil } }
        defer { flock(fd, LOCK_UN) }
        guard base.loadUnaligned(fromByteOffset: 0, as: UInt32.self) == Self.magic,
              base.loadUnaligned(fromByteOffset: 4, as: UInt32.self) == Self.version else { return nil }
        let revision = base.loadUnaligned(fromByteOffset: 8, as: UInt64.self)
        let width = Int(base.loadUnaligned(fromByteOffset: 16, as: UInt32.self))
        let height = Int(base.loadUnaligned(fromByteOffset: 20, as: UInt32.self))
        let stride = Int(base.loadUnaligned(fromByteOffset: 24, as: UInt32.self))
        let flags = base.loadUnaligned(fromByteOffset: 28, as: UInt32.self)
        lastRevision = revision
        guard width > 0, height > 0, width <= 8192, height <= 8192, width * height <= Self.maxPixels,
              stride >= width * 4, stride * height <= Self.maxPixels * 4 else { return nil }
        let pixels = Data(bytes: base + Self.header, count: stride * height)
        return EmbeddedFrame(width: width, height: height, stride: stride, revision: revision,
                             detached: flags & Self.detachedFlag != 0, pixels: pixels)
    }
}

/// Where a pointer in the embedding view lands in the app's window, in the
/// window points the runtime was last resized to. The frame is aspect-fit
/// and centred; points outside it return nil. `y` grows downwards.
public enum EmbedGeometry {
    public static func fit(content: CGSize, in bounds: CGSize) -> CGRect {
        guard content.width > 0, content.height > 0, bounds.width > 0, bounds.height > 0 else { return .zero }
        let scale = min(bounds.width / content.width, bounds.height / content.height)
        let size = CGSize(width: content.width * scale, height: content.height * scale)
        return CGRect(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2,
                      width: size.width, height: size.height)
    }

    public static func appPoint(_ point: CGPoint, view: CGSize, window: CGSize) -> (x: Int, y: Int)? {
        let rect = fit(content: window, in: view)
        guard rect.width > 0, rect.contains(point) else { return nil }
        let x = Int(((point.x - rect.minX) / rect.width * window.width).rounded(.down))
        let y = Int(((point.y - rect.minY) / rect.height * window.height).rounded(.down))
        return (min(max(x, 0), Int(window.width) - 1), min(max(y, 0), Int(window.height) - 1))
    }

    /// The app window size for an area: the whole area, or with `orientation`
    /// set, the largest box of that phone shape (9:19.5) inside it.
    public static func windowSize(for area: CGSize, portrait: Bool?) -> (width: Int, height: Int) {
        guard let portrait else { return (max(1, Int(area.width)), max(1, Int(area.height))) }
        let shape = portrait ? CGSize(width: 9, height: 19.5) : CGSize(width: 19.5, height: 9)
        let rect = fit(content: shape, in: area)
        return (max(1, Int(rect.width)), max(1, Int(rect.height)))
    }
}
