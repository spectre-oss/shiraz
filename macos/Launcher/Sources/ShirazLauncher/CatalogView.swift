// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI

/// Browse the verified f-droid.org catalog and add apps to the library.
struct CatalogView: View {
    @Environment(LauncherModel.self) private var model
    @Environment(CatalogModel.self) private var catalog

    /// The root of the detail column's stack while the catalog is selected
    /// (RootView owns the one NavigationStack; see LauncherModel.routes).
    var body: some View {
        content
            .navigationTitle("F-Droid")
            .navigationSubtitle(subtitle)
            .toolbar { CatalogToolbar() }
            .onAppear { catalog.open() }
    }

    @ViewBuilder private var content: some View {
        switch catalog.phase {
        case .idle, .loading:
            VStack(spacing: 18) {
                FloatingGhost(size: 96)
                Text("Fetching the F-Droid catalog").font(.title2.weight(.semibold))
                Text("The index is checked against f-droid.org's pinned signing certificate before anything is shown.")
                    .font(.callout).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 380)
                ProgressView().controlSize(.small)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed(let message):
            ContentUnavailableView {
                Label("Catalog unavailable", systemImage: "wifi.exclamationmark")
            } description: {
                Text(message)
            } actions: {
                Button("Try Again") { Task { await catalog.refresh() } }
                    .buttonStyle(.glassProminent)
            }
        case .loaded:
            let apps = catalog.visibleApps(search: model.search)
            if apps.isEmpty {
                if model.search.isEmpty {
                    ContentUnavailableView("Nothing here", systemImage: "tray",
                                           description: Text(catalog.scope == .library ? "Apps you add from F-Droid appear here." : "No apps match these filters."))
                } else {
                    ContentUnavailableView.search(text: model.search)
                }
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 300, maximum: 420), spacing: 14)], spacing: 14) {
                        ForEach(apps) { CatalogCard(app: $0) }
                    }
                    .padding(24)
                }
            }
        }
    }

    private var subtitle: String {
        guard catalog.phase == .loaded else { return "" }
        let count = catalog.visibleApps(search: model.search).count
        var parts = [count == 1 ? "1 app" : "\(count.formatted()) apps"]
        if catalog.isRefreshing {
            parts.append("Updating…")
        } else if let date = catalog.catalogDate {
            parts.append("Checked \(date.formatted(.relative(presentation: .named)))")
        }
        return parts.joined(separator: " · ")
    }
}

struct CatalogToolbar: ToolbarContent {
    @Environment(CatalogModel.self) private var catalog

    var body: some ToolbarContent {
        @Bindable var catalog = catalog
        ToolbarItemGroup {
            Picker("Show", selection: $catalog.scope) {
                ForEach(CatalogModel.Scope.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .help("Which catalog apps to show")
            Menu("Category", systemImage: catalog.category == nil ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill") {
                Picker("Category", selection: $catalog.category) {
                    Text("All Categories").tag(String?.none)
                    Divider()
                    ForEach(catalog.catalog?.categories ?? [], id: \.self) { Text($0).tag(String?.some($0)) }
                }
                .pickerStyle(.inline)
            }
            .help(catalog.category ?? "Filter by category")
            Menu("Sort", systemImage: "arrow.up.arrow.down") {
                Picker("Sort By", selection: $catalog.sort) {
                    ForEach(CatalogModel.Sort.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.inline)
            }
            .help("Sort apps")
        }
        ToolbarSpacer(.fixed)
        ToolbarItem {
            if catalog.isRefreshing {
                ProgressView().controlSize(.small).help("Updating the catalog")
            } else {
                Button("Refresh", systemImage: "arrow.clockwise") { Task { await catalog.refresh() } }
                    .help("Download and verify the F-Droid index again")
                    .disabled(catalog.phase == .loading)
            }
        }
    }
}

/// A catalog app's icon from f-droid.org, or a monogram while it loads or when it has none.
struct CatalogIcon: View {
    let app: CatalogApp
    var size: CGFloat = 56

    var body: some View {
        AsyncImage(url: app.iconURL, transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
            if case .success(let image) = phase {
                image.resizable().interpolation(.high).aspectRatio(contentMode: .fit)
            } else {
                RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)
                    .fill(monogramColor.gradient.opacity(phase.error == nil && app.iconURL != nil ? 0.35 : 1))
                    .overlay {
                        Text(String(app.name.prefix(1)).uppercased())
                            .font(.system(size: size * 0.46, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                    }
            }
        }
        .frame(width: size, height: size)
        .clipShape(.rect(cornerRadius: size * 0.225, style: .continuous))
    }

    private var monogramColor: Color {
        let palette: [Color] = [.indigo, .teal, .orange, .pink, .mint, .purple, .blue, .green]
        let sum = app.packageName.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        return palette[sum % palette.count]
    }
}

struct CatalogCard: View {
    @Environment(CatalogModel.self) private var catalog
    @State private var hovering = false
    let app: CatalogApp

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            CatalogIcon(app: app, size: 56)
                .shadow(color: .black.opacity(0.12), radius: 6, y: 3)
            VStack(alignment: .leading, spacing: 3) {
                Text(app.name).font(.headline).lineLimit(1)
                Text(app.summary.isEmpty ? app.packageName : app.summary)
                    .font(.callout).foregroundStyle(.secondary)
                    .lineLimit(2, reservesSpace: true)
                HStack(spacing: 6) {
                    if let category = app.categories.first {
                        Text(category).lineLimit(1)
                    }
                    if let size = app.size {
                        Text("·")
                        Text(size.formatted(.byteCount(style: .file)))
                    }
                }
                .font(.caption).foregroundStyle(.tertiary)
            }
            Spacer(minLength: 0)
            CatalogActionButton(app: app, compact: true)
        }
        .padding(14)
        .background(.background.secondary.opacity(hovering ? 1 : 0.6), in: .rect(cornerRadius: 16, style: .continuous))
        .contentShape(.rect(cornerRadius: 16))
        .onHover { hovering = $0 }
        .onTapGesture { catalog.showDetails(app) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(app.name)
        .accessibilityAction(named: "Show Details") { catalog.showDetails(app) }
    }
}

/// Get, Update, Open, or why the app cannot run here.
struct CatalogActionButton: View {
    @Environment(CatalogModel.self) private var catalog
    let app: CatalogApp
    var compact = false

    var body: some View {
        if let step = catalog.installing[app.packageName] {
            HStack(spacing: 6) {
                ProgressView().controlSize(.small)
                if !compact { Text(step == .downloading ? "Downloading…" : "Adding…").foregroundStyle(.secondary) }
            }
            .help(step == .downloading ? "Downloading and checking the APK" : "Adding to the library")
        } else if !app.compatible {
            Label(compact ? "Unavailable" : "Not available for this Mac", systemImage: "nosign")
                .labelStyle(.titleAndIcon)
                .font(compact ? .caption : .callout)
                .foregroundStyle(.secondary)
                .help(app.reason ?? "No APK fits this host")
        } else {
            switch catalog.status(app) {
            case .notInLibrary:
                Button("Get") { catalog.install(app) }
                    .buttonStyle(.glassProminent)
                    .controlSize(compact ? .regular : .large)
                    .help("Download from F-Droid and add to the library")
            case .updateAvailable(let current):
                Button("Update") { catalog.install(app) }
                    .buttonStyle(.glassProminent)
                    .tint(.green)
                    .controlSize(compact ? .regular : .large)
                    .help("Replace \(current) with \(app.versionName ?? "the catalog version")")
            case .inLibrary:
                Button("Open") { catalog.openInLibrary(app.packageName) }
                    .buttonStyle(.glass)
                    .controlSize(compact ? .regular : .large)
                    .help("Show in the library")
            }
        }
    }
}

struct CatalogAppPage: View {
    @Environment(CatalogModel.self) private var catalog
    let app: CatalogApp

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                if !app.compatible {
                    Label {
                        Text("No APK of this app fits the macOS runtime: \(app.reason ?? "unknown reason").")
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.orange.opacity(0.12), in: .rect(cornerRadius: 12))
                } else {
                    Text("This APK's native code and Android API level fit Shiraz on this Mac. It has not been tested here, so it may still fail to start.")
                        .font(.callout).foregroundStyle(.secondary)
                }
                facts
                if !app.antiFeatures.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Anti-Features").font(.headline)
                        Text(app.antiFeatures.joined(separator: ", ")).foregroundStyle(.secondary)
                    }
                }
                if !app.screenshots.isEmpty { screenshots }
                if !app.description.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("About").font(.headline)
                        Text(app.description).textSelection(.enabled).lineSpacing(3)
                    }
                }
                links
            }
            .padding(32)
            .frame(maxWidth: 820, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle(app.name)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 22) {
            CatalogIcon(app: app, size: 112)
                .shadow(color: .black.opacity(0.18), radius: 16, y: 8)
            VStack(alignment: .leading, spacing: 6) {
                Text(app.name).font(.largeTitle.weight(.bold))
                if !app.summary.isEmpty { Text(app.summary).font(.title3).foregroundStyle(.secondary) }
                if let author = app.authorName { Text(author).font(.callout).foregroundStyle(.tertiary) }
                CatalogActionButton(app: app)
                    .padding(.top, 6)
            }
        }
    }

    private var facts: some View {
        Grid(alignment: .leading, horizontalSpacing: 28, verticalSpacing: 8) {
            row("Version", app.versionName.map { v in app.versionCode.map { "\(v) (\($0))" } ?? v })
            row("Size", app.size.map { $0.formatted(.byteCount(style: .file)) })
            row("License", app.license)
            row("Updated", app.lastUpdated.map { $0.formatted(date: .abbreviated, time: .omitted) })
            row("Android", app.minSdk.map { "API \($0) or later" })
            row("Native code", app.nativeCode.isEmpty ? "None (Java only)" : app.nativeCode.joined(separator: ", "))
            row("Categories", app.categories.isEmpty ? nil : app.categories.joined(separator: ", "))
            row("Package", app.packageName)
        }
        .font(.callout)
    }

    @ViewBuilder private func row(_ title: String, _ value: String?) -> some View {
        if let value {
            GridRow {
                Text(title).foregroundStyle(.secondary)
                Text(value).textSelection(.enabled)
            }
        }
    }

    private var screenshots: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 12) {
                ForEach(app.screenshots, id: \.self) { url in
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().aspectRatio(contentMode: .fit)
                        } else {
                            RoundedRectangle(cornerRadius: 12).fill(.quaternary)
                                .aspectRatio(9.0 / 19.5, contentMode: .fit)
                        }
                    }
                    .frame(height: 340)
                    .clipShape(.rect(cornerRadius: 12))
                }
            }
        }
        .scrollIndicators(.automatic)
    }

    private var links: some View {
        HStack(spacing: 16) {
            if let url = app.webURL { Link("F-Droid Page", destination: url) }
            if let url = app.sourceCode { Link("Source Code", destination: url) }
        }
        .font(.callout)
    }
}
