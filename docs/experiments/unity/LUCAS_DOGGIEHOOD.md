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

11. Closing the activity exposed missing PixelCopy callback types. Added real
    Surface/SurfaceView copying with Handler-delivered results, crop/scaling,
    and no-frame/source/destination validation. ART and JNI-check regressions
    cover software frames and EGL frame swaps. The next observed shutdown crash
    is in ART alternate-signal-stack teardown; a native ownership fix is being
    built and has not yet been verified.

12. Compared the test deployment with AppImage packaging: GLES was mapped
    directly to the host library and two independent copies of the Shiraz EGL
    adapter were loaded. Routed EGL/GLES through one shared adapter instance.
    The unchanged APK now renders the neighborhood, buildings, moving characters,
    currency/settings HUD, and onboarding prompt. This is a real Unity scene,
    not the splash. The packaged builder already maps these APIs through its
    adapter; the deployment correction is not evidence of a new APK modification.

13. Repeated launch renders the scene again. Pointer input opens a house panel
    with residents/upgrade controls and opens Settings. A larger window continues
    rendering, but the capture appears scaled; native responsive game layout is
    not yet established. A drag replay delivered in one frame acted as a tap,
    so camera dragging has not passed acceptance.

14. Rebuilt ART with explicit ownership of alternate signal stacks. The native
    regression fails on the old runtime, which replaces an embedding thread's
    stack, and passes on the rebuilt runtime for both native attachment and
    managed-thread stack replacement. An isolated game run confirmed the rebuilt
    library was loaded and no longer reproduced the invalid-free crash. Closing
    still reports a primary-window detach timeout and exits with signal 9;
    this is not a successful clean-shutdown result. The reproducible regression
    is available through `make art-alt-stack-smoke` after rebuilding ART.

15. Fresh repeated runs with the rebuilt ART remain black, with UnityMain waiting
    on a condition variable. The deployed runtime still renders the scene with
    the same framework and graphics adapter. The ownership fixture passes, but
    the rebuilt runtime is **not yet a verified game-compatible replacement**.
    A syscall trace attributes signal 9 to the game calling kill on itself after
    lifecycle timeouts, rather than to the runner's time limit.

16. On the deployed runtime, a drag delivered over separate frames moves the
    neighborhood camera. The game's onboarding advances from dragging to its
    zoom instruction. A two-finger spread then zooms into the scene and advances
    onboarding to the speech-bubble task. This supersedes the earlier
    inconclusive drag replay and verifies multitouch zoom as well.

17. Isolated the black frame to the test launcher's forced ART preload: doing
    the same with the old library also reproduces it. Loading rebuilt ART through
    an isolated copy of its normal native loader restores the scene; process
    mappings confirm the rebuilt library. The ownership regression passes with
    that arrangement as well. No production ART library was overwritten.

18. SurfaceView did not respond to window visibility, leaving a live surface
    until after engine destruction. It now releases/recreates surfaces when the
    window hides/shows, and activity stop hides the window before onDestroy.
    Surface and activity regressions pass on ART and host Java. The real game's
    next close no longer reports the window-detach timeout or invalid free.
    The game still requests self-termination with signal 9, which the runner
    currently reports as a failed process; orderly runner cleanup is not claimed.

19. Later repeat launches exposed intermittent startup stalls after the splash.
    Comparing EGL adapter revisions initially suggested a graphics regression,
    but repeated runs with the intermediate and current adapters rendered the
    neighborhood successfully when tracing was enabled. That is not proof of
    a fix or a deterministic adapter regression. A stalled-run thread snapshot
    showed the Unity loader waiting; the startup cause remains unresolved.
    Stalled runs also reproduced pause/detach timeouts on close, so step 18's
    successful lifecycle observation does not cover every startup state.

20. On a successful current-adapter run, resizing from 640×360 to 800×450 kept
    the scene animated. At 360×640 the scene stretched instead of adjusting its layout;
    pressing the visible Settings button, including a held press/release, did
    not open the panel. Returning to 640×360 restored the normal image and a
    press on Settings opened the panel. This verifies a recoverable resize/input
    limitation, not responsive portrait support. The APK remained unchanged.

21. Traced SurfaceView layout and native buffer geometry under
    `SHIRAZ_TRACE_SURFACE=1`. The view resized to portrait, but Unity's subsequent
    `ANativeWindow_setBuffersGeometry(0, 0, ...)` restored the original landscape
    buffer dimensions. Host surface resizing now updates the default dimensions
    used by that reset, matching the
    [native-window buffer contract](https://developer.android.com/ndk/reference/group/a-native-window#anativewindow_setbuffersgeometry).
    Native tests cover repeated host resizes, producer overrides, resetting to
    the latest default, and rejected resizes while a buffer is locked. The reset
    test fails with the previous host resize behavior and passes with the fix.
    The unchanged game now renders at the actual portrait dimensions without
    stretching, responds to the visible Settings button there, and preserves
    the open panel when resized back to landscape. Some game UI remains clipped
    in portrait. This resolves step 20's stale-buffer/input failure; it does not
    establish that the game's full UI supports portrait.

22. Followed the tutorial through dragging, two-finger zoom, Bailey's moving
    speech bubble, and **Accept** in the quest dialogue. The moving bubble was
    located from the current app frame on the VM, then clicked through the
    normal pointer path; no app state or APK content was edited. After the
    bounded runner timed out, a new launch restored the accepted quest's world
    marker. Clicking that marker removed it and increased displayed currency
    from 0 to 20. The initial drag tutorial restarted, so tutorial-step
    persistence is not established. A subsequent reward-check launch stalled
    after the splash; thread inspection again showed the Unity main thread
    waiting and its preload thread sleeping. Startup reliability remains open.

23. After closing that stalled process, another unchanged launch rendered the
    neighborhood with currency still at 20 and the collected marker absent.
    This verifies persistence of the accepted quest and its collected reward
    across restarts. It is not a full campaign/save-system pass.

**Interactive scene, camera drag, multitouch zoom, and repeated native resizing
verified in successful runs; startup is intermittent.** Collider warnings still
appear. Portrait Settings input now works, but parts of the UI are clipped.
Accepted-quest and reward persistence are verified. Longer progression,
startup reliability, full portrait usability,
and reporting of app-requested process termination remain open.
