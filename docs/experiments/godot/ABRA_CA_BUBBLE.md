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
   Digitalis/Berberis native bridge in interpreter mode. Initial source baseline
   `4ac6452`; storage milestone `4ad4f13`, followed by the changes below.

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
5. Native engine loading initially could not locate the APK's bundled
   `libc++_shared.so`. Digitalis deliberately filters host loader variables and
   expects `BERBERIS_GUEST_LD_LIBRARY_PATH`. Shiraz now supplies that variable
   from the extracted app-library directory. The unchanged APK passes dependency
   lookup with the original global linker configuration and original translator.
6. Further loading requires host ABI proxies for OpenSL ES, Android runtime,
   Android NDK, and GLES3 functions. Built the pinned upstream interface/callback
   wrappers on the VM, with Shiraz adapters for resource-library symbol lookup,
   graphics dispatch, and ART file descriptors. The unchanged APK passes those
   library-initialization failures. A subsequent unresolved JNI-to-Android call
   is resolved by explicitly routing asset functions to Shiraz's resource library.
   This does not verify audio playback or game rendering.
7. A separate translator rebuild exposed a C++ standard-library compatibility
   issue and an executable-stack link flag. Both have build fixes; application
   acceptance of that rebuilt translator remains separate from the launcher fix.
8. The rebuilt translator then aborted on unsupported `pthread_create`. Added
   Bionic-to-glibc attribute conversion and Digitalis guest-thread creation,
   join, and detach. An ARM64 fixture passes four concurrent callbacks, distinct
   thread identities, isolated TLS, return values, key destructors, and detached
   completion. The unchanged game gets past this failure.
9. Godot now rejects the device's missing Vulkan capability before creating its
   render view. Its error-reporting path then fails because
   `android.app.AlertDialog.Builder` is missing. Vulkan support is not advertised
   falsely; graphics compatibility and the missing dialog API remain open.

## Thread regression coverage

`make guest-pthread-attributes-smoke` checks host attribute conversion, invalid
flags and stacks, scheduling selection, and preservation of guest input.
`make digitalis-thread-smoke` builds and runs the ARM64 lifecycle fixture with
standalone ART and Digitalis. Use `DIGITALIS_THREAD_OPTIONS` to supply the prepared
ART home, bridge, loader, compiler, and host library directories; see the script's
`--help`. These fixtures supplement the real APK retest above.

**Startup blocked; no game scene or gameplay verified.** Further acceptance
requires engine loading, rendering, and an actual puzzle interaction. No APK
modification or synthetic replacement scene is used.
