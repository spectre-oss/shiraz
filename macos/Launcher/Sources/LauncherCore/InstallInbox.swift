// SPDX-License-Identifier: GPL-3.0-only
import Foundation

/// A request from an app running in Shiraz, through its session's inbox
/// directory (java/shiraz/runtime/LauncherInbox.java writes them).
///
///     <id>.request   Java properties: kind, installer, package or expected
///     <id>.apk       the APK, for installs
///     <id>.result    the launcher's answer: status=ok|failed, package, message
public struct InboxRequest: Equatable, Sendable {
    public enum Kind: String, Sendable { case install, launch, remove }

    public var id: String
    public var kind: Kind
    /// The requesting app's own package name, as it reports it. The launcher
    /// trusts the session it gave the inbox to, not this value.
    public var installer: String?
    /// Target for launch and remove.
    public var package: String?
    /// Package name the installer expects the APK to declare.
    public var expected: String?
    public var apk: URL?
}

public enum InboxDecision: Equatable, Sendable {
    case install(apk: URL, expected: String?)
    /// Replace `package`, already in the library. The caller still applies UpdatePolicy.
    case update(apk: URL, package: String)
    case launch(String)
    case remove(String)
    case refuse(String)
}

public enum InstallInbox {
    public static let installPermission = "android.permission.REQUEST_INSTALL_PACKAGES"
    public static let deletePermission = "android.permission.REQUEST_DELETE_PACKAGES"
    static let maxRequestBytes = 16 * 1024
    static let maxPending = 32

    /// Complete requests waiting in `directory`, oldest first. A request whose
    /// file is unreadable or malformed is answered as failed and dropped.
    public static func pending(in directory: URL, fileManager: FileManager = .default) -> [InboxRequest] {
        guard let names = try? fileManager.contentsOfDirectory(atPath: directory.path) else { return [] }
        let ids = names.filter { $0.hasSuffix(".request") }.map { String($0.dropLast(".request".count)) }
            .filter(isValidID).sorted()
        var requests: [(Date, InboxRequest)] = []
        for id in ids.prefix(maxPending) {
            let file = directory.appending(path: id + ".request")
            do {
                let values = try file.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey, .contentModificationDateKey])
                guard values.isRegularFile == true, (values.fileSize ?? 0) <= maxRequestBytes else {
                    throw LibraryError.inspectFailed("Malformed request")
                }
                let text = try String(contentsOf: file, encoding: .utf8)
                let request = try parse(id: id, properties: JavaProperties.parse(text), directory: directory)
                requests.append((values.contentModificationDate ?? .distantPast, request))
            } catch {
                try? answer(id: id, in: directory, ok: false, package: nil, message: "Malformed request")
            }
        }
        return requests.sorted { $0.0 < $1.0 }.map(\.1)
    }

    static func isValidID(_ id: String) -> Bool {
        id.count <= 64 && id.wholeMatch(of: /[A-Za-z0-9-]+/) != nil
    }

    static func parse(id: String, properties: [String: String], directory: URL) throws -> InboxRequest {
        guard let kind = properties["kind"].flatMap(InboxRequest.Kind.init(rawValue:)) else {
            throw LibraryError.inspectFailed("Unknown request")
        }
        func package(_ key: String) throws -> String? {
            guard let value = properties[key], !value.isEmpty else { return nil }
            guard LibraryStore.isValidPackageName(value) else { throw LibraryError.invalidPackageName(value) }
            return value
        }
        let apk = directory.appending(path: id + ".apk")
        return InboxRequest(
            id: id, kind: kind, installer: try package("installer"), package: try package("package"),
            expected: try package("expected"),
            apk: kind == .install && FileManager.default.fileExists(atPath: apk.path) ? apk : nil)
    }

    /// What the launcher should do. `requester` is the library entry of the
    /// app whose session owns the inbox.
    public static func decide(_ request: InboxRequest, requester: AppEntry, library: [AppEntry]) -> InboxDecision {
        func has(_ permission: String) -> Bool { requester.permissions.contains(permission) }
        let name = requester.displayName
        switch request.kind {
        case .install:
            guard has(installPermission) else {
                return .refuse("\(name) does not declare REQUEST_INSTALL_PACKAGES.")
            }
            guard let apk = request.apk else { return .refuse("The request has no APK.") }
            if let expected = request.expected, library.contains(where: { $0.packageName == expected }) {
                guard expected != requester.packageName else {
                    return .refuse("\(name) cannot update itself while it runs. Update it from the F-Droid catalog in Shiraz.")
                }
                return .update(apk: apk, package: expected)
            }
            return .install(apk: apk, expected: request.expected)
        case .launch:
            guard let package = request.package, let target = library.first(where: { $0.packageName == package }) else {
                return .refuse("\(request.package ?? "That app") is not in the library.")
            }
            guard target.compatibility.isLaunchable else { return .refuse("\(target.displayName) cannot run on this Mac.") }
            return .launch(package)
        case .remove:
            guard has(deletePermission) else {
                return .refuse("\(name) does not declare REQUEST_DELETE_PACKAGES.")
            }
            guard let package = request.package, library.contains(where: { $0.packageName == package }) else {
                return .refuse("\(request.package ?? "That app") is not in the library.")
            }
            guard package != requester.packageName else { return .refuse("An app cannot remove itself.") }
            return .remove(package)
        }
    }

    /// Write `<id>.result` atomically and drop the request and its APK.
    public static func answer(id: String, in directory: URL, ok: Bool, package: String?, message: String?) throws {
        guard isValidID(id) else { return }
        var values = ["status": ok ? "ok" : "failed"]
        values["package"] = package
        values["message"] = message.map { String($0.prefix(2000)) }
        let part = directory.appending(path: id + ".result.part")
        try Data(JavaProperties.format(values).utf8).write(to: part)
        try? FileManager.default.removeItem(at: directory.appending(path: id + ".apk"))
        try? FileManager.default.removeItem(at: directory.appending(path: id + ".request"))
        _ = rename(part.path, directory.appending(path: id + ".result").path)
    }
}

/// The subset of java.util.Properties text the inbox exchanges.
enum JavaProperties {
    static func parse(_ text: String) -> [String: String] {
        var result: [String: String] = [:]
        var logical: [String] = []
        var current = ""
        for raw in text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline) {
            let line = raw.drop { $0 == " " || $0 == "\t" }
            if current.isEmpty, line.first == "#" || line.first == "!" { continue }
            let trailing = line.reversed().prefix { $0 == "\\" }.count
            if trailing % 2 == 1 {
                current += line.dropLast()
            } else {
                logical.append(current + line)
                current = ""
            }
        }
        if !current.isEmpty { logical.append(current) }
        for line in logical where !line.isEmpty {
            var key = "", value = "", escaped = false, inValue = false
            var chars = Array(line)[...]
            while let c = chars.popFirst() {
                if escaped {
                    var decoded: Character = c
                    switch c {
                    case "n": decoded = "\n"
                    case "t": decoded = "\t"
                    case "r": decoded = "\r"
                    case "f": decoded = "\u{0C}"
                    case "u":
                        let hex = String(chars.prefix(4))
                        chars = chars.dropFirst(4)
                        decoded = UInt32(hex, radix: 16).flatMap(Unicode.Scalar.init).map(Character.init) ?? "\u{FFFD}"
                    default: break
                    }
                    if inValue { value.append(decoded) } else { key.append(decoded) }
                    escaped = false
                } else if c == "\\" {
                    escaped = true
                } else if !inValue, c == "=" || c == ":" || c == " " || c == "\t" {
                    inValue = true
                    chars = chars.drop { $0 == " " || $0 == "\t" }
                    if let next = chars.first, next == "=" || next == ":", c == " " || c == "\t" {
                        chars = chars.dropFirst().drop { $0 == " " || $0 == "\t" }
                    }
                } else if inValue {
                    value.append(c)
                } else {
                    key.append(c)
                }
            }
            result[key] = value
        }
        return result
    }

    static func format(_ values: [String: String]) -> String {
        func escape(_ text: String, key: Bool) -> String {
            var out = ""
            for (index, scalar) in text.unicodeScalars.enumerated() {
                switch scalar {
                case "\\": out += "\\\\"
                case "\n": out += "\\n"
                case "\r": out += "\\r"
                case "\t": out += "\\t"
                case "=", ":", "#", "!": out += "\\" + String(scalar)
                case " " where key || index == 0: out += "\\ "
                default:
                    if scalar.value < 0x20 || scalar.value > 0x7e {
                        for unit in String(scalar).utf16 { out += String(format: "\\u%04x", unit) }
                    } else {
                        out.unicodeScalars.append(scalar)
                    }
                }
            }
            return out
        }
        return values.sorted { $0.key < $1.key }.map { escape($0.key, key: true) + "=" + escape($0.value, key: false) + "\n" }.joined()
    }
}

/// What the user is asked before the launcher changes the library for an app.
public struct InboxConsent: Equatable, Sendable {
    public enum Kind: Equatable, Sendable { case install, update, remove }
    public var kind: Kind
    /// The app whose session sent the request.
    public var requester: AppEntry
    /// The library app concerned, for updates and removals.
    public var package: String?
    /// The launcher's own copy of the APK, for installs and updates.
    public var apk: URL?
    /// The request file; the app deletes it when it stops waiting.
    public var requestFile: URL
}

/// What the launcher does for an accepted request. Closures run in the
/// caller's isolation (the launcher model's main actor).
public struct InboxActions {
    public var confirm: (_ consent: InboxConsent) async -> Bool
    public var install: (_ apk: URL, _ expected: String?, _ installer: String) async throws -> AppEntry
    public var update: (_ apk: URL, _ package: String, _ installer: String) async throws -> AppEntry
    public var launch: (_ package: String) async -> Bool
    public var remove: (_ package: String) async -> Bool

    public init(
        confirm: @escaping (InboxConsent) async -> Bool,
        install: @escaping (URL, String?, String) async throws -> AppEntry,
        update: @escaping (URL, String, String) async throws -> AppEntry,
        launch: @escaping (String) async -> Bool,
        remove: @escaping (String) async -> Bool
    ) {
        self.confirm = confirm
        self.install = install
        self.update = update
        self.launch = launch
        self.remove = remove
    }
}

public enum InboxOutcome: Equatable, Sendable {
    case refused(String)
    /// The user said no.
    case declined
    /// The app stopped waiting before the user answered; nothing was done.
    case expired
    case installed(AppEntry)
    case updated(AppEntry)
    case installFailed(String)
    case launched(String, Bool)
    case removed(String, Bool)
}

extension InstallInbox {
    /// Decide, act, and answer one request. Always writes `<id>.result`.
    public static func handle(
        _ request: InboxRequest, requester: AppEntry, library: [AppEntry], inbox: URL, actions: InboxActions,
        isolation: isolated (any Actor)? = #isolation
    ) async -> InboxOutcome {
        let decision = decide(request, requester: requester, library: library)
        let file = inbox.appending(path: request.id + ".request")
        let consent: InboxConsent? = switch decision {
        case .install(let apk, _): InboxConsent(kind: .install, requester: requester, package: nil, apk: apk, requestFile: file)
        case .update(let apk, let package): InboxConsent(kind: .update, requester: requester, package: package, apk: apk, requestFile: file)
        case .remove(let package): InboxConsent(kind: .remove, requester: requester, package: package, apk: nil, requestFile: file)
        case .launch, .refuse: nil
        }
        let allowed = if let consent { await actions.confirm(consent) } else { true }
        let outcome: InboxOutcome
        if consent != nil, !FileManager.default.fileExists(atPath: file.path) {
            // The app gave up waiting and deleted its request: do nothing.
            outcome = .expired
        } else if !allowed {
            outcome = .declined
        } else {
            switch decision {
            case .refuse(let reason):
                outcome = .refused(reason)
            case .install(let apk, let expected):
                do {
                    outcome = .installed(try await actions.install(apk, expected, requester.packageName))
                } catch {
                    outcome = .installFailed(error.localizedDescription)
                }
            case .update(let apk, let package):
                do {
                    outcome = .updated(try await actions.update(apk, package, requester.packageName))
                } catch {
                    outcome = .installFailed(error.localizedDescription)
                }
            case .launch(let package):
                outcome = .launched(package, await actions.launch(package))
            case .remove(let package):
                outcome = .removed(package, await actions.remove(package))
            }
        }
        let (ok, package, message): (Bool, String?, String?) = switch outcome {
        case .refused(let reason): (false, nil, reason)
        case .declined: (false, nil, "Declined in the Shiraz launcher.")
        case .expired: (false, nil, "The request expired before it was answered.")
        case .installed(let entry), .updated(let entry): (true, entry.packageName, nil)
        case .installFailed(let reason): (false, nil, reason)
        case .launched(let package, let started): (started, package, started ? nil : "\(package) did not start.")
        case .removed(let package, let gone): (gone, package, gone ? nil : "\(package) could not be removed.")
        }
        try? answer(id: request.id, in: inbox, ok: ok, package: package, message: message)
        return outcome
    }

    /// Remove request directories left by sessions that ended long ago, for
    /// example when the launcher was killed. Live sessions keep theirs.
    public static func removeStale(in sessions: URL, olderThan age: TimeInterval = 24 * 60 * 60, now: Date = Date()) {
        let fileManager = FileManager.default
        guard let names = try? fileManager.contentsOfDirectory(atPath: sessions.path) else { return }
        for name in names {
            let url = sessions.appending(path: name, directoryHint: .isDirectory)
            let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
            if now.timeIntervalSince(modified) > age { try? fileManager.removeItem(at: url) }
        }
    }
}
