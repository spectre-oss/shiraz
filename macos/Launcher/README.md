# Shiraz Launcher (macOS)

A SwiftUI app for macOS 27 that keeps a library of APKs, lets you set per-app
launch options, and starts and stops the Shiraz runtime for each one. UI code
imports SwiftUI and Observation, plus CoreGraphics and ImageIO for icon bitmaps
(no UIKit). AppKit is used only where SwiftUI has no equivalent: bringing
the launcher forward and activating a running app's window.

Experimental. The macOS runtime itself is still in progress (see
`docs/macos_final_goal.md`), so an APK that imports can still fail to start.
The launcher shows the runtime's own output in each app's Console tab.

## Layout

- Sidebar: Library scopes (Home, Running, Recently Played, Favorites) with
  counts, the F-Droid catalog, search, and the runtime status badge (opens
  Settings).
- Home (`Sources/ShirazLauncher/HomeView.swift`): an Android-style home
  screen on a dark wallpaper. Top strip: clock and date, and a runtime chip
  (version, ready or not, apps running; opens Settings). A centred pill search
  filters library apps (folders included) and lists matching F-Droid apps from
  the catalog already on disk (it never downloads; open the F-Droid Catalog
  once to fill it). Continue shows up to three apps, running ones first, then
  the most recently played; there is no captured app frame yet, so the cards
  show the icon only. The grid shows every app as a uniform squircle icon with
  its name and an amber dot while it runs.
  - Click an icon to launch it. Click a running app (or Show) to bring its
    window forward (the runtime's ShirazHost app, found by
    `HostIdentity.bundleIdentifier`).
  - Right-click, or press and hold, for Launch/Show, Stop, App info, Open
    console, Add to/Remove from dock, Remove from folder and Uninstall.
  - Drag an icon onto the edge of another to reorder, onto its centre to make
    a folder (or join one). Click a folder to open it: rename it, drag to
    reorder inside, right-click to take an app out. A folder left with one app
    turns back into that app.
  - Dock: your favorites (at most six), in the order you drag them, then Add
    APK. Drop a grid icon on the dock to add it. Add to dock is the same as
    Favorite.
  - Wallpaper menu in the toolbar: Contours (default), Dusk, Plain, or Choose
    Picture… (copied into the library's `wallpaper/` directory).
  - Order, folders, dock order and wallpaper persist in `home.json` in the
    library directory (`Sources/LauncherCore/HomeLayout.swift`). Apps added or
    removed elsewhere join the end or leave the layout.
  - The toolbar switch shows the library as a sortable list instead.
- Running, Recently Played and Favorites: icon grid or sortable list (name,
  recently played, recently added, size). Running apps glow. Hover a tile for
  a Launch/Stop button; double-click launches, click opens the app page.
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
- Load Native Libraries (Experimental), shown for arm64-v8a apps and off by
  default, launches with `SHIRAZ_GUEST_LOADER=1` so the app's native libraries
  run through the guest ELF loader. Otherwise the launcher passes
  `SHIRAZ_GUEST_LOADER=0` and native calls fail with UnsatisfiedLinkError.
- Launch, Stop and Restart per app. The runtime runs in its own process group,
  so Stop also ends anything it spawned. Quitting the launcher stops running
  apps.
- APKs whose only native code is not `arm64-v8a` are refused with a reason.
- App labels and icons. `python3 -m shiraz label <apk>` resolves
  `android:label` through `resources.arsc` (default locale first).
  `python3 -m shiraz icon <apk> --output-dir <dir>` writes the
  highest-density PNG, WebP or JPEG unchanged, or the layers of an adaptive
  icon. Both run at import and once for apps imported earlier.
- Favorites, last-played time, the home layout and the chosen view and sort persist.
- The Shiraz logo (the Spectre ghost from `landing/src/lib/spectre.svg`) is the
  launcher's app icon. A running APK shows its own label and launcher icon in
  the Dock, with the ghost in a corner badge. `make macos-icons` (also run by
  `make launcher-app`) renders `build/icons/shiraz.icns` and builds
  `build/icons/shiraz-badge`, which the runtime uses to compose each APK's
  Dock icon. APKs without a supported raster icon get the plain ghost.

## F-Droid

- F-Droid Catalog (sidebar, or Browse F-Droid on the welcome screen) runs
  `python3 -m shiraz fdroid sync --cache <library>/fdroid`, which downloads
  index-v1.jar and trusts it only after checking the pinned f-droid.org
  certificate, the JAR signature (SHA-1, as f-droid.org signs v1) and digests.
  A catalog younger than six hours is reused; Refresh downloads it again.
- Filters: Fits This Mac (an APK with arm64-v8a or no native code and
  minSdk <= 27; this is not a claim that the app works), All, In Library;
  category and sort. The sidebar search field searches the catalog.
- Get / Update runs `shiraz fdroid fetch`, which keeps the APK only when its
  SHA-256 matches the signed index, then imports it like a dropped APK.
  Update compares versionCode with the library copy.
- Apps running in Shiraz can install into the library. Each launch gets
  `SHIRAZ_LAUNCHER_APPS` (the library's apps/ directory, which apps read
  through PackageManager) and a per-session request directory
  `SHIRAZ_LAUNCHER_INBOX` (`java/shiraz/runtime/LauncherInbox.java`,
  `Sources/LauncherCore/InstallInbox.swift`). The F-Droid app's
  PackageInstaller session hands its APK there; the launcher copies it,
  asks the user (the window comes forward), imports it and answers. Installs
  and updates need REQUEST_INSTALL_PACKAGES and removals
  REQUEST_DELETE_PACKAGES in the requesting app's manifest, checked by the
  launcher. Open asks the launcher to start the app without a prompt. When
  the app stops waiting, the prompt closes and nothing changes.
- Updates, from the catalog page or from an app (`Sources/LauncherCore/UpdatePolicy.swift`):
  the APK must be newer, its SHA-256 must be one the signed index lists for
  that package (the catalog is downloaded again once when it is not), and the
  installed copy's signing certificate (`shiraz signer`, read, not verified)
  must be the index's `signer` for that release. The app must not be running,
  and an app cannot update itself while it runs. Settings and app data stay.
- Each library app has `package.properties` from `shiraz metadata`, so apps
  see what else is installed; `label` is the resolved application label.
  Files from an older format (`metadataVersion`) are rewritten at startup,
  keeping the recorded installer.
- Limits: updates of apps that are not in the F-Droid catalog are refused.

## Not implemented

Controls for planned features are present but throw `NotImplementedError`
(`Sources/LauncherCore/Feature.swift`); the launcher shows a toast and
changes nothing. That list is the backlog: updates,
automatic updates, collections, Add to Applications, Export as Mac App,
duplicate, replace APK, app data (show, clear, cache, back up), permission
and network controls,
notifications, display scale, full screen, renderer choice, keyboard mapping,
game controllers, app language, keep awake, runtime update, diagnostics
report, performance stats.

Also missing:

- Apps still run in their own windows; the launcher does not host them yet,
  and Continue cards have no last-frame preview.

- Vector-drawable icons are not rendered; those apps get a monogram.
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
