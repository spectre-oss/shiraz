// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import ImageIO
import LauncherCore
import SwiftUI

/// An app's extracted launcher icon, or a monogram when it has none.
struct AppIconView: View {
    @Environment(LauncherModel.self) private var model
    let app: AppEntry
    var size: CGFloat = 32

    var body: some View {
        if let image = model.icons[app.packageName] {
            Image(decorative: image, scale: 1)
                .resizable()
                .interpolation(.high)
                .frame(width: size, height: size)
        } else {
            Text(String(app.displayName.prefix(1)).uppercased())
                .font(.system(size: size * 0.5, weight: .semibold))
                .frame(width: size, height: size)
                .background(.tint.opacity(0.18), in: .rect(cornerRadius: size * 0.25))
        }
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
