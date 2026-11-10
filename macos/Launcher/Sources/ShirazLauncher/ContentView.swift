// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// Android packages have no system-declared type, so derive one from the extension.
    static let apk = UTType(filenameExtension: "apk", conformingTo: .zip) ?? .zip
}

struct ContentView: View {
    @Environment(LauncherModel.self) private var model
    @State private var isTargeted = false

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            AppListView()
                .navigationSplitViewColumnWidth(min: 220, ideal: 260, max: 340)
        } detail: {
            if let package = model.selection, model.entry(package) != nil {
                AppDetailView(package: package)
            } else {
                EmptyLibraryView()
            }
        }
        .dropDestination(for: URL.self) { urls, _ in
            model.importAPKs(urls)
            return !urls.isEmpty
        } isTargeted: { isTargeted = $0 }
        .overlay { if isTargeted { DropOverlay() } }
        .fileImporter(isPresented: $model.showImporter, allowedContentTypes: [.apk], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { model.importAPKs(urls) }
        }
        .alert("Shiraz Launcher", isPresented: Binding(get: { model.alert != nil }, set: { if !$0 { model.alert = nil } })) {
            Button("OK") { model.alert = nil }
        } message: {
            Text(model.alert ?? "")
        }
        .toolbar {
            ToolbarItem {
                Button("Add APK", systemImage: "plus") { model.showImporter = true }
                    .help("Add an APK to the library")
            }
        }
    }
}

struct DropOverlay: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 16)
            .strokeBorder(.tint, style: StrokeStyle(lineWidth: 3, dash: [10]))
            .background(.tint.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                Label("Drop APK to add", systemImage: "arrow.down.app")
                    .font(.title2.weight(.medium))
            }
            .padding(12)
            .allowsHitTesting(false)
    }
}

struct EmptyLibraryView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        ContentUnavailableView {
            VStack(spacing: 12) {
                ShirazMark()
                    .frame(width: 72, height: 72)
                    .foregroundStyle(.secondary)
                Text("No apps yet")
            }
        } description: {
            Text("Drop an APK anywhere in this window, or choose one from disk.")
        } actions: {
            Button("Choose APK…") { model.showImporter = true }
                .buttonStyle(.borderedProminent)
            if case .unavailable(let reason) = model.runtimeStatus {
                Label(reason, systemImage: "exclamationmark.triangle")
                    .font(.callout)
                    .foregroundStyle(.orange)
            }
        }
    }
}

struct AppListView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        @Bindable var model = model
        List(selection: $model.selection) {
            ForEach(model.apps) { app in
                AppRow(app: app, session: model.sessions[app.packageName])
                    .tag(app.id)
                    .contextMenu {
                        Button(model.sessions[app.id]?.isRunning == true ? "Stop" : "Launch") {
                            if model.sessions[app.id]?.isRunning == true { model.stop(app.id) } else { model.launch(app.id) }
                        }
                        Divider()
                        Button("Remove from Library", role: .destructive) { model.remove(app.id) }
                    }
            }
        }
        .overlay {
            if model.isImporting { ProgressView("Inspecting…").padding().background(.regularMaterial, in: .rect(cornerRadius: 10)) }
        }
        .navigationTitle("Library")
    }
}

struct AppRow: View {
    let app: AppEntry
    let session: Session?

    var body: some View {
        HStack(spacing: 10) {
            AppIconView(app: app)
            VStack(alignment: .leading, spacing: 2) {
                Text(app.displayName).lineLimit(1)
                Text(app.packageName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
            if session?.isRunning == true {
                Image(systemName: "circle.fill").foregroundStyle(.green).imageScale(.small)
                    .accessibilityLabel("Running")
            } else if !app.compatibility.isLaunchable {
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange).imageScale(.small)
                    .accessibilityLabel("Unsupported")
            }
        }
    }
}
