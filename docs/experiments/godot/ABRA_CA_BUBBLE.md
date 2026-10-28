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
   Returning null caused a Kotlin null-cast failure. Added enumeration of Shiraz's
   existing app-scoped primary volume, backed by actual filesystem state. The
   unchanged APK passes this step. Storage regression tests pass on both the
   host JVM and ART, including unavailable storage and symlink escape cases.
5. Native engine loading cannot locate the APK's bundled `libc++_shared.so`.
   A temporary VM-only linker search-path diagnostic passes that dependency and
   reaches OpenSL ES initialization. The translator cannot load its OpenSL ES
   proxy; adding the host library alias alone does not fix that failure.
   A per-process guest environment fix and audio proxy support remain under
   investigation. The diagnostic does not establish a working native engine.

**Startup blocked; no game scene or gameplay verified.** Further acceptance
requires engine loading, rendering, and an actual puzzle interaction. No APK
modification or synthetic replacement scene is used.
