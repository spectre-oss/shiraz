# Lucas Doggiehood — Unity IL2CPP

- Upstream: [v0.16.2](https://github.com/derekwinters/lucas-doggiehood/releases/tag/v0.16.2).
- Artifact: `doggiehood-v0.16.2-emulator.apk`, unchanged; app version 0.16.2,
  version code 314, min SDK 25. This is the upstream x86-64 variant.
- SHA-256: `f91045a0d992b3d1b2e4097e529f03d177de4d5fc769bb1acc16dfdecc189ed3`.
- Framework evidence: `libunity.so`, `libil2cpp.so`, and `libmain.so` for x86-64.
- Test: 2026-10-28, separate x86-64 Linux VM with software graphics; source
  baseline `0b59ef2` plus the changes described below.

## Reproduce, fix, retest

1. UnityPlayerActivity failed to resolve `android.telephony.PhoneStateListener`.
2. Added the legacy call-state listener and idle-call reporting for a host without
   a modem. Registration queues a callback on the listener's Looper; unregistering
   cancels pending delivery. A regression covers those lifecycle behaviors.
3. Retesting advanced to native Unity loading, where `libunity.so` failed to link
   and Unity called JNI FatalError. Debugging the first linker error identified
   `eglSurfaceAttrib`. Implemented default window swap/multisample attributes;
   the EGL regression passes and the loader advances.
4. The next missing symbol was `AChoreographer_getInstance`. Added software frame
   pacing through the owning native Looper, with one-shot and delayed callbacks.
   Tests cover callback thread, reposting, delay, and thread teardown. This does
   not implement compositor presentation timestamps.
5. Retesting advances past those symbols and stops at
   `ASensorManager_getInstance`. Native sensor enumeration remains unsupported.

**Startup blocked.** No Unity game scene or input-driven gameplay has been
observed. The next acceptance step is loading a real scene and interacting with
it, then repeating launch and shutdown without a crash.
