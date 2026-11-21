// SPDX-License-Identifier: GPL-3.0-only
import Darwin
import Foundation

public struct HostLaunchRequester: Encodable, Sendable {
    public let package: String
    public let launch_id: String
    public init(package: String, launchID: String) {
        self.package = package; launch_id = launchID
    }
}

public struct HostCompletion: Decodable, Sendable {
    public let package: String
    public let launch_id: String
    public let exit_status: Int
    public let stdout: String
    public let stderr: String
}

public struct HostPackageIdentity: Decodable, Sendable {
    public let package: String
    public let uid: Int
    public let sha256: String
}

public struct HostReply: Decodable, Sendable {
    public let ready: Bool?
    public let `protocol`: Int?
    public let ok: Bool?
    public let error: String?
    public let pid: Int?
    public let launch_id: String?
    public let inbox: String?
    public let stdout: String?
    public let stderr: String?
    public let stopped: Bool?
    public let retired: Bool?
    public let quiesced: Bool?
    public let resumed: Bool?
    public let found: Bool?
    public let identity: HostPackageIdentity?
    public let running: [String]?
    public let finished: [HostCompletion]?
}

/// One private host connection. Overlapping requests reject rather than reorder.
public actor SharedHostClient {
    public nonisolated let registry: URL
    private struct LaunchSettings: Encodable {
        let width: Int
        let height: Int
        let log_level: String
        let timeout: Int?
        let native_code: Bool
        /// Launcher embedding files (EmbedPaths); omitted for a windowed launch.
        let embed: [String: String]?
        init(_ value: AppSettings, embed paths: EmbedPaths? = nil) {
            width = value.width; height = value.height; log_level = value.logLevel.rawValue
            timeout = value.timeoutSeconds; native_code = value.nativeCode
            embed = paths.map { ["input": $0.input.path, "frame": $0.frame.path] }
        }
    }
    private struct Request: Encodable {
        let op: String
        var package: String?
        var sha256: String?
        var launch_id: String?
        var uid: Int?
        var settings: LaunchSettings?
        var requester: HostLaunchRequester?
    }
    private enum Event: Sendable { case output(Data), exit }
    private var input: FileHandle?
    private var process: RunningProcess?
    private var reader: Task<Void, Never>?
    private var timer: Task<Void, Never>?
    private var pending: CheckedContinuation<HostReply, any Error>?
    private var pendingOperation = "ready"
    private var buffer = Data()
    private var closed = false
    private let timeout: Duration

    private init(timeout: Duration, registry: URL) {
        self.timeout = timeout
        self.registry = registry.resolvingSymlinksInPath().standardizedFileURL
    }

    deinit { try? input?.close(); reader?.cancel(); timer?.cancel() }

    public static func connect(runtime: RuntimeLocation, registry: URL, library: URL? = nil, timeout: Duration = .seconds(90)) async throws -> SharedHostClient {
        let client = SharedHostClient(timeout: timeout, registry: registry)
        try await client.open(runtime: runtime, registry: registry, library: library)
        return client
    }

    private func open(runtime: RuntimeLocation, registry: URL, library: URL?) async throws {
        let pipe = Pipe()
        guard fcntl(pipe.fileHandleForWriting.fileDescriptor, F_SETNOSIGPIPE, 1) == 0 else {
            throw SpawnError(message: "Cannot configure host input pipe.")
        }
        input = pipe.fileHandleForWriting
        let events = AsyncStream<Event>.makeStream(bufferingPolicy: .bufferingOldest(16))
        do {
            process = try ProcessRunner.spawn(executable: runtime.python,
                                              arguments: runtime.arguments(["serve-session", "--registry", registry.path] + (library.map { ["--library", $0.path] } ?? [])),
                                              environment: runtime.environment, workingDirectory: runtime.root,
                                              standardInput: pipe.fileHandleForReading,
                                              onOutput: { kind, data in
                                                  if kind == .stdout, case .dropped = events.continuation.yield(.output(data)) {
                                                      events.continuation.finish()
                                                  }
                                              }, onExit: { _ in events.continuation.yield(.exit); events.continuation.finish() })
            try pipe.fileHandleForReading.close()
            reader = Task { [weak self] in
                for await event in events.stream { await self?.receive(event) }
                await self?.fail("Shared host channel ended.")
            }
            _ = try await exchange(nil)
            guard !closed else { throw SpawnError(message: "Shared host closed during startup.") }
        } catch { fail("Shared host startup failed."); throw error }
    }

    public func start(package: String, sha256: String, settings: AppSettings = AppSettings(), requester: HostLaunchRequester? = nil,
                      embed: EmbedPaths? = nil) async throws -> HostReply {
        if let problem = settings.validationError { throw SpawnError(message: problem) }
        guard (settings.timeoutSeconds ?? 1) <= Int32.max else { throw SpawnError(message: "Launch timeout is too large.") }
        return try await exchange(Request(op: "start", package: package, sha256: sha256, settings: LaunchSettings(settings, embed: embed), requester: requester))
    }
    public func stop(package: String, launchID: String) async throws -> HostReply {
        try await exchange(Request(op: "stop", package: package, launch_id: launchID))
    }
    public func identity(package: String) async throws -> HostReply {
        let reply = try await exchange(Request(op: "identity", package: package))
        if let identity = reply.identity, identity.package != package {
            fail("Shared host returned a different package identity.")
            throw SpawnError(message: "Shared host returned a different package identity.")
        }
        return reply
    }
    public func retire(package: String, sha256: String, uid: Int) async throws -> HostReply {
        guard (10000 ... 19999).contains(uid), sha256.wholeMatch(of: /[0-9a-f]{64}/) != nil else {
            throw SpawnError(message: "Invalid package retirement identity.")
        }
        return try await exchange(Request(op: "retire", package: package, sha256: sha256, uid: uid))
    }
    public func status() async throws -> HostReply { try await exchange(Request(op: "status")) }
    public func quiesce(package: String, sha256: String, uid: Int) async throws -> HostReply {
        try await generationOperation("quiesce", package: package, sha256: sha256, uid: uid)
    }
    public func resume(package: String, sha256: String, uid: Int) async throws -> HostReply {
        try await generationOperation("resume", package: package, sha256: sha256, uid: uid)
    }
    private func generationOperation(_ op: String, package: String, sha256: String, uid: Int) async throws -> HostReply {
        guard (10000 ... 19999).contains(uid), sha256.wholeMatch(of: /[0-9a-f]{64}/) != nil else {
            throw SpawnError(message: "Invalid package identity.")
        }
        return try await exchange(Request(op: op, package: package, sha256: sha256, uid: uid))
    }
    public func shutdown() async throws -> HostReply {
        let reply = try await exchange(Request(op: "shutdown"))
        close()
        return reply
    }
    public func close() { fail("Shared host connection closed.") }

    private func exchange(_ request: Request?) async throws -> HostReply {
        guard !closed, pending == nil else { throw SpawnError(message: "Shared host is closed or busy.") }
        try Task.checkCancellation()
        var data = try request.map { try JSONEncoder().encode($0) }
        if data != nil {
            data!.append(10)
            guard data!.count <= 4096 else { throw SpawnError(message: "Host request is too large.") }
        }
        pendingOperation = request?.op ?? "ready"
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                pending = continuation
                timer = Task { [weak self, timeout] in
                    do { try await Task.sleep(for: timeout) } catch { return }
                    await self?.fail("Shared host reply timed out.")
                }
                if let data {
                    do { try input?.write(contentsOf: data) }
                    catch { fail("Cannot write to shared host.") }
                }
            }
        } onCancel: { Task { await self.close() } }
    }

    private func receive(_ event: Event) {
        guard !closed else { return }
        switch event {
        case .exit: process = nil; fail("Shared host exited.")
        case .output(let data):
            guard buffer.count + data.count <= 131072 else { fail("Host reply is too large."); return }
            buffer.append(data)
            while let newline = buffer.firstIndex(of: 10) {
                let frame = Data(buffer[..<newline])
                buffer.removeSubrange(...newline)
                guard let continuation = pending,
                      let reply = try? JSONDecoder().decode(HostReply.self, from: frame), valid(reply) else {
                    fail("Invalid shared host reply."); return
                }
                pending = nil
                timer?.cancel(); timer = nil
                continuation.resume(returning: reply)
            }
        }
    }

    private func valid(_ reply: HostReply) -> Bool {
        if pendingOperation == "ready" { return reply.ready == true && reply.protocol == 1 && reply.ok == nil }
        guard reply.ready == nil, let ok = reply.ok, (reply.finished?.count ?? 0) <= 16 else { return false }
        if !ok { return reply.error != nil && reply.error!.count <= 512 }
        guard reply.finished != nil else { return false }
        switch pendingOperation {
        case "start": return (reply.pid ?? 0) > 0 && reply.launch_id?.wholeMatch(of: /[0-9a-f]{32}/) != nil
        case "stop": return reply.stopped != nil
        case "retire": return reply.retired != nil
        case "quiesce": return reply.quiesced != nil
        case "resume": return reply.resumed != nil
        case "identity":
            guard let found = reply.found else { return false }
            if !found { return reply.identity == nil }
            guard let identity = reply.identity else { return false }
            return (10000 ... 19999).contains(identity.uid) && identity.sha256.wholeMatch(of: /[0-9a-f]{64}/) != nil
        case "status": return reply.running != nil && reply.running!.count <= 16
        case "shutdown": return true
        default: return false
        }
    }

    private func fail(_ message: String) {
        guard !closed else { return }
        closed = true
        try? input?.close(); input = nil
        timer?.cancel(); timer = nil
        let continuation = pending; pending = nil
        buffer.removeAll()
        continuation?.resume(throwing: SpawnError(message: message))
        // EOF lets the host reap its app groups and broker before exiting.
    }
}
