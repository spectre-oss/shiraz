# Unciv — libGDX

- Upstream: [Unciv 4.22.15](https://github.com/yairm210/Unciv/releases/tag/4.22.15).
- Artifact: `Unciv-signed.apk`, unchanged; version code 1279, min SDK 21.
- SHA-256: `46e477b96e897919e23e44a0a4a07e71d0a293ef4c64109112b10612aacc6520`.
- Native ABIs: ARM32/ARM64/x86/x86-64; this test selects x86-64.
- Test: 2026-10-28–29, separate x86-64 Linux VM with software graphics; source
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
5. The render thread next failed because `android.opengl.GLES20` was missing.
   Added a JNI bridge for GLES2 shader/program, texture, buffer, uniform, query,
   and draw operations. Buffer positions and bounds are checked. An ART shader
   triangle/readback regression and a HotSpot JNI-check run pass on the VM.
   The library is included in runtime dependencies and AppImage packaging.
6. Retesting exposed glGetFloatv during texture-filter initialization. Adding the
   float query advances the unchanged APK to its language selector and main menu.

7. A later startup on baseline `68d5ebe` logged a sound-decoding failure:
   decoded samples were required to stay within ±1. VM probes of five bundled
   MP3 effects reproduced finite values outside that range, reaching about 1.89.
   The decoder now clips finite samples to full scale before passing them to the
   mixer; NaN and infinities still fail. A floating-point WAV regression fails
   before the fix and passes afterward, as do all five affected real-asset probes.
   After deployment, the unchanged app no longer logs the normalized-range
   rejection during startup and the gameplay sequence below. This verifies
   decoding, not audible playback. Separate probes still reject some longer
   assets at the existing decoded-size limit and report one asset as truncated;
   those decoder limitations remain unresolved.

## Observed walkthrough

- Selected English, opened Quickstart, and dismissed tutorial/leader dialogs.
- Used the warrior's Explore action: the unit moved, more tiles appeared, and
  selection switched to the settler.
- Used Found city: Onondaga appeared, yields changed, and research/construction
  prompts became available.
- Saved through the in-game menu with the default save name.
- Resized from 640×360 to 360×640. The map and HUD reflowed; a tap on the newly
  positioned research button opened the technology tree.
- Restarted the app: English remained selected. Selected the saved game and
  loaded it; Onondaga, the explored map, and the displayed yields were restored.

- On the later baseline, selected Pottery research and queued a Worker in
  Onondaga. Advanced through six turns using the actual Next turn and Move
  automated units controls. Treasury, research and construction counters changed;
  the city grew from population one to two. Encounter dialogs for Geneva and
  the Netherlands rendered and accepted input; Geneva granted gold and faith.
- Turn six completed Pottery and produced the Worker. Both completion messages
  and the research dialog rendered. Closing and restarting, then selecting
  Resume, restored turn six, the completed research, population and Worker
  completion notification. An earlier restart in the same walkthrough restored
  turn one via Resume; the initial missing-autosave error ceased after normal turn progression
  created an autosave.

**Interactive offline gameplay observed; partial compatibility.** The bounded
walkthrough does not establish a complete campaign, multiplayer, mods, or audio.
The VM has no audio output device. A direct tile tap briefly left the map blank;
the Explore action restored it. This rendering issue remains under investigation.
Six-turn progression, research completion and Worker production are verified;
longer campaigns remain untested. A handled `MediaPlayer not prepared` error
also appeared during music resume; music playback remains unresolved.

The GLES20 bridge is a subset: client vertex-array buffers and several less-used
entry points remain unsupported. These observations apply to this APK and the
tested software graphics configuration, not all libGDX games.
