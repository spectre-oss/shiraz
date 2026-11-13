// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

struct LibraryView: View {
    @Environment(LauncherModel.self) private var model
    let scope: LibraryScope

    var body: some View {
        let apps = model.visibleApps(in: scope)
        Group {
            if model.apps.isEmpty {
                WelcomeView()
            } else if apps.isEmpty {
                emptyScope
            } else if model.showsGrid {
                ScrollView {
                    VStack(alignment: .leading, spacing: 26) {
                        if scope == .all, model.search.isEmpty, model.runningCount > 0 {
                            NowRunningStrip()
                        }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 132, maximum: 168), spacing: 18)], spacing: 26) {
                            ForEach(apps) { app in AppTile(app: app) }
                        }
                    }
                    .padding(28)
                }
            } else {
                AppTable(apps: apps)
            }
        }
        .navigationTitle(scope.title)
        .navigationSubtitle(subtitle(apps.count))
        .overlay {
            if model.isImporting {
                ProgressView("Adding…")
                    .padding(20)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
            }
        }
        .toolbar { LibraryToolbar() }
    }

    private func subtitle(_ count: Int) -> String {
        count == 1 ? "1 app" : "\(count) apps"
    }

    @ViewBuilder private var emptyScope: some View {
        if !model.search.isEmpty {
            ContentUnavailableView.search(text: model.search)
        } else {
            switch scope {
            case .running:
                ContentUnavailableView("Nothing running", systemImage: "play.circle", description: Text("Apps you launch appear here."))
            case .recent:
                ContentUnavailableView("Nothing played yet", systemImage: "clock")
            case .favorites:
                ContentUnavailableView("No favorites", systemImage: "star", description: Text("Star an app to keep it here."))
            case .all:
                WelcomeView()
            }
        }
    }
}

struct LibraryToolbar: ToolbarContent {
    @Environment(LauncherModel.self) private var model

    var body: some ToolbarContent {
        @Bindable var model = model
        ToolbarItemGroup {
            Picker("View", selection: $model.showsGrid) {
                Label("Icons", systemImage: "square.grid.2x2").tag(true)
                Label("List", systemImage: "list.bullet").tag(false)
            }
            .pickerStyle(.segmented)
            .help("Show as icons or list")
            Menu("Sort", systemImage: "arrow.up.arrow.down") {
                Picker("Sort By", selection: $model.sort) {
                    ForEach(LibrarySort.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.inline)
            }
            .help("Sort apps")
        }
        ToolbarSpacer(.fixed)
        ToolbarItem {
            Button("Add APK", systemImage: "plus") { model.showImporter = true }
                .help("Add an APK to the library")
        }
    }
}

/// First run: the ghost waits for an APK.
struct WelcomeView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        VStack(spacing: 22) {
            FloatingGhost(size: 128)
                .padding(.bottom, 12)
            VStack(spacing: 6) {
                Text("Bring an Android app to life").font(.largeTitle.weight(.bold))
                Text("Drop an APK anywhere in this window.").font(.title3).foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                Button("Choose APK…", systemImage: "plus") { model.showImporter = true }
                    .buttonStyle(.glassProminent)
                    .controlSize(.extraLarge)
                Button("Browse F-Droid", systemImage: "sparkles") { model.sidebar = .catalog }
                    .buttonStyle(.glass)
                    .controlSize(.extraLarge)
            }
            if case .unavailable(let reason) = model.runtimeStatus {
                Label(reason, systemImage: "exclamationmark.triangle.fill")
                    .font(.callout)
                    .foregroundStyle(.orange)
                    .padding(.top, 8)
            }
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Running apps as capsules above the grid.
struct NowRunningStrip: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Now Running").font(.title3.weight(.semibold))
            ScrollView(.horizontal) {
                GlassEffectContainer(spacing: 10) {
                    HStack(spacing: 10) {
                        ForEach(model.apps.filter { model.sessions[$0.packageName]?.isRunning == true }) { app in
                            HStack(spacing: 10) {
                                AppIconView(app: app, image: model.icons[app.packageName], size: 28)
                                    .runningGlow(true, color: model.accent(app.packageName), radius: 10)
                                Text(app.displayName).font(.callout.weight(.medium))
                                Button("Stop \(app.displayName)", systemImage: "stop.fill") { model.stop(app.packageName) }
                                    .labelStyle(.iconOnly)
                                    .buttonStyle(.borderless)
                                    .help("Stop")
                            }
                            .padding(.vertical, 7)
                            .padding(.horizontal, 12)
                            .glassEffect(.regular.tint(model.accent(app.packageName).opacity(0.25)).interactive(), in: .capsule)
                            .onTapGesture { model.path.append(app.packageName) }
                        }
                    }
                }
            }
            .scrollIndicators(.never)
        }
    }
}

struct AppTile: View {
    @Environment(LauncherModel.self) private var model
    @State private var hovering = false
    let app: AppEntry

    var body: some View {
        let running = model.sessions[app.packageName]?.isRunning == true
        let accent = model.accent(app.packageName)
        VStack(spacing: 10) {
            ZStack(alignment: .bottomTrailing) {
                AppIconView(app: app, image: model.icons[app.packageName], size: 96)
                    .shadow(color: accent.opacity(hovering ? 0.45 : 0.25), radius: hovering ? 16 : 10, y: hovering ? 10 : 6)
                    .runningGlow(running, color: accent, radius: 30)
                    .scaleEffect(hovering ? 1.05 : 1)
                if hovering || running {
                    Button(running ? "Stop" : "Launch", systemImage: running ? "stop.fill" : "play.fill") {
                        model.toggleRun(app.packageName)
                    }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glassProminent)
                    .tint(running ? .red : accent)
                    .buttonBorderShape(.circle)
                    .controlSize(.large)
                    .offset(x: 10, y: 8)
                    .transition(.scale.combined(with: .opacity))
                    .help(running ? "Stop" : "Launch")
                }
            }
            .frame(width: 112, height: 112)
            VStack(spacing: 2) {
                HStack(spacing: 4) {
                    if app.isFavorite {
                        Image(systemName: "star.fill").font(.caption2).foregroundStyle(.yellow)
                    }
                    Text(app.displayName).font(.callout.weight(.medium)).lineLimit(1)
                }
                status(running: running)
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 6)
        .frame(maxWidth: .infinity)
        .contentShape(.rect(cornerRadius: 18))
        .onHover { inside in withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { hovering = inside } }
        .onTapGesture(count: 2) { model.launch(app.packageName) }
        .onTapGesture { model.path.append(app.packageName) }
        .contextMenu { AppActions(model: model, app: app) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(app.displayName)
        .accessibilityAction(named: "Open") { model.path.append(app.packageName) }
        .accessibilityAction(named: running ? "Stop" : "Launch") { model.toggleRun(app.packageName) }
    }

    @ViewBuilder private func status(running: Bool) -> some View {
        if running {
            Text("Running").font(.caption).foregroundStyle(.green)
        } else if !app.compatibility.isLaunchable {
            Text("Unsupported").font(.caption).foregroundStyle(.orange)
        } else {
            Text(app.versionName ?? app.packageName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
    }
}

/// List presentation with sortable details.
struct AppTable: View {
    @Environment(LauncherModel.self) private var model
    let apps: [AppEntry]
    @State private var selection: String?

    var body: some View {
        Table(apps, selection: $selection) {
            TableColumn("Name") { app in
                HStack(spacing: 10) {
                    AppIconView(app: app, image: model.icons[app.packageName], size: 28)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(app.displayName)
                        Text(app.packageName).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 3)
            }
            .width(min: 220, ideal: 300)
            TableColumn("Version") { Text($0.versionName ?? "—").foregroundStyle(.secondary) }
                .width(min: 70, ideal: 90)
            TableColumn("Size") { app in
                Text(model.apkSize(app.packageName).map { $0.formatted(.byteCount(style: .file)) } ?? "—")
                    .foregroundStyle(.secondary)
            }
            .width(min: 70, ideal: 80)
            TableColumn("Last Played") { app in
                Text(app.lastLaunchedAt.map { $0.formatted(.relative(presentation: .named)) } ?? "Never")
                    .foregroundStyle(.secondary)
            }
            .width(min: 90, ideal: 120)
            TableColumn("") { app in
                let running = model.sessions[app.packageName]?.isRunning == true
                Button(running ? "Stop" : "Launch", systemImage: running ? "stop.fill" : "play.fill") {
                    model.toggleRun(app.packageName)
                }
                .labelStyle(.iconOnly)
                .buttonStyle(.borderless)
                .foregroundStyle(running ? Color.red : Color.accentColor)
            }
            .width(32)
        }
        .contextMenu(forSelectionType: String.self) { ids in
            if let id = ids.first, let app = model.entry(id) { AppActions(model: model, app: app) }
        } primaryAction: { ids in
            if let id = ids.first { model.path.append(id) }
        }
    }
}

/// The shared action set for context menus and the app page. Takes the model
/// as a value: menus are hosted by AppKit outside the window's environment.
struct AppActions: View {
    let model: LauncherModel
    let app: AppEntry

    var body: some View {
        let running = model.sessions[app.packageName]?.isRunning == true
        Button(running ? "Stop" : "Launch", systemImage: running ? "stop.fill" : "play.fill") { model.toggleRun(app.packageName) }
        if running {
            Button("Restart", systemImage: "arrow.clockwise") { model.restart(app.packageName) }
        }
        Button(app.isFavorite ? "Remove from Favorites" : "Add to Favorites", systemImage: app.isFavorite ? "star.slash" : "star") {
            model.toggleFavorite(app.packageName)
        }
        Divider()
        Button("Add to Applications", systemImage: "square.and.arrow.down.on.square") { model.attempt(.addToApplications) }
        Button("Export as Mac App…", systemImage: "shippingbox") { model.attempt(.exportMacApp) }
        Button("Check for Update", systemImage: "arrow.down.circle") { model.attempt(.checkForUpdate) }
        Button("Duplicate", systemImage: "plus.square.on.square") { model.attempt(.duplicateApp) }
        Divider()
        Button("Remove from Library…", systemImage: "trash", role: .destructive) { model.requestRemoval(app.packageName) }
    }
}
