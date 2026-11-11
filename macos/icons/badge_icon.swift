// SPDX-License-Identifier: GPL-3.0-only
// Dock icon for a running APK: the app's own launcher icon with the Shiraz
// ghost in a corner badge. Built by `make macos-icons` into build/icons/shiraz-badge.
// Usage: shiraz-badge <icon-dir> <shiraz-mark.png> <out.icns>
// <icon-dir> is the output of `shiraz icon` (icon.json plus unchanged images).
import AppKit
import ImageIO
import UniformTypeIdentifiers

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data("shiraz-badge: \(message)\n".utf8))
    exit(1)
}

let arguments = CommandLine.arguments
guard arguments.count == 4 else { fail("usage: shiraz-badge <icon-dir> <shiraz-mark.png> <out.icns>") }
let directory = URL(fileURLWithPath: arguments[1], isDirectory: true)
guard let record = try? JSONSerialization.jsonObject(with: Data(contentsOf: directory.appendingPathComponent("icon.json"))) as? [String: Any]
else { fail("cannot read icon.json") }
guard let mark = NSImage(contentsOfFile: arguments[2]) else { fail("cannot read \(arguments[2])") }

func image(_ key: String) -> NSImage? {
    guard let name = record[key] as? String, !name.contains("/") else { return nil }
    return NSImage(contentsOf: directory.appendingPathComponent(name))
}

func color(_ key: String) -> NSColor? {
    guard let text = record[key] as? String, text.hasPrefix("#"), let argb = UInt32(text.dropFirst(), radix: 16) else { return nil }
    return NSColor(srgbRed: CGFloat(argb >> 16 & 255) / 255, green: CGFloat(argb >> 8 & 255) / 255,
                   blue: CGFloat(argb & 255) / 255, alpha: CGFloat(argb >> 24 & 255) / 255)
}

let kind = record["kind"] as? String
let bitmap = kind == "bitmap" ? image("image") : nil
let background = image("background"), backgroundColor = color("background_color")
let foreground = image("foreground"), foregroundColor = color("foreground_color")
// Full-bleed square icons (opaque corners) get the macOS rounded tile;
// shaped icons with transparent corners are drawn as they are.
let fullBleed: Bool = {
    guard let bitmap, let cg = bitmap.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return false }
    let rep = NSBitmapImageRep(cgImage: cg)
    let w = rep.pixelsWide - 1, h = rep.pixelsHigh - 1
    return [(0, 0), (w, 0), (0, h), (w, h)].allSatisfy { (rep.colorAt(x: $0.0, y: $0.1)?.alphaComponent ?? 0) > 0.9 }
}()
guard bitmap != nil || background != nil || foreground != nil || backgroundColor != nil else { fail("icon.json has no drawable layer") }

func render(_ size: Int) -> CGImage {
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)
    else { fail("cannot allocate bitmap") }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSGraphicsContext.current?.imageInterpolation = .high
    let canvas = CGFloat(size)
    // Apple's 1024 grid: 824 pt body inside a 100 pt margin.
    let inset = canvas * 100 / 1024
    let body = NSRect(x: inset, y: inset, width: canvas - 2 * inset, height: canvas - 2 * inset)
    let radius = canvas * 185.4 / 1024
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.3)
    shadow.shadowBlurRadius = canvas * 10 / 1024
    shadow.shadowOffset = NSSize(width: 0, height: -canvas * 6 / 1024)
    if let bitmap {
        NSGraphicsContext.saveGraphicsState()
        shadow.set()
        if fullBleed {
            let tile = NSBezierPath(roundedRect: body, xRadius: radius, yRadius: radius)
            NSColor.black.setFill()
            tile.fill()
            NSGraphicsContext.restoreGraphicsState()
            NSGraphicsContext.saveGraphicsState()
            tile.addClip()
        }
        bitmap.draw(in: body)
        NSGraphicsContext.restoreGraphicsState()
    } else {
        // Adaptive icon: layers are 108 dp with a 72 dp safe zone, so the tile
        // shows the middle two thirds of a canvas 1.5x the body.
        let tile = NSBezierPath(roundedRect: body, xRadius: radius, yRadius: radius)
        NSGraphicsContext.saveGraphicsState()
        shadow.set()
        (backgroundColor ?? .white).setFill()
        tile.fill()
        NSGraphicsContext.restoreGraphicsState()
        NSGraphicsContext.saveGraphicsState()
        tile.addClip()
        let layer = body.insetBy(dx: -body.width / 6, dy: -body.height / 6)
        background?.draw(in: layer)
        if let foregroundColor { foregroundColor.setFill(); body.fill(using: .sourceOver) }
        foreground?.draw(in: layer)
        NSGraphicsContext.restoreGraphicsState()
    }
    // Shiraz badge: dark disc with the ghost, bottom-right over the body corner.
    let diameter = body.width * 0.42
    let disc = NSRect(x: body.maxX - diameter * 0.88, y: body.minY - diameter * 0.12, width: diameter, height: diameter)
    NSGraphicsContext.saveGraphicsState()
    let badgeShadow = NSShadow()
    badgeShadow.shadowColor = NSColor.black.withAlphaComponent(0.45)
    badgeShadow.shadowBlurRadius = canvas * 14 / 1024
    badgeShadow.shadowOffset = NSSize(width: 0, height: -canvas * 5 / 1024)
    badgeShadow.set()
    let circle = NSBezierPath(ovalIn: disc)
    NSGradient(starting: NSColor(white: 0.20, alpha: 1), ending: NSColor(white: 0.03, alpha: 1))?.draw(in: circle, angle: -90)
    NSGraphicsContext.restoreGraphicsState()
    NSColor.white.withAlphaComponent(0.9).setStroke()
    let ring = NSBezierPath(ovalIn: disc.insetBy(dx: canvas * 6 / 1024, dy: canvas * 6 / 1024))
    ring.lineWidth = canvas * 12 / 1024
    ring.stroke()
    mark.draw(in: disc.insetBy(dx: diameter * 0.17, dy: diameter * 0.17))
    NSGraphicsContext.restoreGraphicsState()
    guard let result = rep.cgImage else { fail("cannot render image") }
    return result
}

let output = URL(fileURLWithPath: arguments[3])
let sizes = [16, 32, 64, 128, 256, 512, 1024]
guard let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.icns.identifier as CFString, sizes.count, nil)
else { fail("cannot create \(output.path)") }
for size in sizes { CGImageDestinationAddImage(destination, render(size), nil) }
guard CGImageDestinationFinalize(destination) else { fail("cannot write \(output.path)") }
