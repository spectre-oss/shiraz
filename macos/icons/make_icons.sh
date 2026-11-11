#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Generate the Shiraz app icon (.icns) and in-app ghost mark from the in-repo
# Spectre logo. Outputs are build products: never commit them.
# Usage: make_icons.sh <output-dir>  ->  shiraz.icns, shiraz-mark.png, shiraz-badge
set -eu
here=$(cd "$(dirname "$0")" && pwd)
svg="$here/../../landing/src/lib/spectre.svg"
out=${1:?usage: make_icons.sh <output-dir>}
mkdir -p "$out"
out=$(cd "$out" && pwd)
# Per-APK Dock icon composer used by the runtime (app icon + ghost badge).
if [ ! -x "$out/shiraz-badge" ] || [ "$here/badge_icon.swift" -nt "$out/shiraz-badge" ]; then
    swiftc -O "$here/badge_icon.swift" -o "$out/shiraz-badge.tmp"
    mv -f "$out/shiraz-badge.tmp" "$out/shiraz-badge"
fi
if [ -f "$out/shiraz.icns" ] && [ -f "$out/shiraz-mark.png" ] \
    && [ "$out/shiraz.icns" -nt "$svg" ] && [ "$out/shiraz.icns" -nt "$here/render_icon.swift" ]; then
    exit 0
fi
work="$out/shiraz.iconset"
rm -rf "$work"
mkdir -p "$work"
master="$out/.icon-1024.png"
swift "$here/render_icon.swift" "$svg" "$master" 1024 tile
for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$master" --out "$work/icon_${size}x${size}.png" >/dev/null
    double=$((size * 2))
    sips -z "$double" "$double" "$master" --out "$work/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$work" -o "$out/shiraz.icns"
swift "$here/render_icon.swift" "$svg" "$out/shiraz-mark.png" 256 mark
rm -rf "$work" "$master"
