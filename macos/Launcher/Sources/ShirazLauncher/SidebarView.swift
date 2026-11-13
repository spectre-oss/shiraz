// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

struct SidebarView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        @Bindable var model = model
        List(selection: $model.sidebar) {
            Section("Library") {
                ForEach(LibraryScope.allCases) { scope in
                    Label(scope.title, systemImage: scope.symbol)
                        .badge(badge(for: scope))
                        .tag(SidebarItem.library(scope))
                }
            }
            Section("Discover") {
                Label("F-Droid Catalog", systemImage: "sparkles").tag(SidebarItem.catalog)
                Label("Updates", systemImage: "arrow.down.circle").tag(SidebarItem.feature(.updates))
            }
            Section("Collections") {
                Label("New Collection", systemImage: "plus.rectangle.on.folder").tag(SidebarItem.feature(.collections))
            }
        }
        .listStyle(.sidebar)
        // Places that are not built yet report it and keep the current selection.
        .onChange(of: model.sidebar) { old, new in
            if case .feature(let feature) = new {
                model.sidebar = old
                model.attempt(feature)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { RuntimeBadge() }
    }

    private func badge(for scope: LibraryScope) -> Int {
        switch scope {
        case .running: model.runningCount
        case .all: model.apps.count
        default: 0
        }
    }
}

/// The runtime's identity and health, pinned under the sidebar.
struct RuntimeBadge: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        SettingsLink {
            HStack(spacing: 10) {
                ShirazMark()
                    .frame(width: 22, height: 22)
                    .padding(7)
                    .foregroundStyle(.white)
                    .background(Color.black.gradient, in: .rect(cornerRadius: 9, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Shiraz").font(.headline)
                    status
                }
                Spacer(minLength: 0)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .padding(12)
        .help("Runtime settings")
    }

    @ViewBuilder private var status: some View {
        switch model.runtimeStatus {
        case .ready(_, let version):
            HStack(spacing: 5) {
                Circle().fill(.green).frame(width: 6, height: 6)
                Text("Runtime \(version ?? "ready")")
            }
            .font(.caption).foregroundStyle(.secondary)
        case .unavailable:
            HStack(spacing: 5) {
                Circle().fill(.red).frame(width: 6, height: 6)
                Text("Runtime not found")
            }
            .font(.caption).foregroundStyle(.secondary)
        }
    }
}
