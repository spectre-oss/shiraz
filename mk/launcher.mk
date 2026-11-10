# SPDX-License-Identifier: GPL-3.0-only
# SwiftUI launcher for macOS (macos/Launcher). Build output stays under build/.
.PHONY: launcher-test launcher-app launcher-run macos-icons
# Ghost app icon for the launcher and for running APK host bundles.
macos-icons:
	macos/icons/make_icons.sh build/icons

launcher-test:
	swift test --package-path macos/Launcher --scratch-path build/launcher/swiftpm-test

launcher-app:
	macos/Launcher/bundle.sh build/launcher

launcher-run: launcher-app
	open "build/launcher/Shiraz Launcher.app"
