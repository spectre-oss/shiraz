// swift-tools-version:6.4
// SPDX-License-Identifier: GPL-3.0-only
import PackageDescription

let package = Package(
    name: "ShirazLauncher",
    platforms: [.macOS(.v27)],
    products: [
        .executable(name: "ShirazLauncher", targets: ["ShirazLauncher"]),
    ],
    targets: [
        // Models, APK import, runtime process control. No UI framework imports.
        .target(name: "LauncherCore"),
        // SwiftUI only: no AppKit, no UIKit.
        .executableTarget(name: "ShirazLauncher", dependencies: ["LauncherCore"]),
        .testTarget(name: "LauncherCoreTests", dependencies: ["LauncherCore"]),
    ]
)
