// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// Android packages have no system-declared type, so derive one from the extension.
    static let apk = UTType(filenameExtension: "apk", conformingTo: .zip) ?? .zip
}

struct RootView: View {
    @Environment(LauncherModel.self) private var model
    @State private var isTargeted = false

    var body: some View {
        @Bindable var model = model
        NavigationSplitView {
            SidebarView()
                .navigationSplitViewColumnWidth(min: 200, ideal: 230, max: 300)
        } detail: {
            // One stack for every sidebar place: SwiftUI keeps navigation state
            // per split-view column, and swapping in a stack whose path has
            // another element type traps when a page is pushed.
            NavigationStack(path: $model.routes) {
                Group {
                    if model.sidebar == .catalog {
                        CatalogView()
                    } else {
                        LibraryView(scope: scope)
                    }
                }
                .navigationDestination(for: DetailRoute.self) { route in
                    switch route {
                    case .app(let package): AppPage(package: package)
                    case .catalog(let app): CatalogAppPage(app: app)
                    }
                }
            }
        }
        .searchable(text: $model.search, placement: .sidebar, prompt: model.sidebar == .catalog ? "Search F-Droid" : "Search Apps")
        .dropDestination(for: URL.self) { urls, _ in
            model.importAPKs(urls)
            return !urls.isEmpty
        } isTargeted: { isTargeted = $0 }
        .overlay { if isTargeted { DropOverlay() } }
        .overlay(alignment: .bottom) { ToastHost() }
        .fileImporter(isPresented: $model.showImporter, allowedContentTypes: [.apk], allowsMultipleSelection: true) { result in
            switch result {
            case .success(let urls): model.importAPKs(urls)
            case .failure(let error): model.present(error)
            }
        }
        .confirmationDialog(
            removalTitle,
            isPresented: Binding(get: { model.pendingRemoval != nil }, set: { if !$0 { model.pendingRemoval = nil } }),
            titleVisibility: .visible
        ) {
            Button("Remove", role: .destructive) {
                if let package = model.pendingRemoval { model.remove(package) }
            }
        } message: {
            Text("The imported APK and its settings are deleted. The original file is not touched.")
        }
        .alert(
            model.consent?.title ?? "",
            isPresented: Binding(get: { model.consent != nil }, set: { if !$0 { model.answerConsent(false) } }),
            presenting: model.consent
        ) { prompt in
            Button(prompt.action, role: prompt.destructive ? .destructive : nil) { model.answerConsent(true) }
            Button("Don't Allow", role: .cancel) { model.answerConsent(false) }
        } message: { prompt in
            Text(prompt.message)
        }
        .onChange(of: model.consent?.id) { _, id in
            // An app is waiting on this answer: bring the launcher forward.
            if id != nil { NSApplication.shared.activate() }
        }
    }

    /// The selected place's own path, seen as routes.
    private var scope: LibraryScope {
        if case .library(let scope) = model.sidebar { return scope }
        return .all
    }

    private var removalTitle: String {
        let name = model.pendingRemoval.flatMap { model.entry($0)?.displayName } ?? "app"
        return "Remove \(name) from the library?"
    }
}

/// Shown while an APK is dragged over the window.
struct DropOverlay: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.tint.opacity(0.10))
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(.tint, style: StrokeStyle(lineWidth: 2.5, dash: [9, 7]))
            VStack(spacing: 14) {
                FloatingGhost(size: 84)
                Text("Drop to add").font(.title2.weight(.semibold))
            }
            .padding(28)
            .glassEffect(.regular, in: .rect(cornerRadius: 28))
        }
        .padding(14)
        .allowsHitTesting(false)
        .transition(.opacity)
    }
}

/// Bottom toast. One at a time; a new message replaces the current one.
struct ToastHost: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        ZStack {
            if let toast = model.toast {
                ToastView(toast: toast) { model.dismissToast() }
                    .id(toast.id)
                    .transition(.move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.92)))
            }
        }
        .padding(.bottom, 22)
        .animation(.spring(response: 0.42, dampingFraction: 0.82), value: model.toast?.id)
    }
}

struct ToastView: View {
    let toast: Toast
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            badge
            VStack(alignment: .leading, spacing: 1) {
                Text(toast.title).font(.headline)
                if let message = toast.message {
                    Text(message).font(.callout).foregroundStyle(.secondary).lineLimit(4)
                        .frame(width: message.count > 64 ? 380 : nil, alignment: .leading)
                }
            }
            .fixedSize(horizontal: (toast.message?.count ?? 0) <= 64, vertical: false)
            Button("Dismiss", systemImage: "xmark", action: dismiss)
                .labelStyle(.iconOnly)
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
                .imageScale(.small)
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 16)
        .glassEffect(.regular.interactive(), in: .capsule)
        .shadow(color: .black.opacity(0.12), radius: 18, y: 8)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("toast")
        .accessibilityAddTraits(.isStaticText)
    }

    @ViewBuilder private var badge: some View {
        switch toast.style {
        case .notImplemented:
            ShirazMark()
                .frame(width: 26, height: 26)
                .foregroundStyle(.primary)
                .padding(6)
                .background(.purple.gradient.opacity(0.35), in: .circle)
        case .failure:
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title3)
                .foregroundStyle(.orange)
                .frame(width: 38, height: 38)
        case .info:
            Image(systemName: "checkmark.circle.fill")
                .font(.title3)
                .foregroundStyle(.green)
                .frame(width: 38, height: 38)
        }
    }
}
