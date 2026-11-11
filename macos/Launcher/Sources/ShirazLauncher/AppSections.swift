// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI
import UniformTypeIdentifiers

extension LauncherModel {
    /// A control value for a feature that does not exist yet. Reading gives
    /// the fixed current behaviour; writing reports the feature instead of
    /// changing anything, so the control springs back.
    func unimplemented<Value>(_ value: Value, _ feature: Feature) -> Binding<Value> {
        Binding(get: { value }, set: { [weak self] _ in self?.attempt(feature) })
    }
}

// MARK: Overview

struct OverviewSection: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if let session = model.sessions[app.packageName] {
                    SessionCard(app: app, session: session)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 180), spacing: 14)], spacing: 14) {
                    Fact(title: "Version", value: version, symbol: "tag")
                    Fact(title: "Android", value: "API \(app.minSdk ?? "?") – \(app.targetSdk ?? "?")", symbol: "cpu")
                    Fact(title: "Native Code", value: app.nativeAbis.isEmpty ? "None" : app.nativeAbis.joined(separator: ", "), symbol: "memorychip")
                    Fact(title: "Size", value: model.apkSize(app.packageName).map { $0.formatted(.byteCount(style: .file)) } ?? "—", symbol: "shippingbox")
                    Fact(title: "Added", value: app.importedAt.formatted(date: .abbreviated, time: .shortened), symbol: "calendar")
                    Fact(title: "Last Played", value: app.lastLaunchedAt.map { $0.formatted(.relative(presentation: .named)) } ?? "Never", symbol: "clock")
                    Fact(title: "Window", value: "\(app.settings.width) × \(app.settings.height)", symbol: "rectangle.portrait")
                    Fact(title: "Permissions", value: "\(app.permissions.count) requested", symbol: "hand.raised")
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("SHA-256").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(app.sha256).font(.callout.monospaced()).textSelection(.enabled)
                }
                Button("Show Performance", systemImage: "chart.xyaxis.line") { model.attempt(.performanceStats) }
                    .buttonStyle(.glass)
            }
            .padding(28)
            .frame(maxWidth: 900, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
    }

    private var version: String {
        [app.versionName, app.versionCode.map { "(\($0))" }].compactMap { $0 }.joined(separator: " ").nonEmpty ?? "—"
    }
}

extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}

struct Fact: View {
    let title: String
    let value: String
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: symbol)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3.weight(.medium))
                .lineLimit(2)
                .minimumScaleFactor(0.8)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
        .padding(14)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(.fill.quaternary, in: .rect(cornerRadius: 14, style: .continuous))
    }
}

struct SessionCard: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    let session: Session

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: session.isRunning ? "waveform" : "moon.zzz.fill")
                .font(.title)
                .symbolEffect(.variableColor.iterative, isActive: session.isRunning)
                .foregroundStyle(session.isRunning ? model.accent(app.packageName) : .secondary)
                .frame(width: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text(session.state.summary).font(.headline)
                if session.isRunning {
                    Text(session.startedAt, style: .timer).font(.callout.monospacedDigit()).foregroundStyle(.secondary)
                } else {
                    Text("Started \(session.startedAt.formatted(date: .omitted, time: .shortened))").font(.callout).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if case .running(let pid) = session.state {
                Text("PID \(pid)").font(.callout.monospacedDigit()).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(model.accent(app.packageName).opacity(0.10), in: .rect(cornerRadius: 16, style: .continuous))
    }
}

// MARK: Settings

enum DevicePreset: String, CaseIterable, Identifiable {
    case phone, largePhone, tablet, custom
    var id: String { rawValue }
    var title: String {
        switch self {
        case .phone: "Phone"
        case .largePhone: "Large Phone"
        case .tablet: "Tablet"
        case .custom: "Custom"
        }
    }
    /// Portrait size in Android pixels.
    var size: (Int, Int)? {
        switch self {
        case .phone: (360, 640)
        case .largePhone: (412, 915)
        case .tablet: (800, 1280)
        case .custom: nil
        }
    }
}

enum Renderer: String, CaseIterable, Identifiable {
    case automatic, metal, software
    var id: String { rawValue }
}

struct SettingsSection: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    @State private var draft: AppSettings

    init(app: AppEntry) {
        self.app = app
        _draft = State(initialValue: app.settings)
    }

    var body: some View {
        Form {
            Section("Display") {
                Picker("Device", selection: preset) {
                    ForEach(DevicePreset.allCases) { Text($0.title).tag($0) }
                }
                Picker("Orientation", selection: portrait) {
                    Label("Portrait", systemImage: "rectangle.portrait").tag(true)
                    Label("Landscape", systemImage: "rectangle").tag(false)
                }
                .pickerStyle(.segmented)
                LabeledContent("Size") {
                    HStack(spacing: 6) {
                        TextField("Width", value: $draft.width, format: .number.grouping(.never))
                            .labelsHidden().frame(width: 70).multilineTextAlignment(.trailing)
                        Text("×").foregroundStyle(.secondary)
                        TextField("Height", value: $draft.height, format: .number.grouping(.never))
                            .labelsHidden().frame(width: 70).multilineTextAlignment(.trailing)
                    }
                }
                Picker("Scale", selection: model.unimplemented(1, .displayScale)) {
                    Text("1×").tag(1)
                    Text("2×").tag(2)
                }
                Toggle("Open in Full Screen", isOn: model.unimplemented(false, .fullScreen))
                Picker("Renderer", selection: model.unimplemented(Renderer.automatic, .renderer)) {
                    Text("Automatic").tag(Renderer.automatic)
                    Text("Metal (ANGLE)").tag(Renderer.metal)
                    Text("Software").tag(Renderer.software)
                }
            }
            if let problem = draft.validationError {
                Label(problem, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
            }
            Section("Input") {
                LabeledContent("Keyboard Mapping") {
                    Button("Configure…") { model.attempt(.keyboardMapping) }
                }
                Toggle("Game Controllers", isOn: model.unimplemented(false, .gameController))
            }
            Section("Behavior") {
                Picker("Language", selection: model.unimplemented("system", .language)) {
                    Text("System").tag("system")
                    Text("English").tag("en")
                    Text("Deutsch").tag("de")
                    Text("فارسی").tag("fa")
                }
                Toggle("Keep Mac Awake While Running", isOn: model.unimplemented(false, .keepAwake))
                Toggle("Stop After a Time Limit", isOn: timeoutEnabled)
                if let timeout = draft.timeoutSeconds {
                    Stepper(value: Binding(get: { timeout }, set: { draft.timeoutSeconds = max(1, $0) }), in: 1...86_400, step: 30) {
                        LabeledContent("Time Limit", value: Duration.seconds(timeout).formatted(.units(allowed: [.hours, .minutes, .seconds], width: .abbreviated)))
                    }
                }
            }
            Section("Diagnostics") {
                Picker("Log Level", selection: $draft.logLevel) {
                    ForEach(LogLevel.allCases) { Text($0.rawValue.capitalized).tag($0) }
                }
            }
        }
        .formStyle(.grouped)
        .onChange(of: draft) { _, new in
            if new.validationError == nil { model.updateSettings(new, for: app.packageName) }
        }
    }

    private var preset: Binding<DevicePreset> {
        Binding(
            get: {
                let short = min(draft.width, draft.height), long = max(draft.width, draft.height)
                return DevicePreset.allCases.first { $0.size.map { $0 == (short, long) } ?? false } ?? .custom
            },
            set: { preset in
                guard let (short, long) = preset.size else { return }
                let portrait = draft.height >= draft.width
                (draft.width, draft.height) = portrait ? (short, long) : (long, short)
            })
    }

    private var portrait: Binding<Bool> {
        Binding(
            get: { draft.height >= draft.width },
            set: { portrait in
                let (low, high) = (min(draft.width, draft.height), max(draft.width, draft.height))
                (draft.width, draft.height) = portrait ? (low, high) : (high, low)
            })
    }

    private var timeoutEnabled: Binding<Bool> {
        Binding(get: { draft.timeoutSeconds != nil }, set: { draft.timeoutSeconds = $0 ? 600 : nil })
    }
}

// MARK: Permissions

struct PermissionsSection: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry

    var body: some View {
        Form {
            Section {
                Toggle(isOn: model.unimplemented(true, .networkAccess)) {
                    Label("Network Access", systemImage: "network")
                }
                Toggle(isOn: model.unimplemented(false, .notifications)) {
                    Label("Notifications", systemImage: "bell.badge")
                }
            }
            Section("Requested by \(app.displayName)") {
                if app.permissions.isEmpty {
                    Text("None").foregroundStyle(.secondary)
                }
                ForEach(app.permissions, id: \.self) { permission in
                    let info = PermissionInfo(permission)
                    LabeledContent {
                        Menu("Manage") {
                            Button("Allow") { model.attempt(.grantPermission) }
                            Button("Ask Every Time") { model.attempt(.grantPermission) }
                            Button("Deny") { model.attempt(.grantPermission) }
                        }
                        .fixedSize()
                    } label: {
                        Label {
                            Text(info.title)
                            Text(permission).font(.caption.monospaced())
                        } icon: {
                            Image(systemName: info.symbol).foregroundStyle(.tint)
                        }
                    }
                }
            }
        }
        .formStyle(.grouped)
    }
}

/// Friendly names for common Android permissions.
struct PermissionInfo {
    let title: String
    let symbol: String

    init(_ permission: String) {
        let known: [String: (String, String)] = [
            "android.permission.INTERNET": ("Internet", "globe"),
            "android.permission.ACCESS_NETWORK_STATE": ("Network State", "antenna.radiowaves.left.and.right"),
            "android.permission.ACCESS_WIFI_STATE": ("Wi-Fi State", "wifi"),
            "android.permission.ACCESS_FINE_LOCATION": ("Precise Location", "location.fill"),
            "android.permission.ACCESS_COARSE_LOCATION": ("Approximate Location", "location"),
            "android.permission.ACCESS_BACKGROUND_LOCATION": ("Background Location", "location.circle"),
            "android.permission.CAMERA": ("Camera", "camera"),
            "android.permission.RECORD_AUDIO": ("Microphone", "mic"),
            "android.permission.POST_NOTIFICATIONS": ("Notifications", "bell"),
            "android.permission.VIBRATE": ("Vibration", "iphone.radiowaves.left.and.right"),
            "android.permission.WAKE_LOCK": ("Keep Awake", "moon.zzz"),
            "android.permission.READ_EXTERNAL_STORAGE": ("Read Storage", "folder"),
            "android.permission.WRITE_EXTERNAL_STORAGE": ("Write Storage", "folder.badge.plus"),
            "android.permission.READ_MEDIA_IMAGES": ("Photos", "photo"),
            "android.permission.READ_CONTACTS": ("Contacts", "person.crop.circle"),
            "android.permission.BLUETOOTH": ("Bluetooth", "dot.radiowaves.right"),
            "android.permission.FOREGROUND_SERVICE": ("Foreground Service", "gearshape.2"),
            "android.permission.RECEIVE_BOOT_COMPLETED": ("Start at Login", "power"),
        ]
        if let (title, symbol) = known[permission] {
            self.title = title
            self.symbol = symbol
        } else {
            let tail = permission.split(separator: ".").last.map(String.init) ?? permission
            title = tail.replacingOccurrences(of: "_", with: " ").capitalized
            symbol = "hand.raised"
        }
    }
}

// MARK: Storage

struct StorageSection: View {
    @Environment(LauncherModel.self) private var model
    @Environment(\.openURL) private var openURL
    let app: AppEntry

    var body: some View {
        Form {
            Section("Package") {
                LabeledContent("APK", value: model.apkSize(app.packageName).map { $0.formatted(.byteCount(style: .file)) } ?? "—")
                LabeledContent("Location") {
                    Button("Show in Finder") { openURL(model.apkURL(app.packageName).deletingLastPathComponent()) }
                }
            }
            Section("App Data") {
                LabeledContent("Data") {
                    Button("Show in Finder") { model.attempt(.revealAppData) }
                }
                LabeledContent("Backup") {
                    Button("Back Up…") { model.attempt(.backupAppData) }
                }
            }
            Section {
                Button("Clear Cache") { model.attempt(.clearAppCache) }
                Button("Clear All App Data…", role: .destructive) { model.attempt(.clearAppData) }
            }
        }
        .formStyle(.grouped)
    }
}

// MARK: Console

struct LogDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.plainText]
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

struct ConsoleSection: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    @State private var filter = ""
    @State private var errorsOnly = false
    @State private var follow = true
    @State private var exporting = false

    var body: some View {
        if let session = model.sessions[app.packageName] {
            let lines = session.log.lines.filter { line in
                (!errorsOnly || line.level == .error) && (filter.isEmpty || line.text.localizedCaseInsensitiveContains(filter))
            }
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    TextField("Filter", text: $filter, prompt: Text("Filter output"))
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 260)
                    Toggle("Errors Only", isOn: $errorsOnly).toggleStyle(.button)
                    Toggle("Follow", isOn: $follow).toggleStyle(.button)
                    Spacer()
                    Text("\(lines.count) lines").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    Button("Copy", systemImage: "doc.on.doc") { model.attempt(.copyToClipboard) }
                    Button("Export…", systemImage: "square.and.arrow.up") { exporting = true }
                    Button("Clear", systemImage: "trash") { model.clearLog(app.packageName) }
                }
                .labelStyle(.iconOnly)
                .buttonStyle(.borderless)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                Divider()
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 1) {
                            ForEach(lines) { line in
                                Text(line.text)
                                    .font(.system(size: 11.5, design: .monospaced))
                                    .foregroundStyle(color(line.level))
                                    .textSelection(.enabled)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .id(line.id)
                            }
                        }
                        .padding(14)
                    }
                    .onChange(of: lines.last?.id) { _, last in
                        if follow, let last { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
                .background(.background.secondary)
            }
            .fileExporter(isPresented: $exporting, document: LogDocument(text: session.log.plainText),
                          contentType: .plainText, defaultFilename: "\(app.packageName).log") { result in
                if case .failure(let error) = result { model.present(error) }
            }
        } else {
            ContentUnavailableView {
                Label("No output yet", systemImage: "terminal")
            } description: {
                Text("Launch \(app.displayName) to see its console.")
            } actions: {
                Button("Launch", systemImage: "play.fill") { model.launch(app.packageName) }
                    .buttonStyle(.glassProminent)
            }
        }
    }
    private func color(_ level: LogLine.Level) -> Color {
        switch level {
        case .error: .red
        case .warning: .orange
        case .info: .primary
        case .debug: .secondary
        }
    }
}
