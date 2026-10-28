# Unciv — libGDX

- Upstream: [Unciv 4.22.15](https://github.com/yairm210/Unciv/releases/tag/4.22.15).
- Artifact: `Unciv-signed.apk`, unchanged; version code 1279, min SDK 21.
- SHA-256: `46e477b96e897919e23e44a0a4a07e71d0a293ef4c64109112b10612aacc6520`.
- Native ABIs: ARM32/ARM64/x86/x86-64; this test selects x86-64.
- Test: 2026-10-28, separate x86-64 Linux VM with software graphics; source
  baseline `0b59ef2` plus the compatibility fixes described below.

## Reproduce, fix, retest

1. AndroidFont initialization failed with missing `Paint.setARGB(IIII)V`.
2. Implemented the method using the existing Color.argb and Paint.setColor paths.
   Retesting passed that failure and exposed a missing
   `View.OnGenericMotionListener` interface.
3. Added generic-motion listener support to View's existing event dispatch,
   preserving normal fallback when the listener does not consume it. Regression
   tests cover consumption, fallback, disabled views, and unregistering.
4. Retesting creates an ES2 context and switches the app surface to landscape.
   Added the missing GL10 `glGetString` bridge; the graphics regression verifies
   the version query and real pixel/texture operations on ART.
5. Unciv now reports the actual Mesa renderer and framebuffer configuration, then
   its render thread fails because `android.opengl.GLES20` is missing. A valid
   graphics context and a live process do not establish a rendered game scene.

**Startup blocked; investigation active.** No playable game is claimed. Acceptance
requires opening the menu, starting an offline game, and observing a response to
map/game controls. A successful splash screen alone will not establish this.
