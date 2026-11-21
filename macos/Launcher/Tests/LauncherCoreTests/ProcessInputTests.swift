// SPDX-License-Identifier: GPL-3.0-only
import Foundation
import Testing
@testable import LauncherCore

@Suite struct ProcessInputTests {
    private enum Event: Sendable { case output(Data), exit(LauncherCore.ExitStatus) }

    @Test func talksToRealSharedHostOverPrivatePipes() async throws {
        let root = try #require(RuntimeLocation.detectRoot(executable: nil,
                                                           currentDirectory: URL(filePath: #filePath).deletingLastPathComponent()))
        let directory = FileManager.default.temporaryDirectory.appending(path: "host-input-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let runtime = RuntimeLocation(root: root, python: try #require(RuntimeLocation.detectPython()))
        let pipe = Pipe()
        let events = AsyncStream<Event>.makeStream()
        let process = try ProcessRunner.spawn(executable: runtime.python,
                                              arguments: runtime.arguments(["serve-session", "--registry", directory.path]),
                                              environment: runtime.environment, workingDirectory: root, standardInput: pipe.fileHandleForReading,
                                              onOutput: { kind, data in if kind == .stdout { events.continuation.yield(.output(data)) } },
                                              onExit: { status in events.continuation.yield(.exit(status)); events.continuation.finish() })
        try pipe.fileHandleForReading.close()
        try pipe.fileHandleForWriting.write(contentsOf: Data("{\"op\":\"status\"}\n{\"op\":\"shutdown\"}\n".utf8))
        try pipe.fileHandleForWriting.close()
        let watchdog = Task {
            try await Task.sleep(for: .seconds(10))
            process.terminate(grace: 0)
        }
        defer { watchdog.cancel() }
        var output = Data()
        for await event in events.stream {
            switch event {
            case .output(let data): output.append(data)
            case .exit(let status): #expect(status.succeeded)
            }
        }
        let lines = output.split(separator: 10)
        #expect(lines.count == 3)
        let replies = try lines.map { try JSONSerialization.jsonObject(with: Data($0)) as? [String: Any] }
        let ready = try #require(replies.first ?? nil)
        #expect(ready["ready"] as? Bool == true)
        #expect(replies.dropFirst().allSatisfy { $0?["ok"] as? Bool == true })
    }

    @Test func childReceivesPrivateInputAndExitsOnEOF() async throws {
        let root = FileManager.default.temporaryDirectory.appending(path: "process-input-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let output = root.appending(path: "received.bin")
        let pipe = Pipe()
        let completion = AsyncStream<LauncherCore.ExitStatus>.makeStream()
        let process = try ProcessRunner.spawn(
            executable: try #require(RuntimeLocation.detectPython()),
            arguments: ["-c", "import pathlib,sys; pathlib.Path(sys.argv[1]).write_bytes(sys.stdin.buffer.read())", output.path],
            environment: ProcessInfo.processInfo.environment, workingDirectory: root,
            standardInput: pipe.fileHandleForReading,
            onOutput: { _, _ in },
            onExit: { status in completion.continuation.yield(status); completion.continuation.finish() })
        try pipe.fileHandleForReading.close()
        // The child's copy of stdin stays valid after the parent's read end closes.
        let payload = Data("{\"op\":\"status\"}\n{\"op\":\"shutdown\"}\n".utf8)
        try pipe.fileHandleForWriting.write(contentsOf: payload)
        try pipe.fileHandleForWriting.close()
        // A leaked writer in the child would prevent EOF. Bound failure cleanup.
        let watchdog = Task {
            try await Task.sleep(for: .seconds(5))
            process.terminate(grace: 0)
        }
        defer { watchdog.cancel() }
        var statuses: [LauncherCore.ExitStatus] = []
        for await status in completion.stream { statuses.append(status) }
        #expect(statuses.count == 1)
        #expect(statuses.first?.succeeded == true)
        #expect(try Data(contentsOf: output) == payload)
    }
}
