// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

struct GeneralSettingsView: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Form {
            Section("Runtime") {
                TextField("Shiraz checkout", text: $model.runtimeRootOverride, prompt: Text("Detected automatically"))
                    .textFieldStyle(.roundedBorder)
                switch model.runtimeStatus {
                case .ready(let location, let version):
                    Label("Shiraz \(version ?? "?") at \(location.root.path)", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    Text("Python: \(location.python.path)").font(.callout).foregroundStyle(.secondary)
                case .unavailable(let reason):
                    Label(reason, systemImage: "xmark.octagon.fill").foregroundStyle(.red)
                }
            }
            Section("Library") {
                LabeledContent("Location", value: LibraryStore.defaultDirectory.path)
                Text("Imported APKs are copied here unchanged.").font(.callout).foregroundStyle(.secondary)
            }
        }
        .formStyle(.grouped)
        .frame(width: 520)
        .padding()
    }
}
