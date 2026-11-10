// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

struct AppDetailView: View {
    @Environment(LauncherModel.self) private var model
    let package: String

    var body: some View {
        if let app = model.entry(package) {
            let session = model.sessions[package]
            TabView {
                Tab("Overview", systemImage: "info.circle") { OverviewTab(app: app, session: session) }
                Tab("Settings", systemImage: "slider.horizontal.3") { SettingsTab(app: app) }
                Tab("Log", systemImage: "text.alignleft") { LogTab(session: session) }
            }
            .navigationTitle(app.displayName)
            .navigationSubtitle(app.packageName)
            .toolbar {
                ToolbarItem {
                    if session?.isRunning == true {
                        Button("Stop", systemImage: "stop.fill") { model.stop(package) }
                    } else {
                        Button("Launch", systemImage: "play.fill") { model.launch(package) }
                            .disabled(!app.compatibility.isLaunchable || model.runtimeStatus.location == nil)
                    }
                }
            }
            .id(package)
        }
    }
}

struct OverviewTab: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    let session: Session?

    var body: some View {
        Form {
            Section {
                HStack(spacing: 14) {
                    AppIconView(app: app, size: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(app.displayName).font(.title2.weight(.semibold))
                        Text(app.packageName).foregroundStyle(.secondary)
                        if model.icons[app.packageName] == nil, let why = model.iconError(app.packageName) {
                            Text("No icon: \(why)").font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }
                    }
                }
            }
            Section("Status") {
                LabeledContent("State", value: session?.state.summary ?? "Not running")
                switch app.compatibility {
                case .javaOnly: LabeledContent("Compatibility", value: "Java only")
                case .arm64: LabeledContent("Compatibility", value: "Java and arm64 native code")
                case .unsupported(let why):
                    Label(why, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                }
                Text("The macOS runtime is experimental. An APK that imports can still fail to start.")
                    .font(.callout).foregroundStyle(.secondary)
            }
            Section("Package") {
                LabeledContent("Version", value: [app.versionName, app.versionCode.map { "(\($0))" }].compactMap { $0 }.joined(separator: " "))
                LabeledContent("Android SDK", value: "min \(app.minSdk ?? "?"), target \(app.targetSdk ?? "?")")
                LabeledContent("Native ABIs", value: app.nativeAbis.isEmpty ? "none" : app.nativeAbis.joined(separator: ", "))
                LabeledContent("SHA-256") { Text(app.sha256).font(.caption.monospaced()).textSelection(.enabled) }
                LabeledContent("Added", value: app.importedAt.formatted(date: .abbreviated, time: .shortened))
            }
            Section("Permissions (\(app.permissions.count))") {
                if app.permissions.isEmpty {
                    Text("None requested").foregroundStyle(.secondary)
                } else {
                    ForEach(app.permissions, id: \.self) { Text($0).font(.callout.monospaced()).textSelection(.enabled) }
                }
            }
            Section {
                Button("Remove from Library", role: .destructive) { model.remove(app.packageName) }
            }
        }
        .formStyle(.grouped)
    }
}

struct SettingsTab: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    @State private var draft: AppSettings

    init(app: AppEntry) {
        self.app = app
        _draft = State(initialValue: app.settings)
    }

    var body: some View {
        Form {
            Section("Window") {
                Picker("Orientation", selection: orientation) {
                    Text("Portrait").tag(true)
                    Text("Landscape").tag(false)
                }
                .pickerStyle(.segmented)
                LabeledContent("Width") { TextField("Width", value: $draft.width, format: .number.grouping(.never)).labelsHidden().frame(width: 90).multilineTextAlignment(.trailing) }
                LabeledContent("Height") { TextField("Height", value: $draft.height, format: .number.grouping(.never)).labelsHidden().frame(width: 90).multilineTextAlignment(.trailing) }
                Button("Reset to 360 × 640") { draft.width = 360; draft.height = 640 }
            }
            Section("Diagnostics") {
                Picker("Log level", selection: $draft.logLevel) {
                    ForEach(LogLevel.allCases) { Text($0.rawValue.capitalized).tag($0) }
                }
                Toggle("Stop automatically after a time limit", isOn: timeoutEnabled)
                if let timeout = draft.timeoutSeconds {
                    Stepper("\(timeout) seconds", value: Binding(get: { timeout }, set: { draft.timeoutSeconds = max(1, $0) }), in: 1...86_400, step: 30)
                }
            }
            if let problem = draft.validationError {
                Label(problem, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red)
            }
            Section("Not available yet") {
                Text("Network limits and per-app permission controls need enforcement in the runtime. They will appear here once the runtime can honor them.")
                    .font(.callout).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .onChange(of: draft) { _, new in
            if new.validationError == nil { model.updateSettings(new, for: app.packageName) }
        }
    }

    private var orientation: Binding<Bool> {
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

struct LogTab: View {
    let session: Session?

    var body: some View {
        if let session {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 1) {
                        ForEach(session.log.lines) { line in
                            Text(line.text)
                                .font(.caption.monospaced())
                                .foregroundStyle(line.isError ? Color.red : Color.primary)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .id(line.id)
                        }
                    }
                    .padding(10)
                }
                .onChange(of: session.log.lines.last?.id) { _, last in
                    if let last { proxy.scrollTo(last, anchor: .bottom) }
                }
            }
            .background(.background.secondary)
            .toolbar {
                ToolbarItem { ShareLink(item: session.log.plainText, subject: Text("Shiraz log")) }
            }
        } else {
            ContentUnavailableView("No log yet", systemImage: "text.alignleft", description: Text("Launch the app to see runtime output."))
        }
    }
}
