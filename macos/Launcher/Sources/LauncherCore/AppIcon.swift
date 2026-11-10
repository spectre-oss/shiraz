// SPDX-License-Identifier: GPL-3.0-only
import CoreGraphics
import Foundation
import ImageIO

/// The launcher icon `shiraz icon` extracted from an APK:
///
///     icon/icon.json            kind plus layer file names (see src/shiraz/app_icon.py)
///     icon/icon.png|webp|jpg    a legacy raster icon, or
///     icon/foreground.*, icon/background.*   adaptive-icon layers
///     icon/error.txt            why extraction failed (suppresses retries)
public struct AppIcon: Decodable, Equatable, Sendable {
    public enum Kind: String, Decodable, Sendable { case bitmap, adaptive }

    public var kind: Kind
    public var image: String?
    public var foreground: String?
    public var background: String?
    public var foregroundColor: String?
    public var backgroundColor: String?

    enum CodingKeys: String, CodingKey {
        case kind, image, foreground, background
        case foregroundColor = "foreground_color"
        case backgroundColor = "background_color"
    }

    public init(kind: Kind, image: String? = nil, foreground: String? = nil, background: String? = nil,
                foregroundColor: String? = nil, backgroundColor: String? = nil) {
        self.kind = kind
        self.image = image
        self.foreground = foreground
        self.background = background
        self.foregroundColor = foregroundColor
        self.backgroundColor = backgroundColor
    }

    public static let manifestName = "icon.json"
    public static let errorName = "error.txt"
    static let maxImageBytes = 8 * 1024 * 1024

    public static func load(directory: URL) throws -> AppIcon {
        let data = try Data(contentsOf: directory.appending(path: manifestName))
        guard data.count < 64 * 1024 else { throw CocoaError(.fileReadTooLarge) }
        return try JSONDecoder().decode(AppIcon.self, from: data)
    }

    /// `#AARRGGBB` as written by the extractor.
    public static func color(_ text: String?) -> CGColor? {
        guard let text, text.count == 9, text.first == "#", let value = UInt32(text.dropFirst(), radix: 16) else { return nil }
        func channel(_ shift: UInt32) -> CGFloat { CGFloat((value >> shift) & 0xFF) / 255 }
        return CGColor(srgbRed: channel(16), green: channel(8), blue: channel(0), alpha: channel(24))
    }

    /// Decode one layer file. Names come from icon.json, so only plain file
    /// names inside `directory` are accepted.
    static func image(named name: String?, in directory: URL) -> CGImage? {
        guard let name, !name.isEmpty, !name.contains("/"), !name.hasPrefix(".") else { return nil }
        let url = directory.appending(path: name)
        guard let size = try? url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey]),
              size.isRegularFile == true, (size.fileSize ?? .max) <= maxImageBytes,
              let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    /// Render to a square bitmap. Legacy icons are drawn as shipped (they
    /// already carry their own shape). Adaptive icons follow Android's
    /// geometry: 108 dp layers, the centre 72 dp visible, masked here to a
    /// rounded square.
    public func render(directory: URL, pixels: Int = 256) -> CGImage? {
        let side = CGFloat(pixels)
        guard let context = CGContext(
            data: nil, width: pixels, height: pixels, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        context.interpolationQuality = .high
        let canvas = CGRect(x: 0, y: 0, width: side, height: side)
        switch kind {
        case .bitmap:
            guard let bitmap = Self.image(named: image, in: directory) else { return nil }
            context.draw(bitmap, in: Self.aspectFit(bitmap, in: canvas))
        case .adaptive:
            let back = Self.image(named: background, in: directory)
            let front = Self.image(named: foreground, in: directory)
            let backColor = Self.color(backgroundColor)
            let frontColor = Self.color(foregroundColor)
            guard back != nil || front != nil || backColor != nil else { return nil }
            context.addPath(CGPath(roundedRect: canvas, cornerWidth: side * 0.225, cornerHeight: side * 0.225, transform: nil))
            context.clip()
            context.setFillColor(backColor ?? CGColor(gray: 1, alpha: 1))
            context.fill(canvas)
            let layer = canvas.insetBy(dx: -side / 4, dy: -side / 4)  // 108 dp over a 72 dp window.
            if let back { context.draw(back, in: layer) }
            if let frontColor {
                context.setFillColor(frontColor)
                context.fill(canvas)
            }
            if let front { context.draw(front, in: layer) }
        }
        return context.makeImage()
    }

    static func aspectFit(_ image: CGImage, in rect: CGRect) -> CGRect {
        let scale = min(rect.width / CGFloat(image.width), rect.height / CGFloat(image.height))
        let size = CGSize(width: CGFloat(image.width) * scale, height: CGFloat(image.height) * scale)
        return CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2, width: size.width, height: size.height)
    }
}
