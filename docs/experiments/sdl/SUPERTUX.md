# SuperTux — SDL

## Artifact and environment

- [Upstream release](https://github.com/SuperTux/supertux/releases/tag/v0.7.0).
- Unchanged `SuperTux-v0.7.0-arm64-v8a.apk`, version 0.7.0, version code 7,
  min SDK 21, target SDK 35.
- SHA-256: `7dd41db4c38862a22625ca0cb66f382d2c30653254a7a2807887cedde4b8f0f9`,
  matching the upstream release asset digest.
- Tested on 2026-10-28–29 in the separate x86-64 Linux VM with the optional
  Digitalis/Berberis ARM64 interpreter and two-gear translation mode. Source baseline `b3f1ff8` plus the changes
  described below. No APK or game code executes on the development machine.

## Reproduce, fix, retest

1. Inventory rejected the APK's 287,524,709-byte `assets/data.zip` because the
   general entry limit was 128 MiB. Added a separate 384 MiB asset-entry budget;
   inspection does not decompress asset contents. The 768 MiB total archive
   budget and stricter DEX/resource/native-library limits remain intact. Tests
   cover the asset boundary, other paths, and total-budget rejection. Retesting
   the unchanged APK passes inventory and installs its five native libraries.
2. Activity startup then rejected Android's `Theme.NoTitleBar.Fullscreen`
   (`0x01030007`). Added this genuine pinned AOSP style and its dependency
   closure to the selected framework resources, preserving the public ID.
   The VM rebuild and unchanged APK retest pass theme application.
3. Native loading aborted while resolving `glActiveShaderProgram` from
   `libGLESv2.so`. Extended the upstream proxy builder's shared graphics-adapter
   dispatch to GLES2 as well as GLES3, retaining the upstream callback wrappers.
   The rebuilt proxy passes that failure. The app creates a landscape surface
   and reaches its native SDL thread, then aborts on unsupported
   `pthread_attr_init`. This is the next runtime blocker, not a gameplay result.
4. Added guest-layout attribute initialization, setters/getters, and destruction,
   including Bionic's scheduling enum values and stack validation. Host tests and
   the real ARM64 thread fixture pass attribute-driven thread creation, joined
   results, TLS/destructors, and both explicit detach and detached creation.
   The unchanged APK passes `pthread_attr_init` and creates another native
   thread; it then aborts on unsupported `pthread_setname_np`. Thread naming and
   guest/host thread-handle translation are the next investigation.
5. Added live-thread handle resolution for naming through both guest
   `pthread_self()` and host-backed creation handles. Host tests cover naming,
   invalid sizes, exited handles, and fork cleanup; the ARM64 fixture verifies
   self/cross-thread naming and retains the thread lifecycle checks. The launcher
   also now passes the configured translator's absolute path: basename-only
   selection could load an older deployed copy despite the requested overlay.
   The unchanged APK retest passes naming. It then exits with status 1; a syscall
   trace shows a missing `images/engine/icons/supertux-256x256.png` under the app's
   external-files directory immediately before exit. Game-data initialization
   needs investigation; the missing file's root cause is not yet established.
6. The NDK asset opener still rejected assets above 128 MiB even after APK
   inventory accepted the larger game pack. Aligned native access with the
   384 MiB asset budget. The stream regression reads/seeks a compressed asset
   larger than 128 MiB and rejects one above 384 MiB. Retesting the unchanged
   APK lets the game replace its empty data archive with the full pack: the
   copied file's SHA-256 matches `assets/data.zip` inside the APK, and it contains
   the previously missing icon. No manual extraction or replacement data was
   used. Startup now aborts on unsupported ARM64 `setjmp`; gameplay is still
   unverified.
7. Connected the pinned native-bridge setjmp thunks to Digitalis's ARM64
   register save/restore implementation, including zero-to-one longjmp return
   normalization. The ARM64 fixture passes ordinary and signal-aware jumps,
   negative/nonzero returns, saved-mask restoration, and unsaved-mask retention.
   Retesting passes the previous setjmp abort and reaches SDL haptic discovery.
8. SDL then aborts JNI dispatch because `InputDevice.getVibrator()` is absent.
   Connected the aggregate desktop input device to the existing vibrator object
   that reports no hardware. The next run passes that call and remains on a
   black surface during the observed startup interval; rendering and gameplay
   are not established. The VM also reports unavailable audio hardware.

9. A longer run exposed a test-environment leak: the host's
   `SDL_VIDEODRIVER=dummy` and `SDL_RENDER_DRIVER=software` also selected
   the Android app's SDL backend. A debugger capture showed a partially drawn
   mountain background in an offscreen SDL buffer, not a presented game frame.
   The launcher now moves these settings to host-only variables, and host SDL
   consumes them as instance hints without changing the process environment.
   The headless presentation regression passes with the original variables absent.
10. With that isolation, SDL enters Android window setup and aborts because
    `setRequestedOrientation` is called from its worker thread. Orientation
    preferences now use a synchronized queue; only the activity thread can
    consume the pending resize. The regression verifies worker publication,
    owner-only consumption, and one-shot delivery. The unchanged APK passes
    the previous abort and reaches Android pixel-format negotiation. The
    observed presentation remains black; this is still not verified gameplay.

11. A longer two-gear run reaches the animated startup screen. A debugger
    breakpoint confirms normal EGL swaps call Shiraz's window-frame publisher;
    the captured window shows the game, not an offscreen debugger buffer.
    Touch input declines the Internet and release-check prompts and reaches
    the main menu. Held touches work; immediate press/release pairs were not
    consistently accepted during this run. Some prompt text is clipped at
    640 × 360. No timing claim is made: this run overlapped regression checks
    and debugger pauses.
12. After selecting Start Game, JNI aborts on the missing color-array
    `Bitmap.createBitmap` overload. Added immutable, independent pixel copies,
    density-aware overloads, validated positive/negative row strides, and
    overflow-safe source bounds within the existing supported bitmap formats.
    The VM regression passes pixel, immutability, density, and bounds checks.
    The unchanged APK retest repeats both prompts and the main menu, passes
    bitmap creation, then aborts on missing `PointerIcon.create(Bitmap, float,
    float)` during the Start Game interaction. Custom cursor support is the
    next runtime blocker.

**Animated startup and touch-driven main menu verified; gameplay remains open.**
Custom cursor support, movement, resize/back, audio, and save/restart remain
unverified. Startup uses software graphics and ARM64 translation; it is not
yet evidence of acceptable game performance.
