// SPDX-License-Identifier: GPL-3.0-only
import SwiftUI

@main
struct LauncherApp: App {
    @State private var model = LauncherModel()

    var body: some Scene {
        WindowGroup("Shiraz Launcher") {
            ContentView()
                .environment(model)
                .frame(minWidth: 760, minHeight: 480)
        }
        .defaultSize(width: 980, height: 620)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Add APK…") { model.showImporter = true }
                    .keyboardShortcut("o")
            }
        }

        Settings {
            GeneralSettingsView()
                .environment(model)
        }
    }
}
