// SPDX-License-Identifier: GPL-3.0-only
import AppKit
import LauncherCore
import Observation
import SwiftUI

// MARK: Sessions

/// One app shown inside the launcher window. The runtime renders into the
/// shared frame file and reads input from the live input file (EmbedPaths);
/// its own SDL window stays hidden until Detach.
@MainActor @Observable
final class EmbeddedSession {
    let package: String
    let paths: EmbedPaths
    private(set) var image: CGImage?
    private(set) var frameRevision: UInt64 = 0
    /// The app's window size in points, as last sent with `resize`.
    private(set) var windowSize: CGSize
    private(set) var detached = false
    private(set) var failure: String?
    /// nil fills the stage; true/false keep a phone shape (Rotate).
    var portrait: Bool? = true

    private var writer: LiveInputWriter?
    private var reader: FrameReader?
    private var poller: Task<Void, Never>?
    private var pendingResize: Task<Void, Never>?
    private var area: CGSize = .zero

    private let ownsFiles: Bool

    init(package: String, paths: EmbedPaths, size: CGSize, ownsFiles: Bool = true) {
        self.package = package
        self.paths = paths
        self.ownsFiles = ownsFiles
        windowSize = size
    }

    func start() {
        guard poller == nil else { return }
        do {
            writer = try LiveInputWriter(url: paths.input)
            reader = try FrameReader(url: paths.frame)
        } catch {
            failure = error.localizedDescription
            return
        }
        let reader = reader
        poller = Task { [weak self] in
            while !Task.isCancelled {
                let frame = await Task.detached(priority: .userInitiated) { reader?.next() }.value
                guard let self else { return }
                if let frame { apply(frame) }
                try? await Task.sleep(for: .milliseconds(8))
            }
        }
    }

    func end() {
        poller?.cancel()
        pendingResize?.cancel()
        poller = nil
        writer = nil
        reader = nil
        if ownsFiles { paths.remove() }
    }

    private func apply(_ frame: EmbeddedFrame) {
        frameRevision = frame.revision
        detached = frame.detached
        guard let provider = CGDataProvider(data: frame.pixels as CFData),
              let space = CGColorSpace(name: CGColorSpace.sRGB) else { return }
        let info = CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        image = CGImage(width: frame.width, height: frame.height, bitsPerComponent: 8, bitsPerPixel: 32,
                        bytesPerRow: frame.stride, space: space, bitmapInfo: info, provider: provider,
                        decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }

    func send(_ commands: [LiveCommand]) {
        guard let writer, !detached else { return }
        do {
            try writer.send(commands)
        } catch {
            failure = error.localizedDescription
        }
    }

    /// The stage area changed: relayout the app to fit it (debounced, so a
    /// live window drag does not queue a configuration change per pixel).
    func layout(in area: CGSize) {
        self.area = area
        let size = EmbedGeometry.windowSize(for: area, portrait: portrait)
        let target = CGSize(width: size.width, height: size.height)
        guard target != windowSize, !detached else { return }
        pendingResize?.cancel()
        pendingResize = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(120))
            guard !Task.isCancelled, let self else { return }
            windowSize = target
            send([.resize(size.width, size.height)])
        }
    }

    func rotate() {
        portrait = !(portrait ?? false)
        layout(in: area)
    }

    func fill() {
        portrait = portrait == nil ? true : nil
        layout(in: area)
    }

    func back() { send([.keyDown("AC Back"), .keyUp("AC Back")]) }

    func detach() {
        send([.focus(false), .embed(false)])
        detached = true
    }

    func attach() {
        detached = false
        send([.embed(true), .focus(true)])
        let current = area
        area = .zero
        if current != .zero { layout(in: current) }
    }
}

/// The embedded sessions and which one the stage shows.
@MainActor @Observable
final class EmbedHub {
    static let shared = EmbedHub()

    private(set) var sessions: [String: EmbeddedSession] = [:]
    /// The package on the stage; nil shows Home.
    var shown: String?
    /// Packages most recently shown first (Recents).
    private(set) var recents: [String] = []

    /// Files and environment for a new embedded launch of `package`.
    func prepare(_ package: String, root: URL, size: CGSize) throws -> [String: String] {
        end(package)
        let paths = try EmbedPaths.create(in: root)
        let session = EmbeddedSession(package: package, paths: paths, size: size)
        sessions[package] = session
        session.start()
        return paths.environment
    }

    /// Testing aid: `SHIRAZ_EMBED_ATTACH=<package>=<directory>[,…]` attaches
    /// the stage to runs started outside the launcher with that directory's
    /// input and frame files (`shiraz run` with SHIRAZ_LIVE_INPUT and
    /// SHIRAZ_EMBED_FRAME). Only owner-only files are opened.
    func attachFromEnvironment() {
        guard !attachedFromEnvironment else { return }
        attachedFromEnvironment = true
        guard let value = ProcessInfo.processInfo.environment["SHIRAZ_EMBED_ATTACH"] else { return }
        for item in value.split(separator: ",") {
            let parts = item.split(separator: "=", maxSplits: 1).map(String.init)
            guard parts.count == 2, LibraryStore.isValidPackageName(parts[0]), sessions[parts[0]] == nil else { continue }
            let session = EmbeddedSession(package: parts[0], paths: EmbedPaths(directory: URL(fileURLWithPath: parts[1], isDirectory: true)),
                                          size: CGSize(width: 360, height: 640), ownsFiles: false)
            sessions[parts[0]] = session
            session.start()
        }
    }

    private var attachedFromEnvironment = false

    func show(_ package: String) {
        guard sessions[package] != nil else { return }
        if let current = shown, current != package { sessions[current]?.send([.focus(false)]) }
        shown = package
        recents.removeAll { $0 == package }
        recents.insert(package, at: 0)
        sessions[package]?.send([.focus(true)])
    }

    func home() {
        if let current = shown { sessions[current]?.send([.focus(false)]) }
        shown = nil
    }

    func end(_ package: String) {
        sessions.removeValue(forKey: package)?.end()
        recents.removeAll { $0 == package }
        if shown == package { shown = nil }
    }
}

// MARK: Stage

/// Artboard E: the shown app between its neighbours, a top bar with Rotate,
/// Console and Detach, and Back / Home / Recents below.
struct EmbeddedStage: View {
    @Environment(LauncherModel.self) private var model
    let session: EmbeddedSession
    private var hub: EmbedHub { .shared }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            GeometryReader { proxy in
                HStack(spacing: 24) {
                    if wide(proxy.size), let previous = neighbour {
                        SwitchCard(package: previous)
                    }
                    surface
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if wide(proxy.size) { OpenAppCard() }
                }
                .padding(18)
            }
            navBar
        }
        .background(Color(hex: 0x0e0d14))
        .foregroundStyle(HomePalette.text)
        .environment(\.colorScheme, .dark)
    }

    private func wide(_ size: CGSize) -> Bool { size.width > 900 }

    private var neighbour: String? {
        hub.recents.first { $0 != session.package && hub.sessions[$0] != nil }
    }

    @ViewBuilder private var surface: some View {
        if session.detached {
            VStack(spacing: 14) {
                Image(systemName: "macwindow.on.rectangle").font(.system(size: 40))
                Text("Running in its own window").font(.headline)
                Button("Bring Back Here", systemImage: "rectangle.inset.filled.and.person.filled") { session.attach() }
                    .buttonStyle(.glassProminent)
                    .tint(HomePalette.amber)
            }
            .foregroundStyle(HomePalette.secondary)
        } else {
            GeometryReader { proxy in
                FrameHost(session: session)
                    .onAppear { session.layout(in: proxy.size) }
                    .onChange(of: proxy.size) { _, size in session.layout(in: size) }
                    .onChange(of: session.portrait) { _, _ in session.layout(in: proxy.size) }
            }
        }
    }

    private var topBar: some View {
        let app = model.entry(session.package)
        return HStack(spacing: 12) {
            if let app { HomeIcon(app: app, size: 26).shadow(radius: 0) }
            Text(app?.displayName ?? session.package).font(.system(size: 14, weight: .semibold))
            if let since = model.runningSince(session.package) {
                Text("Running · \(since, style: .relative)").font(.system(size: 12.5)).foregroundStyle(HomePalette.secondary)
            }
            if let failure = session.failure {
                Text(failure).font(.system(size: 12)).foregroundStyle(HomePalette.danger).lineLimit(1)
            }
            Spacer(minLength: 8)
            StageButton(title: session.portrait == false ? "Portrait" : "Landscape", symbol: "rotate.right") { session.rotate() }
                .help("Turn the app between portrait and landscape")
                .disabled(session.detached)
            StageButton(title: session.portrait == nil ? "Phone Size" : "Fill", symbol: "arrow.up.left.and.arrow.down.right") { session.fill() }
                .help("Fill the window or keep a phone shape")
                .disabled(session.detached)
            StageButton(title: "Console", symbol: "terminal") { model.showConsole(session.package); hub.home() }
            Button {
                session.detached ? session.attach() : session.detach()
            } label: {
                Label(session.detached ? "Attach" : "Detach Window", systemImage: "arrow.up.forward.square")
                    .font(.system(size: 12.5, weight: .semibold))
                    .padding(.horizontal, 14)
                    .frame(height: 32)
                    .background(HomePalette.amber, in: .rect(cornerRadius: 10))
                    .foregroundStyle(HomePalette.onAmber)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .frame(height: 52)
        .background(Color(hex: 0x1d1b29))
        .overlay(alignment: .bottom) { Color(hex: 0x2c2a3b).frame(height: 1) }
    }

    private var navBar: some View {
        HStack(spacing: 56) {
            NavButton(label: "Back", symbol: "chevron.left") { session.back() }
                .keyboardShortcut(.leftArrow, modifiers: [.command])
                .disabled(session.detached)
            NavButton(label: "Home", symbol: "circle") { hub.home() }
                .keyboardShortcut("h", modifiers: [.command, .shift])
            Menu {
                ForEach(hub.recents.filter { hub.sessions[$0] != nil }, id: \.self) { package in
                    Button(model.entry(package)?.displayName ?? package) { hub.show(package) }
                }
            } label: {
                Image(systemName: "square").font(.system(size: 17, weight: .medium)).frame(width: 44, height: 44)
            }
            .menuStyle(.button)
            .menuIndicator(.hidden)
            .buttonStyle(.plain)
            .fixedSize()
            .accessibilityLabel("Recents")
            .help("Recents")
        }
        .frame(maxWidth: .infinity)
        .frame(height: 56)
        .background(Color(hex: 0x1d1b29))
        .overlay(alignment: .top) { Color(hex: 0x2c2a3b).frame(height: 1) }
    }
}

private struct StageButton: View {
    let title: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.system(size: 12.5))
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(Color(hex: 0x2a2744), in: .rect(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

private struct NavButton: View {
    let label: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 17, weight: .medium)).frame(width: 44, height: 44).contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .help(label)
    }
}

/// Another running app: its last frame, click to switch.
private struct SwitchCard: View {
    @Environment(LauncherModel.self) private var model
    let package: String

    var body: some View {
        Button { EmbedHub.shared.show(package) } label: {
            VStack(spacing: 10) {
                if let image = EmbedHub.shared.sessions[package]?.image {
                    Image(decorative: image, scale: 1).resizable().aspectRatio(contentMode: .fit)
                        .clipShape(.rect(cornerRadius: 10))
                        .frame(maxHeight: 220)
                } else if let app = model.entry(package) {
                    HomeIcon(app: app, size: 48)
                }
                Text(model.entry(package)?.displayName ?? package).font(.system(size: 12.5)).lineLimit(1)
            }
            .padding(12)
            .frame(width: 150, height: 300)
            .background(HomePalette.wall, in: .rect(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(HomePalette.cardBorder))
            .opacity(0.8)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Switch to \(model.entry(package)?.displayName ?? package)")
    }
}

private struct OpenAppCard: View {
    var body: some View {
        Button { EmbedHub.shared.home() } label: {
            VStack(spacing: 10) {
                Image(systemName: "plus").font(.system(size: 22))
                Text("Open app").font(.system(size: 12.5))
            }
            .frame(width: 150, height: 300)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: 0x5a567a), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5])))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Open another app")
    }
}

// MARK: Frame view

/// Shows the app's frames and turns pointer and keyboard events into live
/// input commands in the app's own window coordinates.
struct FrameHost: NSViewRepresentable {
    let session: EmbeddedSession

    func makeNSView(context: Context) -> FrameView {
        let view = FrameView()
        view.session = session
        return view
    }

    func updateNSView(_ view: FrameView, context: Context) {
        view.session = session
        view.show(session.image, revision: session.frameRevision, window: session.windowSize)
    }
}

final class FrameView: NSView {
    weak var session: EmbeddedSession?
    private var window_: CGSize = .zero
    private var revision: UInt64 = 0
    private let surface = CALayer()

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.addSublayer(surface)
        surface.contentsGravity = .resizeAspect
        surface.cornerRadius = 20
        surface.masksToBounds = true
        surface.backgroundColor = NSColor(calibratedRed: 0.137, green: 0.129, blue: 0.227, alpha: 1).cgColor
        surface.borderColor = NSColor(calibratedRed: 0.27, green: 0.255, blue: 0.373, alpha: 1).cgColor
        surface.borderWidth = 1
    }

    @available(*, unavailable) required init?(coder: NSCoder) { nil }

    override var isFlipped: Bool { true }
    override var acceptsFirstResponder: Bool { true }

    func show(_ image: CGImage?, revision: UInt64, window: CGSize) {
        window_ = window
        if revision != self.revision || surface.contents == nil {
            self.revision = revision
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            surface.contents = image
            CATransaction.commit()
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        // The surface takes the app window's shape; the frame fills it once the
        // relayout at the new size arrives.
        surface.frame = EmbedGeometry.fit(content: window_, in: bounds.size)
        surface.contentsScale = window.map(\.backingScaleFactor) ?? 2
        CATransaction.commit()
    }

    private func appPoint(_ event: NSEvent) -> (x: Int, y: Int)? {
        EmbedGeometry.appPoint(convert(event.locationInWindow, from: nil), view: bounds.size, window: window_)
    }

    private var pressed = false

    override func mouseDown(with event: NSEvent) {
        window?.makeFirstResponder(self)
        guard let point = appPoint(event) else { return }
        pressed = true
        session?.send([.move(point.x, point.y), .down(point.x, point.y)])
    }

    override func mouseDragged(with event: NSEvent) {
        guard pressed else { return }
        let local = convert(event.locationInWindow, from: nil)
        let rect = EmbedGeometry.fit(content: window_, in: bounds.size)
        // Keep a drag inside the app's edge rather than dropping it.
        let clamped = CGPoint(x: min(max(local.x, rect.minX), rect.maxX - 0.5), y: min(max(local.y, rect.minY), rect.maxY - 0.5))
        guard let point = EmbedGeometry.appPoint(clamped, view: bounds.size, window: window_) else { return }
        session?.send([.move(point.x, point.y)])
    }

    override func mouseUp(with event: NSEvent) {
        guard pressed else { return }
        pressed = false
        let local = convert(event.locationInWindow, from: nil)
        let rect = EmbedGeometry.fit(content: window_, in: bounds.size)
        let clamped = CGPoint(x: min(max(local.x, rect.minX), rect.maxX - 0.5), y: min(max(local.y, rect.minY), rect.maxY - 0.5))
        guard let point = EmbedGeometry.appPoint(clamped, view: bounds.size, window: window_) else { return }
        session?.send([.move(point.x, point.y), .up(point.x, point.y)])
    }

    override func scrollWheel(with event: NSEvent) {
        guard let point = appPoint(event) else { return }
        let scale: CGFloat = event.hasPreciseScrollingDeltas ? 0.1 : 1
        session?.send([.wheel(x: point.x, y: point.y, dx: Float(-event.scrollingDeltaX * scale), dy: Float(event.scrollingDeltaY * scale))])
    }

    override func keyDown(with event: NSEvent) {
        if let name = Self.keyName(event) {
            session?.send([.keyDown(name)])
        } else if let text = event.characters, !text.isEmpty, !event.modifierFlags.contains(.command) {
            session?.send(LiveCommand.typing(text))
        } else {
            super.keyDown(with: event)
        }
    }

    override func keyUp(with event: NSEvent) {
        if let name = Self.keyName(event) { session?.send([.keyUp(name)]) }
    }

    /// SDL key names (SDL_GetKeyFromName) for keys that are not text.
    static func keyName(_ event: NSEvent) -> String? {
        switch event.keyCode {
        case 36, 76: "Return"
        case 48: "Tab"
        case 51: "Backspace"
        case 117: "Delete"
        case 53: "Escape"
        case 123: "Left"
        case 124: "Right"
        case 125: "Down"
        case 126: "Up"
        case 115: "Home"
        case 119: "End"
        case 116: "PageUp"
        case 121: "PageDown"
        default: nil
        }
    }
}
