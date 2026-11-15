// SPDX-License-Identifier: GPL-3.0-only
import LauncherCore
import SwiftUI
import UniformTypeIdentifiers

/// Colours of the approved home design: dark indigo grounds, amber accent.
enum HomePalette {
    static let ground = Color(hex: 0x14131c)
    static let wall = Color(hex: 0x1a1830)
    static let card = Color(hex: 0x211f36)
    static let cardBorder = Color(hex: 0x33304a)
    static let chip = Color(hex: 0x24223a)
    static let menu = Color(hex: 0x2a2740)
    static let menuBorder = Color(hex: 0x45415f)
    static let amber = Color(hex: 0xf5b85c)
    static let onAmber = Color(hex: 0x1c1404)
    static let ready = Color(hex: 0x4ade80)
    static let text = Color(hex: 0xf1eff7)
    static let secondary = Color(hex: 0xb9b5cc)
    static let danger = Color(hex: 0xff9d94)
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255,
                  blue: Double(hex & 0xff) / 255)
    }
}

/// Where a dragged icon lands relative to the icon under it.
enum HomeDropZone: Equatable { case before, after, combine }

extension LauncherModel {
    /// Apply a home drop: reorder, or make or join a folder.
    func dropOnHome(_ id: String, onto target: String, zone: HomeDropZone) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            updateHome { layout in
                switch zone {
                case .before: layout.move(id, before: target)
                case .after: layout.move(id, after: target)
                case .combine: layout.combine(id, onto: target)
                }
            }
        }
    }

    /// Tap on a home icon: launch, or bring a running app's window forward.
    func open(_ package: String) {
        if sessions[package]?.isRunning == true {
            showRunning(package)
        } else {
            launch(package)
        }
    }

    /// The runtime gives each APK its own macOS app identity (HostIdentity);
    /// activate it. Until its window exists, show the app page instead.
    func showRunning(_ package: String) {
        let identifier = HostIdentity.bundleIdentifier(for: package)
        if let host = NSRunningApplication.runningApplications(withBundleIdentifier: identifier).first {
            host.activate()
        } else {
            show(package)
        }
    }
}

// MARK: Screen

/// The Home place: wallpaper, clock and runtime status, search, Continue
/// row, the arranged grid with folders, and the dock.
struct HomeScreen: View {
    @Environment(LauncherModel.self) private var model
    @Environment(CatalogModel.self) private var catalog
    @State private var openFolder: UUID?
    @State private var choosingWallpaper = false

    var body: some View {
        @Bindable var model = model
        let searching = !model.search.trimmingCharacters(in: .whitespaces).isEmpty
        ZStack {
            WallpaperView(wallpaper: model.home.wallpaper, image: model.wallpaperImage)
                .ignoresSafeArea()
            ScrollView {
                VStack(spacing: 26) {
                    HomeHeader()
                    PillSearch(text: $model.search)
                    if searching {
                        HomeSearchResults(query: model.search)
                    } else {
                        let recent = ContinueRow.pick(model.apps, running: runningPackages)
                        if !recent.isEmpty { ContinueSection(apps: recent) }
                        HomeGrid(openFolder: $openFolder)
                    }
                }
                .padding(.horizontal, 40)
                .padding(.top, 20)
                .padding(.bottom, 130)
                .frame(maxWidth: 1180)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.automatic)
        }
        .overlay(alignment: .bottom) {
            DockBar()
                .padding(.bottom, 18)
                .frame(maxWidth: .infinity)
                // Icons scrolled under the dock fade out instead of colliding with it.
                .background(alignment: .bottom) {
                    LinearGradient(colors: [.clear, HomePalette.ground.opacity(0.85)], startPoint: .top, endPoint: .bottom)
                        .frame(height: 130)
                        .allowsHitTesting(false)
                        .ignoresSafeArea()
                }
        }
        .overlay {
            if let id = openFolder, let folder = model.home.folder(id: id) {
                FolderOverlay(folder: folder) { withAnimation(.smooth(duration: 0.25)) { openFolder = nil } }
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .foregroundStyle(HomePalette.text)
        .environment(\.colorScheme, .dark)
        .onChange(of: model.search) { _, query in
            if !query.isEmpty { catalog.loadCachedForSearch() }
        }
        .fileImporter(isPresented: $choosingWallpaper, allowedContentTypes: [.image]) { result in
            switch result {
            case .success(let url): model.importWallpaper(url)
            case .failure(let error): model.present(error)
            }
        }
        .navigationTitle("Home")
        .navigationSubtitle(model.apps.count == 1 ? "1 app" : "\(model.apps.count) apps")
        .toolbar { HomeToolbar(choosingWallpaper: $choosingWallpaper) }
        .overlay {
            if model.isImporting {
                ProgressView("Adding…")
                    .padding(20)
                    .glassEffect(.regular, in: .rect(cornerRadius: 16))
            }
        }
    }

    private var runningPackages: Set<String> {
        Set(model.sessions.values.filter(\.isRunning).map(\.package))
    }
}

struct HomeToolbar: ToolbarContent {
    @Environment(LauncherModel.self) private var model
    @Binding var choosingWallpaper: Bool

    var body: some ToolbarContent {
        @Bindable var model = model
        ToolbarItemGroup {
            Picker("View", selection: $model.showsGrid) {
                Label("Home", systemImage: "square.grid.2x2").tag(true)
                Label("List", systemImage: "list.bullet").tag(false)
            }
            .pickerStyle(.segmented)
            .help("Show the home screen or a list")
            Menu("Wallpaper", systemImage: "photo") {
                Picker("Wallpaper", selection: Binding(get: { model.home.wallpaper }, set: { model.setWallpaper($0) })) {
                    ForEach(Wallpaper.builtIns, id: \.self) { name in
                        Text(WallpaperView.title(name)).tag(Wallpaper.builtIn(name))
                    }
                    if case .image = model.home.wallpaper {
                        Text("Your Picture").tag(model.home.wallpaper)
                    }
                }
                .pickerStyle(.inline)
                Divider()
                Button("Choose Picture…") { choosingWallpaper = true }
            }
            .help("Change the home wallpaper")
        }
        ToolbarSpacer(.fixed)
        ToolbarItem {
            Button("Add APK", systemImage: "plus") { model.showImporter = true }
                .help("Add an APK to the library")
        }
    }
}

// MARK: Wallpaper

struct WallpaperView: View {
    let wallpaper: Wallpaper
    let image: CGImage?

    static func title(_ name: String) -> String {
        switch name {
        case "contours": "Contours"
        case "dusk": "Dusk"
        case "plain": "Plain"
        default: name.capitalized
        }
    }

    var body: some View {
        switch wallpaper {
        case .image where image != nil:
            Image(decorative: image!, scale: 1)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .overlay(Color.black.opacity(0.28))
        case .builtIn("dusk"):
            ZStack {
                LinearGradient(colors: [HomePalette.ground, Color(hex: 0x2a1f3d), Color(hex: 0x3d2a3a)],
                               startPoint: .top, endPoint: .bottom)
                RadialGradient(colors: [HomePalette.amber.opacity(0.22), .clear], center: .bottom,
                               startRadius: 0, endRadius: 520)
            }
        case .builtIn("plain"):
            HomePalette.wall
        default:
            Contours()
        }
    }

    /// The default: faint contour lines over indigo, after the approved design.
    struct Contours: View {
        var body: some View {
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(HomePalette.wall))
                let sx = size.width / 1072, sy = size.height / 800
                var lines = Path()
                for step in 0..<4 {
                    let d = CGFloat(step) * 60, e = CGFloat(step) * 20
                    lines.move(to: CGPoint(x: -40 * sx, y: (520 + d) * sy))
                    lines.addCurve(to: CGPoint(x: (560 + e) * sx, y: (500 + d) * sy),
                                   control1: CGPoint(x: (180 + e) * sx, y: (380 + d) * sy),
                                   control2: CGPoint(x: (320 + e) * sx, y: (640 + d) * sy))
                    lines.addCurve(to: CGPoint(x: 1120 * sx, y: (460 + d) * sy),
                                   control1: CGPoint(x: (800 + e) * sx, y: (360 + d) * sy),
                                   control2: CGPoint(x: (900 + e) * sx, y: (320 + d) * sy))
                }
                for (rx, ry) in [(210.0, 110.0), (150.0, 74.0), (90.0, 40.0)] {
                    lines.addEllipse(in: CGRect(x: (820 - rx) * sx, y: (170 - ry) * sy, width: 2 * rx * sx, height: 2 * ry * sy))
                }
                context.stroke(lines, with: .color(.white.opacity(0.055)), lineWidth: 1.5)
            }
        }
    }
}

// MARK: Header

struct HomeHeader: View {
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .bottom, spacing: 24) {
                Clock()
                RuntimeChip().padding(.bottom, 2)
                Spacer(minLength: 0)
            }
            VStack(alignment: .leading, spacing: 12) {
                Clock()
                RuntimeChip()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    struct Clock: View {
        var body: some View {
            TimelineView(.everyMinute) { context in
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.date, format: .dateTime.hour().minute())
                        .font(.system(size: 54, weight: .light))
                        .monospacedDigit()
                        .tracking(-1)
                    Text(context.date, format: .dateTime.weekday(.wide).month(.wide).day())
                        .font(.system(size: 14))
                        .foregroundStyle(HomePalette.secondary)
                }
                .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
            }
        }
    }
}

/// Runtime version and health, and how many apps run. Opens Settings.
struct RuntimeChip: View {
    @Environment(LauncherModel.self) private var model

    var body: some View {
        SettingsLink {
            HStack(spacing: 8) {
                switch model.runtimeStatus {
                case .ready(_, let version):
                    Circle().fill(HomePalette.ready).frame(width: 7, height: 7)
                    Text("Runtime \(version ?? "") ready")
                case .unavailable:
                    Circle().fill(.red).frame(width: 7, height: 7)
                    Text("Runtime not found")
                }
                if model.runningCount > 0 {
                    Text(model.runningCount == 1 ? "· 1 app running" : "· \(model.runningCount) apps running")
                        .foregroundStyle(HomePalette.secondary)
                }
            }
            .font(.system(size: 13))
            .padding(.vertical, 9)
            .padding(.horizontal, 14)
            .background(HomePalette.chip, in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .help("Runtime settings")
        .accessibilityIdentifier("runtime-chip")
    }
}

struct PillSearch: View {
    @Binding var text: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(Color(hex: 0xcfcbe0))
            TextField("Search apps and F-Droid", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
            if !text.isEmpty {
                Button("Clear", systemImage: "xmark.circle.fill") { text = "" }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.plain)
                    .foregroundStyle(HomePalette.secondary)
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 44)
        .frame(maxWidth: 420)
        .background(.white.opacity(0.10), in: .capsule)
        .overlay(Capsule().strokeBorder(.white.opacity(0.14)))
        .accessibilityIdentifier("home-search")
    }
}

// MARK: Continue

struct ContinueSection: View {
    let apps: [AppEntry]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Continue").font(.system(size: 13, weight: .semibold)).foregroundStyle(HomePalette.secondary)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 220, maximum: 380), spacing: 16)], alignment: .leading, spacing: 16) {
                ForEach(apps) { ContinueCard(app: $0) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A recent or running app. There is no captured app frame yet, so the
/// card shows the icon only.
struct ContinueCard: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry

    var body: some View {
        let since = model.runningSince(app.packageName)
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                HomeIcon(app: app, size: 48)
                Text(app.displayName).font(.system(size: 16, weight: .semibold)).lineLimit(1).padding(.top, 6)
                Group {
                    if let since {
                        Text("Running · \(since, style: .relative)")
                    } else if let played = app.lastLaunchedAt {
                        Text(played.formatted(.relative(presentation: .named)).capitalizedFirst)
                    }
                }
                .font(.system(size: 12.5))
                .foregroundStyle(HomePalette.secondary)
                .lineLimit(1)
                Spacer(minLength: 8)
                Button(since == nil ? "Launch" : "Show") { model.open(app.packageName) }
                    .buttonStyle(AmberCapsule())
            }
            Spacer(minLength: 0)
            if since != nil {
                Button("Stop", systemImage: "stop.fill") { model.stop(app.packageName) }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.plain)
                    .foregroundStyle(HomePalette.secondary)
                    .help("Stop \(app.displayName)")
            }
        }
        .padding(18)
        .frame(height: 168)
        .background(HomePalette.card.opacity(0.92), in: .rect(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(HomePalette.cardBorder))
        .contentShape(.rect(cornerRadius: 20))
        .contextMenu { HomeActions(model: model, app: app) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Continue \(app.displayName)")
    }
}

struct AmberCapsule: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12.5, weight: .semibold))
            .padding(.vertical, 6)
            .padding(.horizontal, 14)
            .foregroundStyle(HomePalette.onAmber)
            .background(HomePalette.amber.opacity(configuration.isPressed ? 0.8 : 1), in: .capsule)
            .contentShape(.capsule)
    }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}

// MARK: Grid

struct HomeGrid: View {
    @Environment(LauncherModel.self) private var model
    @Binding var openFolder: UUID?

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96, maximum: 124), spacing: 12)], spacing: 26) {
            ForEach(model.home.items) { item in
                HomeCell(item: item) { folder in
                    withAnimation(.smooth(duration: 0.25)) { openFolder = folder }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// One grid place: an app or a folder. Tap opens, right-click or long-press
/// shows the actions, drag reorders or (onto an app's centre) makes a folder.
struct HomeCell: View {
    @Environment(LauncherModel.self) private var model
    let item: HomeItem
    let openFolder: (UUID) -> Void
    @State private var zone: HomeDropZone?
    @State private var showsMenu = false

    static let width: CGFloat = 96

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                icon
                    .scaleEffect(zone == .combine ? 1.08 : 1)
                if zone == .combine {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .strokeBorder(HomePalette.amber, lineWidth: 2)
                        .frame(width: 80, height: 80)
                }
            }
            .frame(width: 80, height: 80)
            Text(title)
                .font(.system(size: 12.5, weight: .medium))
                .lineLimit(1)
                .shadow(color: .black.opacity(0.6), radius: 3, y: 1)
            Circle()
                .fill(running ? HomePalette.amber : .clear)
                .frame(width: 5, height: 5)
                .padding(.top, -4)
                .accessibilityLabel(running ? "Running" : "")
        }
        .frame(width: Self.width)
        .overlay(alignment: zone == .after ? .trailing : .leading) {
            if zone == .before || zone == .after {
                Capsule().fill(HomePalette.amber).frame(width: 3, height: 64).offset(x: zone == .after ? 7 : -7, y: -14)
            }
        }
        .contentShape(.rect)
        .onTapGesture(perform: activate)
        .onLongPressGesture(minimumDuration: 0.5) { showsMenu = true }
        .popover(isPresented: $showsMenu, arrowEdge: .bottom) { menu }
        .contextMenu { contextMenu }
        .onDrag {
            model.homeDragging = item.id
            return NSItemProvider(object: item.id as NSString)
        } preview: {
            icon
        }
        .onDrop(of: [.text], delegate: HomeDrop(target: item.id, model: model, zone: $zone))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(running ? "\(title), running" : title)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default, activate)
        .accessibilityIdentifier("home-\(item.id)")
    }

    private var title: String {
        switch item {
        case .app(let package): model.entry(package)?.displayName ?? package
        case .folder(let folder): folder.name
        }
    }

    private var running: Bool { item.packages.contains { model.sessions[$0]?.isRunning == true } }

    @ViewBuilder private var icon: some View {
        switch item {
        case .app(let package):
            if let app = model.entry(package) { HomeIcon(app: app, size: 68) }
        case .folder(let folder):
            FolderIcon(folder: folder, size: 68)
        }
    }

    private func activate() {
        switch item {
        case .app(let package): model.open(package)
        case .folder(let folder): openFolder(folder.id)
        }
    }

    @ViewBuilder private var contextMenu: some View {
        switch item {
        case .app(let package):
            if let app = model.entry(package) { HomeActions(model: model, app: app) }
        case .folder(let folder):
            Button("Open Folder", systemImage: "folder") { openFolder(folder.id) }
        }
    }

    @ViewBuilder private var menu: some View {
        switch item {
        case .app(let package):
            if let app = model.entry(package) { HomeMenu(app: app) { showsMenu = false } }
        case .folder(let folder):
            HomeMenuList {
                HomeMenuRow(title: "Open folder", primary: true) { showsMenu = false; openFolder(folder.id) }
            }
        }
    }
}

/// Drop handling for one grid place. The view only chooses the zone from
/// the pointer; HomeLayout does the arranging.
struct HomeDrop: DropDelegate {
    let target: String
    let model: LauncherModel
    @Binding var zone: HomeDropZone?

    private var dragged: String? {
        guard let id = model.homeDragging, id != target, id.hasPrefix("app:") || id.hasPrefix("folder:") else { return nil }
        return id
    }

    func validateDrop(info: DropInfo) -> Bool { dragged != nil }

    func dropEntered(info: DropInfo) { zone = zone(at: info.location) }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        guard dragged != nil else { return DropProposal(operation: .forbidden) }
        zone = zone(at: info.location)
        return DropProposal(operation: .move)
    }

    func dropExited(info: DropInfo) { zone = nil }

    func performDrop(info: DropInfo) -> Bool {
        defer {
            zone = nil
            model.homeDragging = nil
        }
        guard let dragged else { return false }
        model.dropOnHome(dragged, onto: target, zone: zone(at: info.location))
        return true
    }

    /// Edges reorder; the centre of an app makes a folder. Folders only reorder.
    private func zone(at location: CGPoint) -> HomeDropZone {
        let x = location.x / HomeCell.width
        if x < 0.28 { return .before }
        if x > 0.72 { return .after }
        if dragged?.hasPrefix("app:") == true { return .combine }
        return x < 0.5 ? .before : .after
    }
}

// MARK: Icons

/// The launcher icon in one uniform squircle. Adaptive icons arrive masked;
/// legacy bitmaps sit inset on a white plate, as Android shows them.
struct HomeIcon: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    var size: CGFloat = 68

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.29, style: .continuous)
        Group {
            if let image = model.icons[app.packageName] {
                if model.adaptiveIcons.contains(app.packageName) {
                    Image(decorative: image, scale: 1).resizable().interpolation(.high)
                } else {
                    ZStack {
                        Color.white
                        Image(decorative: image, scale: 1).resizable().interpolation(.high)
                            .aspectRatio(contentMode: .fit)
                            .padding(size * 0.12)
                    }
                }
            } else {
                AppIconView(app: app, image: nil, size: size)
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .shadow(color: .black.opacity(0.35), radius: 7, y: 6)
    }
}

/// Up to four member icons on a frosted plate.
struct FolderIcon: View {
    @Environment(LauncherModel.self) private var model
    let folder: HomeFolder
    var size: CGFloat = 68

    var body: some View {
        let inner = (size - 18 - 5) / 2
        LazyVGrid(columns: [GridItem(.fixed(inner), spacing: 5), GridItem(.fixed(inner), spacing: 5)], spacing: 5) {
            ForEach(folder.apps.prefix(4), id: \.self) { package in
                if let app = model.entry(package) {
                    HomeIcon(app: app, size: inner).shadow(radius: 0)
                }
            }
        }
        .padding(9)
        .frame(width: size, height: size, alignment: .topLeading)
        .background(.white.opacity(0.14), in: .rect(cornerRadius: size * 0.29, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: size * 0.29, style: .continuous).strokeBorder(.white.opacity(0.16)))
    }
}

// MARK: Menus

/// Right-click actions for a home icon (system menu).
struct HomeActions: View {
    let model: LauncherModel
    let app: AppEntry

    var body: some View {
        let package = app.packageName
        let running = model.sessions[package]?.isRunning == true
        if running {
            Button("Show", systemImage: "macwindow") { model.showRunning(package) }
            Button("Stop", systemImage: "stop.fill") { model.stop(package) }
            Button("Restart", systemImage: "arrow.clockwise") { model.restart(package) }
        } else {
            Button("Launch", systemImage: "play.fill") { model.launch(package) }
        }
        Button("App Info", systemImage: "info.circle") { model.show(package) }
        Button("Open Console", systemImage: "terminal") { model.showConsole(package) }
        Button(app.isFavorite ? "Remove from Dock" : "Add to Dock", systemImage: app.isFavorite ? "minus.circle" : "plus.circle") {
            model.toggleDock(package)
        }
        if model.home.folder(containing: package) != nil {
            Button("Remove from Folder", systemImage: "folder.badge.minus") {
                withAnimation { model.updateHome { $0.removeFromFolder(package) } }
            }
        }
        Divider()
        Button("Uninstall…", systemImage: "trash", role: .destructive) { model.requestRemoval(package) }
    }
}

/// The long-press menu, drawn after the approved design.
struct HomeMenu: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    let dismiss: () -> Void

    var body: some View {
        let package = app.packageName
        let running = model.sessions[package]?.isRunning == true
        HomeMenuList {
            HomeMenuRow(title: running ? "Show" : "Launch", primary: true) { dismiss(); model.open(package) }
            if running { HomeMenuRow(title: "Stop") { dismiss(); model.stop(package) } }
            HomeMenuRow(title: "App info") { dismiss(); model.show(package) }
            HomeMenuRow(title: "Open console") { dismiss(); model.showConsole(package) }
            HomeMenuRow(title: app.isFavorite ? "Remove from dock" : "Add to dock") { dismiss(); model.toggleDock(package) }
            if model.home.folder(containing: package) != nil {
                HomeMenuRow(title: "Remove from folder") {
                    dismiss()
                    withAnimation { model.updateHome { $0.removeFromFolder(package) } }
                }
            }
            HomeMenuRow(title: "Uninstall", destructive: true) { dismiss(); model.requestRemoval(package) }
        }
    }
}

struct HomeMenuList<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 2) { content }
            .padding(6)
            .frame(width: 208)
            .background(HomePalette.menu)
            .environment(\.colorScheme, .dark)
    }
}

struct HomeMenuRow: View {
    let title: String
    var primary = false
    var destructive = false
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: primary ? .semibold : .regular))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 9)
                .padding(.horizontal, 12)
                .foregroundStyle(primary ? HomePalette.onAmber : destructive ? HomePalette.danger : HomePalette.text)
                .background(background, in: .rect(cornerRadius: 10, style: .continuous))
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }

    private var background: Color {
        if primary { return HomePalette.amber.opacity(hovering ? 0.85 : 1) }
        return hovering ? .white.opacity(0.08) : .clear
    }
}

// MARK: Folder

/// An opened folder over the home screen: rename, open, reorder by drag.
struct FolderOverlay: View {
    @Environment(LauncherModel.self) private var model
    let folder: HomeFolder
    let close: () -> Void
    @State private var name = ""
    @State private var dragging: String?

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: commitAndClose)
            VStack(spacing: 18) {
                TextField("Folder name", text: $name)
                    .textFieldStyle(.plain)
                    .font(.system(size: 20, weight: .semibold))
                    .multilineTextAlignment(.center)
                    .onSubmit(commit)
                    .accessibilityIdentifier("folder-name")
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(HomeCell.width), spacing: 10), count: min(4, max(2, folder.apps.count))),
                          spacing: 22) {
                    ForEach(folder.apps, id: \.self) { package in
                        if let app = model.entry(package) {
                            FolderMember(app: app, folder: folder.id, dragging: $dragging, close: commitAndClose)
                        }
                    }
                }
            }
            .padding(26)
            .background(HomePalette.menu.opacity(0.96), in: .rect(cornerRadius: 28, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(HomePalette.menuBorder))
            .shadow(color: .black.opacity(0.5), radius: 30, y: 16)
            .frame(maxWidth: 480)
            .padding(30)
        }
        .onAppear { name = folder.name }
        .onExitCommand(perform: commitAndClose)
    }

    private func commit() {
        model.updateHome { $0.rename(folder: folder.id, to: name) }
    }

    private func commitAndClose() {
        commit()
        close()
    }
}

struct FolderMember: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    let folder: UUID
    @Binding var dragging: String?
    let close: () -> Void
    @State private var target = false

    var body: some View {
        let running = model.sessions[app.packageName]?.isRunning == true
        VStack(spacing: 8) {
            HomeIcon(app: app, size: 64)
            Text(app.displayName).font(.system(size: 12.5, weight: .medium)).lineLimit(1)
            Circle().fill(running ? HomePalette.amber : .clear).frame(width: 5, height: 5).padding(.top, -4)
        }
        .frame(width: HomeCell.width)
        .overlay(alignment: .leading) {
            if target { Capsule().fill(HomePalette.amber).frame(width: 3, height: 60).offset(x: -6, y: -12) }
        }
        .contentShape(.rect)
        .onTapGesture { close(); model.open(app.packageName) }
        .contextMenu { HomeActions(model: model, app: app) }
        .onDrag {
            dragging = app.packageName
            return NSItemProvider(object: "folderapp:\(app.packageName)" as NSString)
        }
        .onDrop(of: [.text], isTargeted: $target) { _ in
            guard let moving = dragging, moving != app.packageName else { return false }
            withAnimation { model.updateHome { $0.moveInFolder(folder, moving, before: app.packageName) } }
            dragging = nil
            return true
        }
        .accessibilityLabel(app.displayName)
    }
}

// MARK: Dock

/// Favorites in the user's order, then Add APK. Drop an icon here to dock it.
struct DockBar: View {
    @Environment(LauncherModel.self) private var model
    @State private var targeted = false

    var body: some View {
        HStack(spacing: 18) {
            ForEach(model.dockApps) { DockIcon(app: $0) }
            if !model.dockApps.isEmpty {
                Rectangle().fill(.white.opacity(0.18)).frame(width: 1, height: 44)
            }
            Button { model.showImporter = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 20, weight: .medium))
                    .frame(width: 60, height: 60)
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(.white.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
                    .contentShape(.rect(cornerRadius: 18))
            }
            .buttonStyle(.plain)
            .help("Add APK")
            .accessibilityLabel("Add APK")
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 20)
        .background(.ultraThinMaterial.opacity(0.6), in: .rect(cornerRadius: 28, style: .continuous))
        .background(.white.opacity(targeted ? 0.16 : 0.08), in: .rect(cornerRadius: 28, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous)
            .strokeBorder(targeted ? HomePalette.amber : .white.opacity(0.14)))
        .onDrop(of: [.text], delegate: DockDrop(target: nil, model: model, targeted: $targeted))
        .environment(\.colorScheme, .dark)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Dock")
        .accessibilityIdentifier("dock")
    }
}

struct DockIcon: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    @State private var targeted = false

    var body: some View {
        let running = model.sessions[app.packageName]?.isRunning == true
        VStack(spacing: 3) {
            HomeIcon(app: app, size: 60)
            Circle().fill(running ? HomePalette.amber : .clear).frame(width: 5, height: 5)
        }
        .padding(.bottom, -8)
        .overlay(alignment: .leading) {
            if targeted { Capsule().fill(HomePalette.amber).frame(width: 3, height: 52).offset(x: -10, y: -4) }
        }
        .contentShape(.rect)
        .onTapGesture { model.open(app.packageName) }
        .contextMenu { HomeActions(model: model, app: app) }
        .onDrag {
            model.homeDragging = "dock:\(app.packageName)"
            return NSItemProvider(object: "dock:\(app.packageName)" as NSString)
        } preview: {
            HomeIcon(app: app, size: 60)
        }
        .onDrop(of: [.text], delegate: DockDrop(target: app.packageName, model: model, targeted: $targeted))
        .help(app.displayName)
        .accessibilityLabel(running ? "\(app.displayName), running" : app.displayName)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default) { model.open(app.packageName) }
        .accessibilityIdentifier("dock-\(app.packageName)")
    }
}

/// Dock drops: reorder dock icons, or dock a grid app before `target`
/// (nil: at the end).
struct DockDrop: DropDelegate {
    let target: String?
    let model: LauncherModel
    @Binding var targeted: Bool

    private var package: String? {
        guard let id = model.homeDragging else { return nil }
        if id.hasPrefix("dock:") { return String(id.dropFirst(5)) }
        if id.hasPrefix("app:") { return String(id.dropFirst(4)) }
        return nil
    }

    func validateDrop(info: DropInfo) -> Bool { package != nil && package != target }
    func dropEntered(info: DropInfo) { targeted = true }
    func dropExited(info: DropInfo) { targeted = false }
    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: package == nil ? .forbidden : .move) }

    func performDrop(info: DropInfo) -> Bool {
        defer {
            targeted = false
            model.homeDragging = nil
        }
        guard let package, let app = model.entry(package) else { return false }
        if !app.isFavorite {
            model.toggleDock(package)
            guard model.entry(package)?.isFavorite == true else { return false }
        }
        withAnimation { model.updateHome { $0.moveInDock(package, before: target) } }
        return true
    }
}

// MARK: Search

/// Library apps matching the query (folders flattened), then F-Droid apps
/// from the cached catalog that are not in the library yet.
struct HomeSearchResults: View {
    @Environment(LauncherModel.self) private var model
    @Environment(CatalogModel.self) private var catalog
    let query: String

    var body: some View {
        let matches = model.home.matching(query) { model.entry($0)?.displayName ?? $0 }.compactMap(model.entry)
        let remote = catalog.searchResults(query)
        VStack(alignment: .leading, spacing: 22) {
            Text("Apps").font(.system(size: 13, weight: .semibold)).foregroundStyle(HomePalette.secondary)
            if matches.isEmpty {
                Text("No apps in your library match “\(query)”.").foregroundStyle(HomePalette.secondary)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96, maximum: 124), spacing: 12)], spacing: 26) {
                    ForEach(matches) { app in
                        HomeCell(item: .app(app.packageName)) { _ in }
                    }
                }
            }
            if !remote.isEmpty {
                Text("F-Droid").font(.system(size: 13, weight: .semibold)).foregroundStyle(HomePalette.secondary)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 260, maximum: 400), spacing: 12)], spacing: 12) {
                    ForEach(remote) { app in
                        Button { catalog.showDetails(app) } label: {
                            HStack(spacing: 12) {
                                CatalogIcon(app: app, size: 40)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(app.name).font(.system(size: 13.5, weight: .semibold)).lineLimit(1)
                                    Text(app.summary).font(.system(size: 12)).foregroundStyle(HomePalette.secondary).lineLimit(1)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(12)
                            .background(HomePalette.card.opacity(0.92), in: .rect(cornerRadius: 14, style: .continuous))
                            .contentShape(.rect(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                }
            } else if catalog.catalog == nil {
                Text("Open the F-Droid Catalog once to search it here.")
                    .font(.system(size: 12.5)).foregroundStyle(HomePalette.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
