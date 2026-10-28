# SuperTux — SDL

## Artifact and environment

- [Upstream release](https://github.com/SuperTux/supertux/releases/tag/v0.7.0).
- Unchanged `SuperTux-v0.7.0-arm64-v8a.apk`, version 0.7.0, version code 7,
  min SDK 21, target SDK 35.
- SHA-256: `7dd41db4c38862a22625ca0cb66f382d2c30653254a7a2807887cedde4b8f0f9`,
  matching the upstream release asset digest.
- Tested on 2026-10-28 in the separate x86-64 Linux VM with the optional
  Digitalis/Berberis ARM64 interpreter. Source baseline `b3f1ff8` plus the changes
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

**Startup investigation; no rendered game or gameplay verified.** Movement,
menus, resize/back, audio, and save/restart remain unverified.
