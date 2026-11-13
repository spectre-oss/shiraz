// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

@main
struct LauncherApp: App {
    @State private var model: LauncherModel
    @State private var catalog: CatalogModel

    init() {
        let model = LauncherModel()
        _model = State(initialValue: model)
        _catalog = State(initialValue: CatalogModel(launcher: model))
    }

    var body: some Scene {
        Window("Shiraz", id: "library") {
            RootView()
                .environment(model)
                .environment(catalog)
                .frame(minWidth: 820, minHeight: 540)
        }
        .defaultSize(width: 1120, height: 720)
        .commands { LauncherCommands(model: model) }

        Settings {
            LauncherSettingsView()
                .environment(model)
        }

        MenuBarExtra {
            MenuBarContent()
                .environment(model)
        } label: {
            ShirazMark()
                .frame(width: 16, height: 16)
                .accessibilityLabel("Shiraz")
        }
    }
}

struct LauncherCommands: Commands {
    let model: LauncherModel

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("Add APK…") { model.showImporter = true }
                .keyboardShortcut("o")
        }
        CommandGroup(after: .sidebar) {
            Button("as Icons") { model.showsGrid = true }.keyboardShortcut("1")
            Button("as List") { model.showsGrid = false }.keyboardShortcut("2")
            Divider()
        }
        CommandMenu("App") {
            let current = model.path.last.flatMap(model.entry)
            let running = current.map { model.sessions[$0.packageName]?.isRunning == true } ?? false
            Button(running ? "Stop" : "Launch") { current.map { model.toggleRun($0.packageName) } }
                .keyboardShortcut("r")
                .disabled(current == nil)
            Button("Restart") { current.map { model.restart($0.packageName) } }
                .keyboardShortcut("r", modifiers: [.command, .shift])
                .disabled(!running)
            Button(current?.isFavorite == true ? "Remove from Favorites" : "Add to Favorites") {
                current.map { model.toggleFavorite($0.packageName) }
            }
            .keyboardShortcut("d")
            .disabled(current == nil)
            Divider()
            Button("Add to Applications") { model.attempt(.addToApplications) }.disabled(current == nil)
            Button("Export as Mac App…") { model.attempt(.exportMacApp) }.disabled(current == nil)
            Divider()
            Button("Stop All") { model.stopAll() }
                .keyboardShortcut(".", modifiers: [.command, .option])
                .disabled(model.runningCount == 0)
            Button("Remove from Library…") { current.map { model.requestRemoval($0.packageName) } }
                .keyboardShortcut(.delete)
                .disabled(current == nil)
        }
    }
}

/// The menu bar ghost: what is running, and a quick way to start something.
struct MenuBarContent: View {
    @Environment(LauncherModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        let running = model.apps.filter { model.sessions[$0.packageName]?.isRunning == true }
        let recent = model.visibleApps(in: .recent).prefix(5)
        if running.isEmpty {
            Text("Nothing running")
        } else {
            Section("Running") {
                ForEach(running) { app in
                    Button("Stop \(app.displayName)") { model.stop(app.packageName) }
                }
                Button("Stop All") { model.stopAll() }
            }
        }
        if !recent.isEmpty {
            Section("Recently Played") {
                ForEach(Array(recent)) { app in
                    Button("Launch \(app.displayName)") { model.launch(app.packageName) }
                        .disabled(model.sessions[app.packageName]?.isRunning == true)
                }
            }
        }
        Divider()
        Button("Open Shiraz") { openWindow(id: "library") }
        SettingsLink { Text("Settings…") }
    }
}
