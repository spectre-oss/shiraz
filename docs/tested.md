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

## Subway Surfers 3.71.0 — ARM64 split, partial offline startup

The supplied `com.kiloo.subwaysurf` APKM (version code 97327) contains a base APK
and ARM64/ARM32 native splits. The unchanged base SHA-256 is
`b1a6f7a677d5e9687bb271c0efb32ac6e627d181ff854a5ec010fbb482c2c30b`;
the ARM64 split is
`f7ad8a95181332f5b116de628a4991e67603f407f587fce405e5a33f6bba15a2`.
The x86_64 development runtime loaded its 19 ARM64 libraries through the optional
pinned Digitalis bridge. This is not an AppImage compatibility result.

Startup exposed incomplete/stale proxy deployment, current-thread attribute and
signal/exit gaps, a missing NDK media bridge, missing debugger/orientation APIs,
package-query flags, and local Messenger parcel support. The corresponding
changes were exercised with the real APK and dedicated regression fixtures.
The media bridge forwards the existing host subset and explicitly rejects
unsupported calls; it does not provide every Android codec API.

In an isolated Linux network namespace, the game rendered its loading screen,
age confirmation, optional ad-personalization choice, title screen and 3D run.
A synthetic local adult test profile was used and ad personalization declined.
Normal input actions exercised lane movement, a roll animation, and pause/resume.
Resizing from 390×720 to 720×390 changed the rendered camera viewport. The initial
profile choices persisted across restart. No APK patch, Google account, purchase,
or online session was used. Both input APKs remain unchanged.

**Remaining blocker:** forward progress stalls around score 43–44 at the first
barrier. Full gameplay, jump progression, sound, rewards, and online features are
not verified. The stall also occurred using Digitalis's lite translation tier.
A separate read-only timing diagnostic reported time scale 1, approximately
0.016-second frame deltas and focused state; this does not identify the stall's
cause. Optional virtual mouse-to-touch input did not resolve it. Do not interpret
the rendered game screen as full compatibility.

## Telegram 13.2.1 — macOS ARM64, partial compatibility

Recorded on 2026-11-08 using the unchanged `org.telegram.messenger.web` APK,
version 13.2.1 (72919), SHA-256
`2b73067f429a5cc20cf060e84c9296a18a04b77f1aa02739fa6457c6b5a508d2`.
The environment was an Apple Silicon macOS host with standalone ARM64 ART, SDL
presentation and the experimental guest ELF/JNI loader. This is a development
checkout result on `feat/macos` (source baseline `be412cb`, VERSION 0.338.0,
with additional uncommitted compatibility changes), not a released build or
a Linux/AppImage acceptance result.

### Observed in the actual APK

- The Telegram native library initializes, runs all 18 expected constructors and
  completes `JNI_OnLoad`. This is native initialization evidence, not a claim
  that every native API or syscall is implemented.
- The welcome screen, login form, country picker, country search/selection and
  text input were exercised through normal app input. Portrait/landscape
  resizing and a restart were exercised; saved authenticated login was not.
- A user-controlled login reached successful `auth.sendCode` and authentication
  response callbacks. Phone numbers, codes and passwords stayed in the app.
  No account details, private screenshots or session data are published here.
- A missing `CLOCK_BOOTTIME` mapping was reproduced in a guest regression and
  repaired. Telegram uses this clock for network timing. Initial MTProto
  handshake probes also passed on both the host and the guest socket bridge;
  neither result alone establishes full messaging compatibility.
- Authentication was followed by a missing `MediaRouter` crash during media
  initialization. The local default-audio route API was added, its component
  tests pass, and the actual APK's media initializer passes a pre-login check.
  The signed-in chat screen has not yet been verified after this repair.

### Component results and remaining limits

OpenJDK and ART component tests cover PCM playback, WAV/AAC decoding, AAC
encoding, synthetic MPEG-4 output, playback timestamps, JPEG encoding/rotation,
local audio-route metadata and capture-consent rejection. These use synthetic
media or bounded bridge tests; they are not microphone/camera acceptance inside
Telegram. Audio-only recording APIs exist, while video recording and remote/cast
routing remain unsupported. Physical microphone/camera capture, voice messages,
calls and video calls remain unverified.

App storage is persistent per package, separate from per-APK build/run artifacts.
Synthetic preferences survive separate OpenJDK and ART processes, including
queued writes drained at clean shutdown. Telegram returning to its login screen
after the crash does not prove app data was erased: the crash occurred in a
login-success cleanup path that can run before the current user is saved.
**Telegram login persistence across restart remains unverified.**

Still required: reach the signed-in chat UI, verify saved login after a clean
restart, exercise messaging with an authorized test destination, and validate
actual microphone/camera use and calls. Earlier intro-animation gaps and
short-landscape keypad clipping also remain open. Public results must be updated
after those flows are observed; successful server authentication or component
tests must not be relabeled as full Telegram support.

## Android coverage tracker — measurement boundaries

The website's Android coverage table separates maintained subsystem notes from
class inventory and behavioral assertions. Its initial development snapshot
records 933 of 4,445 matching `android.*` class names against the pinned Android
15 / API 35 SDK, excluding resource classes. This is a class-name inventory,
not a percentage of Android behavior that works. The runtime advertises API 27;
that declaration does not establish certification at either API level.

The recorded development probe catalog defines 239 assertions. A separately imported,
fingerprint-verified suite 0.333.2 snapshot contains 233 cases, with 49 Network
passes and 184 cases not run. No failures in that targeted run does not mean the
unrun areas passed. The exported report does not record the host platform, so
none is inferred from its synthetic device model. The website retains explicit
PASS, FAIL, UNSUPPORTED, SKIPPED, TIMEOUT and NOT_RUN counts and labels the snapshot
historical. Later maintenance may change these counts; the versioned website
inventory and exact source fingerprint identify each published measurement.

Framework/API work should update the maintained support notes and regenerate
inventories using the workflow in `landing/README.md`. App-flow results stay in
the app directory and their reports. Physical capture, account persistence and
complete messaging/calling require their own real-app acceptance evidence.
