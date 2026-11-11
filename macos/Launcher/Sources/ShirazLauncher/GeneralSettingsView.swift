// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

struct LauncherSettingsView: View {
    var body: some View {
        TabView {
            Tab("General", systemImage: "gearshape") { GeneralSettingsView() }
            Tab("Runtime", systemImage: "cpu") { RuntimeSettingsView() }
        }
        .frame(width: 560)
        .overlay(alignment: .bottom) { ToastHost() }
    }
}

struct GeneralSettingsView: View {
    @Environment(LauncherModel.self) private var model
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            Section("Library") {
                LabeledContent("Location") {
                    Button("Show in Finder") { openURL(LibraryStore.defaultDirectory) }
                }
                LabeledContent("Apps", value: "\(model.apps.count)")
            }
            Section("Updates") {
                Toggle("Check for App Updates Automatically", isOn: model.unimplemented(false, .autoUpdates))
            }
        }
        .formStyle(.grouped)
        .frame(height: 260)
    }
}

struct RuntimeSettingsView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Form {
            Section {
                HStack(spacing: 14) {
                    ShirazMark()
                        .frame(width: 30, height: 30)
                        .padding(9)
                        .foregroundStyle(.white)
                        .background(Color.black.gradient, in: .rect(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Shiraz Runtime").font(.headline)
                        switch model.runtimeStatus {
                        case .ready(let location, let version):
                            Text("Version \(version ?? "?")").foregroundStyle(.secondary)
                            Text(location.python.path).font(.caption).foregroundStyle(.tertiary)
                        case .unavailable(let reason):
                            Text(reason).foregroundStyle(.red)
                        }
                    }
                }
            }
            Section("Location") {
                TextField("Checkout", text: $model.runtimeRootOverride, prompt: Text("Detected automatically"))
            }
            Section {
                Button("Check for Runtime Update") { model.attempt(.runtimeUpdate) }
                Button("Create Diagnostics Report…") { model.attempt(.diagnosticsReport) }
            }
        }
        .formStyle(.grouped)
        .frame(height: 360)
    }
}
