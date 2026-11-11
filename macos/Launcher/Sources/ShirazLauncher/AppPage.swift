// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

enum AppSection: String, CaseIterable, Identifiable {
    case overview, settings, permissions, storage, console
    var id: String { rawValue }
    var title: String {
        switch self {
        case .overview: "Overview"
        case .settings: "Settings"
        case .permissions: "Permissions"
        case .storage: "Storage"
        case .console: "Console"
        }
    }
}

struct AppPage: View {
    @Environment(LauncherModel.self) private var model
    let package: String
    @State private var section: AppSection = .overview

    var body: some View {
        if let app = model.entry(package) {
            VStack(spacing: 0) {
                AppHero(app: app, compact: section != .overview)
                    .animation(.smooth(duration: 0.35), value: section)
                Picker("Section", selection: $section) {
                    ForEach(AppSection.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .frame(maxWidth: 520)
                .padding(.vertical, 14)
                Divider()
                Group {
                    switch section {
                    case .overview: OverviewSection(app: app)
                    case .settings: SettingsSection(app: app)
                    case .permissions: PermissionsSection(app: app)
                    case .storage: StorageSection(app: app)
                    case .console: ConsoleSection(app: app)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .background(.windowBackground)
            .navigationTitle(app.displayName)
            .toolbar {
                ToolbarItem {
                    Button(app.isFavorite ? "Unfavorite" : "Favorite", systemImage: app.isFavorite ? "star.fill" : "star") {
                        model.toggleFavorite(package)
                    }
                    .help(app.isFavorite ? "Remove from Favorites" : "Add to Favorites")
                }
            }
        } else {
            ContentUnavailableView("App removed", systemImage: "trash")
        }
    }
}

/// The app's icon, washed out into a backdrop of its own colours.
struct AppHero: View {
    @Environment(LauncherModel.self) private var model
    @Environment(\.openURL) private var openURL
    let app: AppEntry
    var compact = false

    var body: some View {
        let session = model.sessions[app.packageName]
        let running = session?.isRunning == true
        let accent = model.accent(app.packageName)
        HStack(alignment: .center, spacing: 26) {
            AppIconView(app: app, image: model.icons[app.packageName], size: compact ? 72 : 128)
                .shadow(color: accent.opacity(0.4), radius: 22, y: 12)
                .runningGlow(running, color: accent, radius: 40)
                .keyframeAnimator(initialValue: 1.0, trigger: session?.id) { content, scale in
                    content.scaleEffect(scale)
                } keyframes: { _ in
                    KeyframeTrack {
                        CubicKeyframe(0.9, duration: 0.12)
                        SpringKeyframe(1.0, duration: 0.5, spring: .bouncy)
                    }
                }
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(app.displayName).font(.system(size: compact ? 24 : 34, weight: .bold, design: .rounded))
                    Text(app.packageName).font(compact ? .callout : .title3).foregroundStyle(.secondary).textSelection(.enabled)
                }
                if !compact { chips(session: session, running: running) }
            }
            Spacer(minLength: 0)
            if compact { actions(running: running, accent: accent) }
        }
        .padding(.horizontal, 36)
        .padding(.top, compact ? 18 : 30)
        .padding(.bottom, compact ? 14 : 24)
        .frame(maxWidth: .infinity)
        .background { backdrop(accent: accent) }
    }

    private func chips(session: Session?, running: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                if let version = app.versionName { Chip(text: "Version \(version)", symbol: "tag") }
                Chip(text: compatibility, symbol: app.compatibility.isLaunchable ? "checkmark.seal" : "exclamationmark.triangle")
                if let session { Chip(text: session.state.summary, symbol: running ? "dot.radiowaves.left.and.right" : "moon.zzz") }
            }
            actions(running: running, accent: model.accent(app.packageName))
                .padding(.top, 4)
        }
    }

    private func actions(running: Bool, accent: Color) -> some View {
        HStack(spacing: 10) {
            if running {
                Button("Stop", systemImage: "stop.fill") { model.stop(app.packageName) }
                    .buttonStyle(.glassProminent)
                    .tint(.red)
                Button("Restart", systemImage: "arrow.clockwise") { model.restart(app.packageName) }
                    .buttonStyle(.glass)
            } else {
                Button("Launch", systemImage: "play.fill") { model.launch(app.packageName) }
                    .buttonStyle(.glassProminent)
                    .tint(accent)
                    .keyboardShortcut(.defaultAction)
            }
            Menu("More", systemImage: "ellipsis") {
                AppActions(model: model, app: app)
                Divider()
                Button("Show APK in Finder", systemImage: "folder") {
                    openURL(model.apkURL(app.packageName).deletingLastPathComponent())
                }
                Button("Replace APK…", systemImage: "arrow.triangle.2.circlepath") { model.attempt(.replaceAPK) }
            }
            .menuIndicator(.hidden)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .labelStyle(.iconOnly)
            .fixedSize()
            .help("More actions")
        }
        .controlSize(.large)
    }

    private var compatibility: String {
        switch app.compatibility {
        case .javaOnly: "Java"
        case .arm64: "Java + arm64"
        case .unsupported: "Unsupported ABI"
        }
    }

    @ViewBuilder private func backdrop(accent: Color) -> some View {
        ZStack {
            if let image = model.icons[app.packageName] {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .scaleEffect(1.6)
                    .blur(radius: 70)
                    .saturation(1.4)
                    .opacity(0.55)
            } else {
                accent.opacity(0.35)
            }
            Rectangle()
                .fill(.windowBackground)
                .mask(LinearGradient(colors: [.clear, .black.opacity(0.85), .black], startPoint: .top, endPoint: .bottom))
        }
        .clipped()
        .ignoresSafeArea(edges: .top)
    }
}

struct Chip: View {
    let text: String
    let symbol: String

    var body: some View {
        Label(text, systemImage: symbol)
            .font(.caption.weight(.medium))
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(.thinMaterial, in: .capsule)
    }
}
