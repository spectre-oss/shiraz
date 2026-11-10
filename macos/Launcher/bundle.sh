#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Wrap the SwiftPM executable in a minimal .app bundle (ad-hoc signed, local use).
# Usage: bundle.sh <output-dir>   ->  <output-dir>/Shiraz Launcher.app
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?usage: bundle.sh <output-dir>}
version=$(tr -d '[:space:]' < "$here/../../VERSION")
mkdir -p "$out"
out=$(cd "$out" && pwd)
swift build -c release --package-path "$here" --scratch-path "$out/swiftpm"
bin=$(swift build -c release --package-path "$here" --scratch-path "$out/swiftpm" --show-bin-path)
app="$out/Shiraz Launcher.app"
rm -rf "$app"
mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources"
cp "$bin/ShirazLauncher" "$app/Contents/MacOS/ShirazLauncher"
# The same ghost icon also marks running APKs (src/shiraz/darwin_app.py reads build/icons).
icons="$here/../../build/icons"
"$here/../icons/make_icons.sh" "$icons"
cp "$icons/shiraz.icns" "$app/Contents/Resources/Shiraz.icns"
cp "$icons/shiraz-mark.png" "$app/Contents/Resources/shiraz-mark.png"
cat > "$app/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleIdentifier</key><string>org.shiraz.launcher</string>
  <key>CFBundleName</key><string>Shiraz Launcher</string>
  <key>CFBundleDisplayName</key><string>Shiraz Launcher</string>
  <key>CFBundleExecutable</key><string>ShirazLauncher</string>
  <key>CFBundleIconFile</key><string>Shiraz</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$version</string>
  <key>CFBundleVersion</key><string>$version</string>
  <key>LSMinimumSystemVersion</key><string>27.0</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>CFBundleDocumentTypes</key><array><dict>
    <key>CFBundleTypeName</key><string>Android Package</string>
    <key>CFBundleTypeExtensions</key><array><string>apk</string></array>
    <key>CFBundleTypeRole</key><string>Viewer</string>
    <key>LSHandlerRank</key><string>Alternate</string>
  </dict></array>
</dict></plist>
PLIST
codesign --force --sign - "$app"
echo "$app"
