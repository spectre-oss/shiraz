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
    Touch input advances through the Internet and release-check prompts to
    the main menu; the selected choices were not independently verified. Held touches work; immediate press/release pairs were not
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

13. Added custom bitmap pointer icons with validated hotspots and SDL color
    cursor delivery. Java tests cover icon data/equality and rejection; JNI
    tests exercise the active-surface path; native tests create and replace
    cursors under an X11 virtual display and verify hide/restore behavior.
    The unchanged APK passes the previous cursor abort. Start Game opens the
    animated introduction; Escape skips it to the world map. Touch controls
    move Tux to the next node and enter “Welcome to Antarctica.” The level
    renders Tux, enemies, terrain and animated blocks; Escape opens its pause
    menu. Resizing from 640 × 360 to 960 × 540 changes the visible game area,
    and portrait 360 × 640 and return to landscape do not crash. Portrait touch
    controls overlap. Direction/jump attempts did not establish reliable
    player control; death/restart transitions occurred during observation.
    A tap aimed at Abort Level resumed the game instead, suggesting menu
    confirmation and precise touch hit-testing must be distinguished. This
    remains an input/performance investigation, not a gameplay pass.

14. Input tracing confirms that normalized touch coordinates and keyboard
    codes arrive at SDL's ARM64 JNI entry points. The host runner nevertheless
    consumed mouse hover after choosing a cursor icon. It now also dispatches
    generic motion through the dialog/popup/content path. A nested-view test
    verifies hover coordinates and hidden-child exclusion. In the real APK,
    moving the mouse over Audio previously left Locale highlighted; after the
    fix, Audio highlights without clicking. Main-menu hover also tracks the
    selected item.
15. Choosing Quit through the menu exits successfully and writes preferences.
    Restart skips the first-run prompts, but the saved release-check setting
    exposed unsupported native `getaddrinfo`. The resolver bridge now converts
    Bionic's flags, error numbers and address-list layout, with matching
    `freeaddrinfo` and `gai_strerror`. Numeric IPv4/IPv6 and failure-path host
    tests pass; the host test also passes AddressSanitizer/leak detection, and an
    ARM64 fixture verifies the returned layout and errors through ART/Digitalis.
    The unchanged APK passes the former release-check abort and reaches the
    interactive menu. This does not establish every network feature.
    Saved settings also confirmed that earlier touchscreen prompt interactions
    had accepted the selected options; they had not reliably chosen the item
    visually under the finger.

16. Retested the level on baseline `51d3782`, pausing between observations
    so enemies could not kill Tux while captures were being inspected. From a
    live state, held Right and Space visibly move Tux right and into the air.
    The on-screen Right control also moves Tux; simultaneous Right and Jump
    touches jump into a block and increase the coin counter to 101. These are
    actual APK frames delivered through the ordinary input path. Earlier
    death/restart observations did not establish an input-routing defect.
    Restart also restores the selected world-map node, “Welcome to Antarctica.”
    Its title/statistics screen waits for confirmation before play; it should
    not be treated as a stalled level load.

17. On baseline `ff32df7`, another observed keyboard walkthrough advances
    farther through “Welcome to Antarctica.” Right/Space input scrolls the
    level, jumps past enemies, reaches successive block/platform sections,
    and increases the coin counter from 100 to 106. Frames captured before
    opening the pause menu show Tux's airborne positions and the changing
    terrain. Pausing between observations keeps inspection time separate from
    active play. The bounded ten-minute runner then times out; its log records
    that timeout rather than an application crash. This extends movement,
    scrolling, and collectible coverage, but does not establish level completion,
    damage handling, or persistence of completed-level progress.

18. On baseline `b7fb92e`, observed an enemy collision and Tux's death animation,
    followed by an automatic return to the level start with the app still running.
    This establishes this death/restart path, not every damage or checkpoint case.
    A subsequent pause menu accepted mouse hover followed by a held click on
    **Abort Level** and returned to the world map. The same pointer sequence
    selected **Leave World**, and the main menu's **Quit** ended the runner with
    exit status zero. These clicks used the actual visible menu coordinates;
    earlier touchscreen selection limitations do not prevent this mouse path.
    Short key presses were not consistently reflected in the observed UI on this
    VM; longer held presses worked. No event-loss cause or runtime fix is claimed
    from that observation. Level completion remains unverified.

19. Enabled actual OpenSL ES output through an ALSA file sink on baseline
    `f6dfcdd`. This exposed an ARM64 libc-bridge abort at
    `pthread_setschedparam`, previously hidden by missing audio hardware.
    Added scheduling get/set operations and priority changes through the live
    guest-thread registry, translating guest self handles and preserving host
    errors. Host and ARM64/ART fixtures cover both handle forms, invalid requests,
    and exited threads; host tests also cover fork behavior.
20. With scheduling calls available, the file sink exposed unpaced OpenSL buffer
    callbacks: the mixer consumed CPU and emitted PCM far faster than real time
    while the screen stayed black. The worker now waits for the submitted audio
    duration, counting time already spent in the device write, and wakes on clear
    or destruction. A callback-timing regression fails before this change and
    passes afterward, alongside queue, marker and waveform assertions.
    The unchanged APK then reaches its animated menu with audio enabled. In
    Options → Audio, Music off produces a near-zero captured signal (one-second
    window peak 1, RMS about 0.5); Music on restores a non-silent signal (peak
    8,664, RMS about 1,742). The game exits normally through Quit. This verifies
    menu music and its toggle through ARM64/OpenSL and a VM file sink; speaker
    output, hardware timing and gameplay sound effects remain unverified.

21. On baseline `428c774`, entered “Welcome to Antarctica” with the audio
    sink enabled, moved and jumped, and collected a coin (100 → 101) without
    the former scheduling abort. Disabled Music through the in-game Audio menu
    while leaving Sound enabled. A 1.5-second idle PCM window had peak 1 and
    RMS 0.502; a jump window had peak 1,904 and RMS 151.692. Disabling Sound
    through the same menu and repeating the jump returned to peak 1 and RMS
    0.501. These are stereo signed-16-bit samples at 48 kHz captured on the VM.
    This verifies gameplay sound-effect output and the Sound toggle separately
    from music, superseding step 20's unverified-effects limitation. It does not
    verify every effect, physical speakers or hardware latency. Window close
    ended the runner with exit status zero. This run did not complete the level.

**Animated startup, menus, mouse hover, world-map navigation, level rendering,
keyboard movement/jumping, touchscreen movement/jumping, coin collection and
resize verified.** Preference writing/reloading, restoration of the selected
world-map node, enemy death/restart, mouse-driven level/world exit, and a
menu-driven clean exit are verified. Completing a level,
campaign progress persistence, speaker output and general
network compatibility remain unverified. Menu music, a gameplay sound effect,
and their separate toggles produce verified PCM through a VM file sink.
Portrait controls overlap, and precise touchscreen menu selection
remains limited. Startup uses software graphics and ARM64 translation; these
bounded interactions are not a performance benchmark or a full-game pass.
