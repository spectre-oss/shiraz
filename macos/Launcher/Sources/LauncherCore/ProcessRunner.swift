// SPDX-License-Identifier: GPL-3.0-only
import Darwin
import Foundation

public enum StreamKind: Sendable { case stdout, stderr }

public struct ExitStatus: Sendable, Equatable, CustomStringConvertible {
    public enum Kind: Sendable, Equatable { case exited(Int32), signaled(Int32) }
    public var kind: Kind

    public init(waitStatus: Int32) {
        let signal = waitStatus & 0x7f
        kind = signal == 0 ? .exited((waitStatus >> 8) & 0xff) : .signaled(signal)
    }

    public init(kind: Kind) { self.kind = kind }

    public var succeeded: Bool { kind == .exited(0) }

    public var description: String {
        switch kind {
        case .exited(let code): "exited with code \(code)"
        case .signaled(let signal): "stopped by signal \(signal)"
        }
    }
}

public struct SpawnError: Error, LocalizedError, Sendable {
    public var message: String
    public var errorDescription: String? { message }
}

/// A child process in its own process group, so stopping it also stops
/// anything the runtime itself spawned (helper processes, ART children).
public final class RunningProcess: @unchecked Sendable {
    public let pid: pid_t
    private let lock = NSLock()
    private var terminationRequested = false

    init(pid: pid_t) { self.pid = pid }

    /// Ask the process group to exit, then force it after `grace` seconds.
    public func terminate(grace: TimeInterval = 3) {
        lock.lock()
        let first = !terminationRequested
        terminationRequested = true
        lock.unlock()
        guard first else { return }
        kill(-pid, SIGTERM)
        let pid = pid
        DispatchQueue.global().asyncAfter(deadline: .now() + grace) {
            // ESRCH (group already gone) is the expected result when it exited.
            kill(-pid, SIGKILL)
        }
    }
}

public enum ProcessRunner {
    /// Start `executable` without a shell. Output arrives on background queues;
    /// `onExit` fires once, after the process has exited and its pipes drained
    /// (or one second after exit if a stray descendant keeps a pipe open).
    public static func spawn(
        executable: URL,
        arguments: [String],
        environment: [String: String],
        workingDirectory: URL?,
        onOutput: @escaping @Sendable (StreamKind, Data) -> Void,
        onExit: @escaping @Sendable (ExitStatus) -> Void
    ) throws -> RunningProcess {
        var outPipe: [Int32] = [-1, -1]
        var errPipe: [Int32] = [-1, -1]
        guard pipe(&outPipe) == 0 else { throw SpawnError(message: "pipe: \(String(cString: strerror(errno)))") }
        guard pipe(&errPipe) == 0 else {
            close(outPipe[0]); close(outPipe[1])
            throw SpawnError(message: "pipe: \(String(cString: strerror(errno)))")
        }
        for fd in outPipe + errPipe { _ = fcntl(fd, F_SETFD, FD_CLOEXEC) }

        var actions: posix_spawn_file_actions_t?
        posix_spawn_file_actions_init(&actions)
        defer { posix_spawn_file_actions_destroy(&actions) }
        posix_spawn_file_actions_addopen(&actions, 0, "/dev/null", O_RDONLY, 0)
        posix_spawn_file_actions_adddup2(&actions, outPipe[1], 1)
        posix_spawn_file_actions_adddup2(&actions, errPipe[1], 2)
        if let workingDirectory {
            posix_spawn_file_actions_addchdir(&actions, workingDirectory.path)
        }

        var attributes: posix_spawnattr_t?
        posix_spawnattr_init(&attributes)
        defer { posix_spawnattr_destroy(&attributes) }
        var defaults = sigset_t(), mask = sigset_t()
        sigfillset(&defaults)
        sigemptyset(&mask)
        posix_spawnattr_setsigdefault(&attributes, &defaults)
        posix_spawnattr_setsigmask(&attributes, &mask)
        posix_spawnattr_setpgroup(&attributes, 0)
        posix_spawnattr_setflags(&attributes, Int16(POSIX_SPAWN_SETPGROUP | POSIX_SPAWN_SETSIGDEF | POSIX_SPAWN_SETSIGMASK))

        let argv = ([executable.path] + arguments).map { strdup($0) } + [nil]
        let envp = environment.map { strdup("\($0.key)=\($0.value)") } + [nil]
        defer { for pointer in argv + envp { free(pointer) } }

        var pid: pid_t = 0
        let result = posix_spawn(&pid, executable.path, &actions, &attributes, argv, envp)
        close(outPipe[1]); close(errPipe[1])
        guard result == 0 else {
            close(outPipe[0]); close(errPipe[0])
            throw SpawnError(message: "Cannot start \(executable.path): \(String(cString: strerror(result)))")
        }

        let child = pid
        let drained = DispatchGroup()
        // The handles close their descriptors when deallocated, so the waiter
        // below keeps them alive until the process has exited.
        var handles: [FileHandle] = []
        for (fd, kind) in [(outPipe[0], StreamKind.stdout), (errPipe[0], StreamKind.stderr)] {
            drained.enter()
            let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
            handles.append(handle)
            handle.readabilityHandler = { handle in
                let data = handle.availableData
                if data.isEmpty {
                    handle.readabilityHandler = nil
                    drained.leave()
                } else {
                    onOutput(kind, data)
                }
            }
        }

        let retained = handles
        DispatchQueue.global().async {
            var status: Int32 = 0
            while waitpid(child, &status, 0) < 0 && errno == EINTR {}
            // Reap anything the runtime left behind in its group.
            kill(-child, SIGKILL)
            _ = drained.wait(timeout: .now() + 1)
            for handle in retained { handle.readabilityHandler = nil }
            onExit(ExitStatus(waitStatus: status))
        }
        return RunningProcess(pid: pid)
    }

    public struct Output: Sendable {
        public var status: ExitStatus
        public var stdout: Data
        public var stderr: String
    }

    /// Run to completion and collect output. Kills the process after `timeout` seconds.
    public static func run(
        executable: URL,
        arguments: [String],
        environment: [String: String],
        workingDirectory: URL?,
        timeout: TimeInterval = 60,
        maxOutputBytes: Int = 16 * 1024 * 1024
    ) async throws -> Output {
        let collector = Collector(limit: maxOutputBytes)
        return try await withCheckedThrowingContinuation { continuation in
            do {
                let process = try spawn(
                    executable: executable, arguments: arguments, environment: environment,
                    workingDirectory: workingDirectory,
                    onOutput: { kind, data in collector.append(kind, data) },
                    onExit: { status in
                        let snapshot = collector.snapshot()
                        continuation.resume(returning: Output(status: status, stdout: snapshot.0, stderr: snapshot.1))
                    })
                DispatchQueue.global().asyncAfter(deadline: .now() + timeout) { process.terminate(grace: 1) }
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }

    private final class Collector: @unchecked Sendable {
        private let lock = NSLock()
        private let limit: Int
        private var out = Data(), err = Data()
        init(limit: Int) { self.limit = limit }
        func append(_ kind: StreamKind, _ data: Data) {
            lock.lock(); defer { lock.unlock() }
            switch kind {
            case .stdout: if out.count < limit { out.append(data.prefix(limit - out.count)) }
            case .stderr: if err.count < 1 << 20 { err.append(data.prefix((1 << 20) - err.count)) }
            }
        }
        func snapshot() -> (Data, String) {
            lock.lock(); defer { lock.unlock() }
            return (out, String(decoding: err, as: UTF8.self))
        }
    }
}
