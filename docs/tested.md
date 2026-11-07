# Tested applications

Shiraz is experimental. This list records bounded observations, not certification
or a guarantee that an application works completely. App versions and runtime
changes matter. New framework experiments run only in a separate test VM.

## Established app targets

| App | Evidence available | Limitations |
| --- | --- | --- |
| OsmAnd | Earlier walkthroughs reached maps, navigation drawer, and route-detail screens; a packaged run also verified landscape resizing. | Historical evidence, not a fresh result from this VM series. Complete search, routing, downloads, restart persistence, and Android visual parity remain unverified as a whole. |
| F-Droid | Earlier web-runner testing rendered its main screen. A later fix made the service's access-audit state directory writable. | Historical evidence. Complete repository refresh and app installation workflows are not established by those tests. |

## Current framework experiments

The [experiment index](experiments/README.md) links platform and per-app reports.
Results are updated as the reproduce–fix–retest loop progresses; open issues must
remain explicit. No unrelated private experiments are included in this list.

| App | Framework | Current observation |
| --- | --- | --- |
| [Obtainium](experiments/flutter/OBTAINIUM.md) | Flutter | Settings, text entry, back navigation, and theme persistence verified. Add retrieves GitHub releases, displays package choices and reaches details; the app entry survives restart. Adaptive icons render with a circular host mask. Saved-entry landscape/portrait resizing and back navigation now work after GL routing fixes. Manual JSON export works through the app-storage folder picker, including repeated export after restart. Importing its own export reports one app plus settings restored, and the saved entry remains visible. This overwrites existing data; clean-state restoration is unverified. Release-asset download still encounters an Android storage-path assumption; installation remains unverified. |
| [Joplin](experiments/react-native/JOPLIN.md) | React Native | Notes list, Add menu, note screen, title input, and title persistence across restart verified after line-height fixes. Editor toolbar and notice now render; body editing is unsupported because WebView has no JavaScript/DOM engine. |
| [SEN-214 Calculator](experiments/react-native/SEN_214_CALCULATOR.md) | React Native / Expo | Fixed explicit OpenType font-feature rejection. Basic keypad evaluates `7 + 5` to `12`; scientific mode evaluates `sin(30)` to `0.5`. Fixed numeric-input rejection; the permutation dialog accepts editing and computes `5P2 = 20`. Short-window controls overlap, and result text can disappear after resize until changed. Complete scientific functionality and persistence remain unverified. |
| [2048](experiments/unity/2048.md) | Unity | Release has only 32-bit native code; rejected by this 64-bit runtime. |
| [Lucas Doggiehood](experiments/unity/LUCAS_DOGGIEHOOD.md) | Unity | Animated scene, house/Settings input, camera drag, multitouch zoom, quest acceptance, item collection, and reward persistence across restarts verified in successful runs. A native buffer-default fix restores actual portrait rendering and Settings input across repeated resizes; some portrait UI remains clipped. Headless tests require an explicit focus event. Intermittent startup stalls and lifecycle timeouts, audio, longer progression, and app-requested termination reporting remain open. |
| [Unciv](experiments/libgdx/UNCIV.md) | libGDX | Offline map, unit exploration, city founding, save/reload, portrait relayout, six-turn progression, Pottery research completion and Worker production verified. Fixed rejection of finite over-full-scale samples and MPEG files with overestimated frame counts; downloaded music and pause/resume produce PCM through a VM file sink. Speaker output and timing remain unverified. Rendering stability and longer campaigns remain under test. |
| [Abra-Ca Bubble](experiments/godot/ABRA_CA_BUBBLE.md) | Godot | ARM64 APK passes splash/theme, storage setup, native dependency loading, and thread creation. Its Vulkan requirements warning renders and responds to OK after dialog fixes. Vulkan rendering remains unsupported; no game scene verified. |
| [SuperTux](experiments/sdl/SUPERTUX.md) | SDL | ARM64 release renders an animated startup screen and reaches the main menu through touch input after runtime fixes. Repaired bitmap and cursor APIs allow Start Game, the intro, touch-driven world-map navigation, level rendering, pause, and landscape/portrait resize. Mouse hover, preference reload, and menu-driven exit also work; the native release-check resolver crash is fixed. Keyboard and touchscreen movement/jumping, scrolling through successive platform sections, coin collection, and restoration of the selected world-map node are verified. Enemy death/restart and mouse-driven level/world exit also work. Fixed an ARM64 thread-scheduling abort and unpaced OpenSL callbacks; menu music, a gameplay sound effect, and their separate toggles now produce PCM through a VM file sink. Level completion, campaign persistence and speaker output remain unverified; portrait controls overlap. |
| [Tiny Crate](experiments/godot/TINY_CRATE.md) | Godot | ARM64/OpenGL ES release renders its animated menu after adding a missing framework theme. Keyboard navigation, jumping, first two levels and two-star progress after restart verified; crate pickup/release also works. Added EGL11 and fixed capability-probe display lifetime, current-surface preservation and stale-frame handling. Repeated resizing now renders with portrait letterboxing; on-screen touch selection works. Music-volume control produces verified PCM through a VM file sink. Full touch gameplay, physical audio, clean shutdown and later levels remain unverified. |
| [Mindustry](experiments/arc/MINDUSTRY.md) | Arc | Fixed missing GLES30 vertex-array operations; assets load and the main menu renders. Fixed Java framebuffer routing that left Campaign gray. Planet previews, Serpulo selection and Ground Zero’s tutorial world now render; touch dragging, landscape resizing and copper mining (`0/15` to `6/15`) work. Mechanical Drill research/construction and restoration after Save & Quit plus process restart are verified. Conveyor research, long-press construction and resource delivery into the core work; the 100-copper and 25-lead objectives complete, reaching turret construction. Portrait redraws but tutorial text clips; tutorial completion, combat and audio remain under test. |
| [Ember Quest](experiments/flutter/EMBER_QUEST.md) | Flutter / Flame | Impeller OpenGLES renders the scene and menu. Fixed CLI native font setup to restore labels, instructions and score. Initial keyboard-focus assignment restores left/right movement and jumping; Game Over and Play Again work. Direct landscape launch shows the full player and ground; portrait-to-landscape resize clips the player. Collection, progression, persistence and audio remain unverified. |

## 2048 by tpcstld — separate app retest

The Play listing supplied for this test identifies `com.tpcstld.twozerogame`,
not the Unity app `com.starsep.game2048` above. These are separate applications.
The tested artifact is the unchanged upstream [GitHub 1.3.3 release](https://github.com/tpcstld/2048/releases/tag/1.3.3),
`133-release.apk`, SHA-256
`caed84eb0556cb5745da3cfaab8587244fc0122b49fe3df80d0caea3908f1f39`.
It has no native libraries. This is not a newly downloaded Google Play build.

On 2026-11-07, a bounded x86-64 standalone ART run with software SDL presentation,
390×720 dimensions, isolated app storage and no network reached `MainActivity`
and `MainView.createBitmapCells`. The first run rejected the tile's root
`android:padding` attribute. Android's GradientDrawable reads padding from its
`<padding>` child; this misplaced root attribute is ignored. Shiraz now accepts
it without applying padding and retains the child-padding behavior.

Retesting the unchanged APK rendered the real game board. After dismissing a
Google Play Services warning, pointer swipes moved the tiles. A subsequent swipe
merged two 2 tiles into a 4 tile, with score and high score increasing to 4.
Resizing to 720×390 recreated the activity, preserved the board and rendered the
landscape layout. A clean window close and restart back to 390×720 restored the
same board, score and high score. The Play Services warning recurs on recreation;
it can be dismissed to continue offline play. The Undo control also restored the
previous board and score while retaining the high score.

These are bounded gameplay observations, not complete app certification. Long
games, reaching 2048, Play services features and the current Play Store build
remain unverified. The separate starsep Unity variant was removed from the web
catalog at the user's request; its older experiment report remains historical.
