# Abra-Ca Bubble — Godot

## Artifact and environment

- Upstream: [Global Game Jam project](https://globalgamejam.org/games/2025/abra-ca-bubble-0)
  and [published release](https://github.com/SlugFiller/ggj2025/releases/tag/2025-01-31).
- Unchanged artifact: `GGJ2025.apk`, version 1.0.0, version code 1,
  min SDK 21, target SDK 34.
- SHA-256: `1af0c0e8b15fb9ec608119aff9c4612899410532b3b12382eb0458e07ed6e5ee`.
- Manifest identifies Godot 4.3.0.stable. Native libraries are
  `libgodot_android.so` and `libc++_shared.so`, ARM64 only.
- Tested on 2026-10-28 in the separate x86-64 Linux VM, using the optional
  Digitalis/Berberis native bridge in interpreter mode. Source baseline
  `4ac6452` plus the compatibility changes described below.

## Reproduce, fix, retest

1. The launcher activity enters AndroidX splash-screen setup, then fails because
   `Resources.Theme.getDrawable(int)` is missing. This happens before native
   Godot engine startup, so it does not yet test ARM64 execution or rendering.
2. Added themed drawable lookup through the existing resource loader, preserving
   the selected theme and existing missing-resource errors. Retesting passes
   that call and reaches post-splash theme application.
3. The app's main theme inherits Android's `Theme.Black.NoTitleBar`, which was
   absent from the selected framework resource set. Added that real AOSP style
   and its dependency closure while preserving public resource IDs. The VM
   resource build passes. Retesting passes splash setup and theme application.
4. Godot's filesystem directory access requires the storage-manager service.
   Shiraz currently returns null, causing a Kotlin null-cast failure while
   constructing the engine wrapper. Storage-volume support is the next issue.

**Startup blocked; no game scene or gameplay verified.** Further acceptance
requires engine loading, rendering, and an actual puzzle interaction. No APK
modification or synthetic replacement scene is used.
