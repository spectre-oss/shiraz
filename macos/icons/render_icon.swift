// SPDX-License-Identifier: GPL-3.0-only
// Build-time tool: rasterise the Spectre ghost (landing/src/lib/spectre.svg)
// onto a macOS app-icon tile. Usage: render_icon.swift <svg> <out.png> <size> [tile|mark]
// "tile" draws the rounded-square app icon with the standard margin;
// "mark" draws only the white ghost on transparency for in-app use.
import AppKit

let arguments = CommandLine.arguments
guard arguments.count >= 4, let size = Int(arguments[3]), (16...2048).contains(size) else {
    FileHandle.standardError.write(Data("usage: render_icon.swift <svg> <out.png> <size> [tile|mark]\n".utf8))
    exit(2)
}
let style = arguments.count > 4 ? arguments[4] : "tile"
var svg = try String(contentsOfFile: arguments[1], encoding: .utf8)
// NSImage's SVG reader rejects a leading comment, so start at the <svg> element.
if let start = svg.range(of: "<svg") { svg = String(svg[start.lowerBound...]) }
guard let ghost = NSImage(data: Data(svg.utf8)) else {
    FileHandle.standardError.write(Data("cannot read SVG \(arguments[1])\n".utf8))
    exit(1)
}
guard let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4,
    hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)
else { exit(1) }
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSGraphicsContext.current?.imageInterpolation = .high
let canvas = CGFloat(size)
if style == "tile" {
    // Apple's 1024 grid: 824 pt body, 100 pt margin, ~185 pt corner radius.
    let inset = canvas * 100 / 1024
    let body = NSRect(x: inset, y: inset, width: canvas - 2 * inset, height: canvas - 2 * inset)
    let radius = canvas * 185.4 / 1024
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.35)
    shadow.shadowBlurRadius = canvas * 10 / 1024
    shadow.shadowOffset = NSSize(width: 0, height: -canvas * 6 / 1024)
    NSGraphicsContext.saveGraphicsState()
    shadow.set()
    let tile = NSBezierPath(roundedRect: body, xRadius: radius, yRadius: radius)
    NSGradient(starting: NSColor(white: 0.16, alpha: 1), ending: NSColor(white: 0.02, alpha: 1))?.draw(in: tile, angle: -90)
    NSGraphicsContext.restoreGraphicsState()
    // The ghost spans about 62% of its own 512 canvas, so drawing that canvas
    // over the body leaves the usual optical margin inside the tile.
    ghost.draw(in: body.offsetBy(dx: 0, dy: -body.height * 0.01))
} else {
    ghost.draw(in: NSRect(x: 0, y: 0, width: canvas, height: canvas).insetBy(dx: -canvas * 0.15, dy: -canvas * 0.15))
}
NSGraphicsContext.restoreGraphicsState()
guard let png = bitmap.representation(using: .png, properties: [:]) else { exit(1) }
try png.write(to: URL(fileURLWithPath: arguments[2]))
