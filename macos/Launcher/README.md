# Shiraz Launcher (macOS)

A SwiftUI app for macOS 27 that keeps a library of APKs, lets you set per-app
launch options, and starts and stops the Shiraz runtime for each one. UI code
imports SwiftUI and Observation, plus CoreGraphics and ImageIO for icon bitmaps
(no AppKit, no UIKit).

Experimental. The macOS runtime itself is still in progress (see
`docs/macos_final_goal.md`), so an APK that imports can still fail to start.
The launcher shows the runtime's own output in the Log tab.

## What works

- Drop an APK anywhere in the window, or use File > Add APK. The file is
  checked (regular file, size limit, ZIP magic), copied unchanged into
  `~/Library/Application Support/Shiraz Launcher/apps/<package>/app.apk`, then
  inspected by `python3 -m shiraz inspect`. The original is never modified.
- Per-app settings that map to `shiraz run` flags: window size and
  orientation, log level, optional time limit.
- Launch and Stop per app. The runtime runs in its own process group, so Stop
  also ends anything it spawned. Quitting the launcher stops running apps.
- APKs whose only native code is not `arm64-v8a` are refused with a reason.
- App icons. `python3 -m shiraz icon <apk> --output-dir <dir>` resolves the
  manifest icon through `resources.arsc` and writes the highest-density PNG,
  WebP or JPEG unchanged, or the layers of an adaptive icon. The launcher
  extracts it at import (and once for apps imported earlier), stores it under
  `apps/<package>/icon/`, and draws adaptive layers into a rounded square.
- The Shiraz logo (the Spectre ghost from `landing/src/lib/spectre.svg`) is the
  launcher's app icon and the Dock icon of running APKs. `make macos-icons`
  (also run by `make launcher-app`) renders `build/icons/shiraz.icns`; the
  runtime adds it to each APK's host bundle when present.

## What does not exist yet

- Network limits and per-app permission controls. The runtime cannot enforce
  them yet, so the UI says so instead of showing inert switches.
- App labels. The package name is shown instead.
- Vector-drawable icons are not rendered. Such apps keep a monogram and the
  Overview tab shows why.
- Running APKs show the Shiraz ghost, not their own icon.
- Embedded runtime. For now the launcher drives a source checkout through
  `python3 -m shiraz`. It finds the checkout from `SHIRAZ_ROOT`, from its own
  location, or from Settings.

## Build and test

    make launcher-test     # swift test
    make launcher-app      # build/launcher/Shiraz Launcher.app (ad-hoc signed)
    make launcher-run

The package needs Xcode with Swift 6.4 or later (`swift-tools-version:6.4`).

Layout: `Sources/LauncherCore` (models, import, process control; no UI),
`Sources/ShirazLauncher` (SwiftUI), `Tests/LauncherCoreTests`.
