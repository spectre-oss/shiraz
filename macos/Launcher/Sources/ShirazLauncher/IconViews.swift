// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import ImageIO
import LauncherCore
import SwiftUI

extension LauncherModel {
    /// The app's icon tint, or the system accent when it has no icon.
    func accent(_ package: String) -> Color {
        accents[package].map { Color(cgColor: $0) } ?? .accentColor
    }
}

/// An app's extracted launcher icon, or a tinted monogram when it has none.
/// Takes the rendered icon as a value rather than reading the model from the
/// environment: table and menu cells are hosted by AppKit and can be updated
/// while detached from the window's environment.
struct AppIconView: View {
    let app: AppEntry
    let image: CGImage?
    var size: CGFloat = 32

    var body: some View {
        Group {
            if let image {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .interpolation(.high)
            } else {
                RoundedRectangle(cornerRadius: size * 0.225, style: .continuous)
                    .fill(monogramColor.gradient)
                    .overlay {
                        Text(String(app.displayName.prefix(1)).uppercased())
                            .font(.system(size: size * 0.46, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                    }
            }
        }
        .frame(width: size, height: size)
    }

    /// Stable per-package colour so monograms are distinguishable.
    private var monogramColor: Color {
        let palette: [Color] = [.indigo, .teal, .orange, .pink, .mint, .purple, .blue, .green]
        let sum = app.packageName.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        return palette[sum % palette.count]
    }
}

/// A running app's icon breathes in its own colour.
struct RunningGlow: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let active: Bool
    let color: Color
    let radius: CGFloat

    func body(content: Content) -> some View {
        content
            .background {
                if active {
                    if reduceMotion {
                        Circle().fill(color.opacity(0.45)).blur(radius: radius * 0.6)
                    } else {
                        PhaseAnimator([0.25, 0.6]) { phase in
                            Circle()
                                .fill(color.opacity(phase))
                                .blur(radius: radius * (0.4 + phase))
                                .scaleEffect(0.9 + phase * 0.25)
                        } animation: { _ in .easeInOut(duration: 1.6) }
                    }
                }
            }
    }
}

extension View {
    func runningGlow(_ active: Bool, color: Color, radius: CGFloat = 24) -> some View {
        modifier(RunningGlow(active: active, color: color, radius: radius))
    }
}

/// The Spectre ghost, the Shiraz logo. The bundle script copies
/// shiraz-mark.png (white on transparency) into Resources; it is drawn as a
/// template so it follows the current foreground style. Under `swift run`
/// there is no bundle resource, so an SF Symbol stands in.
struct ShirazMark: View {
    private static let image: CGImage? = {
        guard let url = Bundle.main.url(forResource: "shiraz-mark", withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }()

    var body: some View {
        if let image = Self.image {
            Image(decorative: image, scale: 1)
                .renderingMode(.template)
                .resizable()
                .interpolation(.high)
                .aspectRatio(contentMode: .fit)
        } else {
            Image(systemName: "app.dashed").resizable().aspectRatio(contentMode: .fit)
        }
    }
}

/// The ghost hovering in place, for empty states.
struct FloatingGhost: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var size: CGFloat = 120

    var body: some View {
        if reduceMotion {
            ghost(lift: 0)
        } else {
            PhaseAnimator([0.0, 1.0]) { phase in
                ghost(lift: phase)
            } animation: { _ in .easeInOut(duration: 2.2) }
        }
    }

    private func ghost(lift: Double) -> some View {
        ZStack {
            Ellipse()
                .fill(.primary.opacity(0.10 - lift * 0.04))
                .frame(width: size * (0.62 - lift * 0.08), height: size * 0.08)
                .blur(radius: 4)
                .offset(y: size * 0.56)
            ShirazMark()
                .frame(width: size, height: size)
                .foregroundStyle(.secondary)
                .offset(y: -lift * size * 0.08)
                .rotationEffect(.degrees(lift * 4 - 2))
        }
        .accessibilityHidden(true)
    }
}
