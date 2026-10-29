# Tiny Crate — Godot 3

- Upstream: [2026.03.12 release](https://github.com/HarmonyHoney/tiny_crate/releases/tag/2026.03.12).
- Artifact: unchanged `Tiny-Crate.apk`, version 1.0, code 4, minimum SDK 19.
- SHA-256: `0990ac49597d9a056c01da74c36ae08f439891f763b54f92bd3845811fcfe468`.
- APK includes ARM64 and ARM32 libraries; tested ARM64 through the optional
  translation bridge on a separate x86-64 Linux VM with software graphics.
- Upstream describes Godot 3.6. This APK requests OpenGL ES 3.0 and starts its
  Godot main loop. Test date: 2026-10-29, baseline `334f6cd` plus the fixes below.

## Reproduce, fix, retest

1. Startup failed while applying `GodotAppSplashTheme`. Its parent is Android's
   `Theme.Black.NoTitleBar.Fullscreen`, missing from Shiraz's resource bundle.
   Added that theme to the pinned AOSP dependency closure. A regression fails
   against the old bundle and passes after rebuilding, checking inherited
   fullscreen, no-title and background attributes.
2. The unchanged APK then renders its animated title screen. Keyboard X selects
   Play and a new save slot; arrow keys navigate character setup and X accepts
   it. Tapping the visible menu labels did not activate them in this walkthrough;
   touchscreen menu support is not established.
3. Entered the first level, moved right through the ordinary keyboard input path,
   and reached the exit. Level selection then showed one earned star and a
   completion time. This is actual first-level completion, not just a menu or
   splash-screen result.
4. Resizing to portrait crashed the render thread on missing Java `EGL11`.
   Added the SDK interface, its `EGL_CONTEXT_LOST` constant, and the interface
   on the existing EGL implementation. An API regression checks class loading,
   implementation identity and the constant value.
5. After rebuilding, restart displayed the saved character and one star.
   Portrait resize no longer throws the missing-class exception, but reports
   `eglMakeCurrent failed: EGL_BAD_ACCESS` and produces a black frame. Returning
   to landscape did not establish recovery. This remaining graphics failure
   needs investigation; responsive resizing is **not verified**.
6. Tracing that failure found the GLES capability probe terminating the shared
   EGL display during resize. The probe now preserves the caller's client API,
   retains the shared display and caches its result. A regression probes with a
   live rendering context: the old implementation changes the API and destroys
   the usable display/context; the fixed implementation preserves both.
7. Further resizing exposed two independent surface-lifetime problems. Host
   geometry updates destroyed an EGL surface that was still current on the
   render thread. They now retain it while the renderer resizes its buffers.
   A Java/JNI regression checks that a surface remains valid and current across
   UI-thread geometry changes; it fails before the fix and passes afterward on
   OpenJDK and ART. An in-flight frame with obsolete dimensions also produced
   `EINVAL`, which Godot treated as a dead surface. Frame publication now checks
   dimensions atomically and discards obsolete frames with `EAGAIN`; tests cover
   changed dimensions with both equal and unequal pixel counts.
8. The unchanged APK now remains animated through 640×360 → 360×640 → 800×450
   → 360×640 → 640×360. Touching its on-screen X button selects Play after
   resizing; keyboard input then returns to the saved map with one star and
   the prior completion time. Portrait is letterboxed, preserving the game's
   landscape layout. This supersedes step 5's black-frame result, but is not
   evidence of a native portrait UI or arbitrary resize-stress reliability.

The focused regressions are `make gles-probe-smoke` and
`make egl-surface-resize-smoke` in an isolated test environment with EGL support.

## Current result

**Animated menu, keyboard navigation, first-level movement/completion, and saved
progress after restart verified.** Repeated resizing renders correctly with
portrait letterboxing; touch selection through the on-screen X button works.
Jumping, crate mechanics, later levels, full touchscreen gameplay, audio and clean shutdown remain
unverified. The VM had no default ALSA output device during this run; engine
startup alone is not an audio pass.

Use X to select, C to go back, arrow keys to move, and Enter for the menu, as
described by the [author](https://harmonymonroe.itch.io/tinycrate). Send a focus
event when using Shiraz's headless live-input tool. Run the APK only on the
test VM and keep captures private; no game assets are included in this repository.
