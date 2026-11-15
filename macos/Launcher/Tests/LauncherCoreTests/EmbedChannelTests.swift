// SPDX-License-Identifier: GPL-3.0-only
import Darwin
import Foundation
import Testing
@testable import LauncherCore

@Suite struct EmbedChannelTests {
    private func root() -> URL {
        FileManager.default.temporaryDirectory.appending(path: "embed-\(UUID().uuidString)")
    }

    private func mode(_ url: URL) -> mode_t {
        var info = stat()
        stat(url.path, &info)
        return info.st_mode & 0o777
    }

    @Test func createsPrivateFreshFiles() throws {
        let base = root()
        defer { try? FileManager.default.removeItem(at: base) }
        let paths = try EmbedPaths.create(in: base)
        #expect(mode(paths.directory) == 0o700)
        #expect(mode(paths.input) == 0o600 && mode(paths.frame) == 0o600)
        #expect(paths.environment["SHIRAZ_LIVE_INPUT"] == paths.input.path)
        #expect(paths.environment["SHIRAZ_EMBED_FRAME"] == paths.frame.path)
        let other = try EmbedPaths.create(in: base)
        #expect(other.directory != paths.directory)
        paths.remove()
        #expect(!FileManager.default.fileExists(atPath: paths.directory.path))
    }

    @Test func commandLinesMatchRuntimeGrammar() {
        #expect(LiveCommand.down(12, 40).line == "down 12 40")
        #expect(LiveCommand.move(-5, 9000).line == "move 0 8192")
        #expect(LiveCommand.wheel(x: 3, y: 4, dx: 0, dy: -0.5).line == "wheel 3 4 0.0 -0.5")
        #expect(LiveCommand.wheel(x: 3, y: 4, dx: .infinity, dy: 900).line == "wheel 3 4 0.0 128.0")
        #expect(LiveCommand.resize(640, 360).line == "resize 640 360")
        #expect(LiveCommand.resize(0, 360).line == nil)
        #expect(LiveCommand.resize(8192, 8192).line == nil)
        #expect(LiveCommand.embed(false).line == "embed 0")
        #expect(LiveCommand.focus(true).line == "focus 1")
        #expect(LiveCommand.keyDown("AC Back").line == "keydown \"AC Back\"")
        #expect(LiveCommand.keyDown("bad\nkey").line == nil)
        #expect(LiveCommand.text("say \"hi\" \\o/").line == "text \"say \\\"hi\\\" \\\\o/\"")
        #expect(LiveCommand.text("").line == nil)
        #expect(LiveCommand.text("line\nbreak").line == nil)
        #expect(LiveCommand.text(String(repeating: "x", count: 32)).line == nil)
    }

    @Test func typingSplitsOnCharactersAndMapsReturn() {
        let typed = LiveCommand.typing("ab\ncd")
        #expect(typed == [.text("ab"), .keyDown("Return"), .keyUp("Return"), .text("cd")])
        let long = LiveCommand.typing(String(repeating: "é", count: 20))
        #expect(long.count == 2)
        for command in long {
            guard case .text(let chunk) = command else { Issue.record("not text"); continue }
            #expect(chunk.utf8.count <= 31)
        }
        #expect(LiveCommand.typing("\u{7}").isEmpty)
    }

    @Test func writerAppendsLinesInOrder() throws {
        let base = root()
        defer { try? FileManager.default.removeItem(at: base) }
        let paths = try EmbedPaths.create(in: base)
        let writer = try LiveInputWriter(url: paths.input)
        #expect(try writer.send([.down(1, 2), .text(""), .up(1, 2)]) == 2)
        try writer.send(.embed(true))
        let written = try String(contentsOf: paths.input, encoding: .utf8)
        #expect(written == "down 1 2\nup 1 2\nembed 1\n")
    }

    @Test func writerRefusesSharedFiles() throws {
        let base = root()
        defer { try? FileManager.default.removeItem(at: base) }
        let paths = try EmbedPaths.create(in: base)
        chmod(paths.input.path, 0o644)
        #expect(throws: EmbedError.notPrivate) { try LiveInputWriter(url: paths.input) }
    }

    /// Writes a frame the way frame_export.hpp does.
    private func publish(_ url: URL, width: UInt32, height: UInt32, revision: UInt64, flags: UInt32 = 0, fill: UInt8) throws {
        let handle = try FileHandle(forUpdating: url)
        defer { try? handle.close() }
        try handle.truncate(atOffset: UInt64(FrameReader.capacity))
        var header = Data(count: 36)
        func put(_ value: some Any, _ offset: Int) {
            withUnsafeBytes(of: value) { header.replaceSubrange(offset ..< offset + $0.count, with: $0) }
        }
        put(FrameReader.magic, 0); put(FrameReader.version, 4); put(revision, 8)
        put(width, 16); put(height, 20); put(width * 4, 24); put(flags, 28)
        try handle.seek(toOffset: UInt64(FrameReader.header))
        try handle.write(contentsOf: Data(repeating: fill, count: Int(width * height * 4)))
        try handle.seek(toOffset: 0)
        try handle.write(contentsOf: header)
    }

    @Test func readerReturnsOnlyNewFrames() throws {
        let base = root()
        defer { try? FileManager.default.removeItem(at: base) }
        let paths = try EmbedPaths.create(in: base)
        let reader = try FrameReader(url: paths.frame)
        #expect(reader.next() == nil) // runtime has not sized the file yet
        try publish(paths.frame, width: 4, height: 2, revision: 1, fill: 0x7F)
        let frame = try #require(reader.next())
        #expect(frame.width == 4 && frame.height == 2 && frame.stride == 16 && frame.revision == 1)
        #expect(frame.pixels.count == 32 && frame.pixels.allSatisfy { $0 == 0x7F })
        #expect(!frame.detached)
        #expect(reader.next() == nil)
        try publish(paths.frame, width: 2, height: 4, revision: 2, flags: FrameReader.detachedFlag, fill: 1)
        let rotated = try #require(reader.next())
        #expect(rotated.width == 2 && rotated.height == 4 && rotated.detached)
    }

    @Test func readerRejectsImpossibleHeaders() throws {
        let base = root()
        defer { try? FileManager.default.removeItem(at: base) }
        let paths = try EmbedPaths.create(in: base)
        let reader = try FrameReader(url: paths.frame)
        try publish(paths.frame, width: 9000, height: 1, revision: 1, fill: 0)
        #expect(reader.next() == nil)
    }

    @Test func pointsMapIntoTheFittedFrame() throws {
        let view = CGSize(width: 800, height: 600)
        let window = CGSize(width: 360, height: 640)
        let rect = EmbedGeometry.fit(content: window, in: view)
        #expect(rect.height == 600 && abs(rect.width - 337.5) < 0.01)
        #expect(EmbedGeometry.appPoint(CGPoint(x: 10, y: 300), view: view, window: window) == nil)
        let centre = try #require(EmbedGeometry.appPoint(CGPoint(x: 400, y: 300), view: view, window: window))
        #expect(centre.x == 180 && centre.y == 320)
        let corner = try #require(EmbedGeometry.appPoint(CGPoint(x: rect.maxX - 0.25, y: rect.maxY - 0.25), view: view, window: window))
        #expect(corner.x == 359 && corner.y == 639)
    }

    @Test func windowSizeFollowsAreaOrPhoneShape() {
        #expect(EmbedGeometry.windowSize(for: CGSize(width: 700, height: 500), portrait: nil) == (700, 500))
        let portrait = EmbedGeometry.windowSize(for: CGSize(width: 1000, height: 780), portrait: true)
        #expect(portrait.height == 780 && portrait.width == 360)
        let landscape = EmbedGeometry.windowSize(for: CGSize(width: 1000, height: 780), portrait: false)
        #expect(landscape.width == 1000 && landscape.height == 461)
    }
}
