# Shiraz Launcher (macOS)

A SwiftUI app for macOS 27 that keeps a library of APKs, lets you set per-app
launch options, and starts and stops the Shiraz runtime for each one. UI code
imports SwiftUI and Observation, plus CoreGraphics and ImageIO for icon bitmaps
(no AppKit, no UIKit).

Experimental. The macOS runtime itself is still in progress (see
`docs/macos_final_goal.md`), so an APK that imports can still fail to start.
The launcher shows the runtime's own output in each app's Console tab.

## Layout

- Sidebar: Library scopes (All Apps, Running, Recently Played, Favorites) with
  counts, search, and the runtime status badge (opens Settings).
- Library: icon grid or sortable list (name, recently played, recently added,
  size). Running apps glow and sit in a Now Running strip. Hover a tile for a
  Launch/Stop button; double-click launches, click opens the app page.
- App page: a hero tinted from the app icon's colours, then Overview,
  Settings, Permissions, Storage and Console sections. The Console colours
  lines by severity (logcat priority, Shiraz markers, Python errors), filters,
  follows, exports and clears.
- Menu bar extra and an App menu: Launch/Stop (⌘R), Restart (⇧⌘R),
  Favorite (⌘D), Stop All (⌥⌘.), Remove (⌘⌫, with confirmation).

## What works

- Drop an APK anywhere in the window, or use File > Add APK. The file is
  checked (regular file, size limit, ZIP magic), copied unchanged into
  `~/Library/Application Support/Shiraz Launcher/apps/<package>/app.apk`, then
  inspected by `python3 -m shiraz inspect`. The original is never modified.
  `SHIRAZ_LAUNCHER_LIBRARY` points the launcher at another library directory.
- Per-app settings that map to `shiraz run` flags: device size presets or a
  custom size, orientation, log level, optional time limit.
- Launch, Stop and Restart per app. The runtime runs in its own process group,
  so Stop also ends anything it spawned. Quitting the launcher stops running
  apps.
- APKs whose only native code is not `arm64-v8a` are refused with a reason.
- App labels and icons. `python3 -m shiraz label <apk>` resolves
  `android:label` through `resources.arsc` (default locale first).
  `python3 -m shiraz icon <apk> --output-dir <dir>` writes the
  highest-density PNG, WebP or JPEG unchanged, or the layers of an adaptive
  icon. Both run at import and once for apps imported earlier.
- Favorites, last-played time and the chosen view and sort persist.
- The Shiraz logo (the Spectre ghost from `landing/src/lib/spectre.svg`) is the
  launcher's app icon and the Dock icon of running APKs. `make macos-icons`
  (also run by `make launcher-app`) renders `build/icons/shiraz.icns`; the
  runtime adds it to each APK's host bundle when present.

## Not implemented

Controls for planned features are present but throw `NotImplementedError`
(`Sources/LauncherCore/Feature.swift`); the launcher shows a toast and
changes nothing. That list is the backlog: F-Droid catalog, updates,
automatic updates, collections, Add to Applications, Export as Mac App,
duplicate, replace APK, app data (show, clear, cache, back up), permission
and network controls,
notifications, display scale, full screen, renderer choice, keyboard mapping,
game controllers, app language, keep awake, runtime update, diagnostics
report, performance stats, copying console output.

Also missing:

- Vector-drawable icons are not rendered; those apps get a monogram.
- Running APKs show the Shiraz ghost in the Dock, not their own icon.
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
