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
5. Retesting advanced to `ASensorManager_getInstance`. Added native enumeration
   for a desktop with no connected sensors: an empty list, no default sensor,
   empty event queues, and failed sensor registration. No hardware readings are
   synthesized. Queue lifecycle and invalid arguments are regression tested.
6. Unity then reached a resize callback and failed on `Configuration.diff`.
   Added nonmutating configuration comparison and corrected change flags for
   unchanged partial UI-mode updates. Regression tests pass.
7. Retesting loads IL2CPP, relayouts to landscape, and displays the game's
   illustrated splash screen. No scene transition followed the observed tap
   before the bounded timeout.
8. Found that window focus reached views but not the activity callback. Forwarded
   decor focus changes to Window.Callback and added explicit focus control to the
   headless live-input tool. Callback transitions and SDL event dispatch pass.
9. Focusing Unity exposed missing NativeActivity type discovery and
   Activity.getCallingPackage. The former now permits type checks but explicitly
   rejects its unsupported native lifecycle; the latter derives from the calling
   activity. Retesting starts IL2CPP game initialization and frame swaps.
10. The subsequent scene capture is black. One run reported missing collider
    classes during game WorldBootstrap; another stalled after initial swaps.
    Native thread/rendering investigation remains open. These observations do
    not establish whether the remaining problem is wholly in Shiraz or the APK.

**Splash rendered; gameplay unverified.** No Unity game scene or input-driven gameplay has been
observed. The next acceptance step is loading a real scene and interacting with
it, then repeating launch and shutdown without a crash.
