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
| [2048](experiments/unity/2048.md) | Unity | Release has only 32-bit native code; rejected by this 64-bit runtime. |
| [Lucas Doggiehood](experiments/unity/LUCAS_DOGGIEHOOD.md) | Unity | Animated scene, house/Settings input, camera drag, multitouch zoom, quest acceptance, item collection, and reward persistence across restarts verified in successful runs. A native buffer-default fix restores actual portrait rendering and Settings input across repeated resizes; some portrait UI remains clipped. Intermittent startup stalls and lifecycle timeouts, longer progression, and app-requested termination reporting remain open. |
| [Unciv](experiments/libgdx/UNCIV.md) | libGDX | Offline map, unit exploration, city founding, save/reload, portrait relayout, six-turn progression, Pottery research completion and Worker production verified. Fixed rejection of finite over-full-scale samples and MPEG files with overestimated frame counts; downloaded music and pause/resume produce PCM through a VM file sink. Speaker output and timing remain unverified. Rendering stability and longer campaigns remain under test. |
| [Abra-Ca Bubble](experiments/godot/ABRA_CA_BUBBLE.md) | Godot | ARM64 APK passes splash/theme, storage setup, native dependency loading, and thread creation. Its Vulkan requirements warning renders and responds to OK after dialog fixes. Vulkan rendering remains unsupported; no game scene verified. |
| [SuperTux](experiments/sdl/SUPERTUX.md) | SDL | ARM64 release renders an animated startup screen and reaches the main menu through touch input after runtime fixes. Repaired bitmap and cursor APIs allow Start Game, the intro, touch-driven world-map navigation, level rendering, pause, and landscape/portrait resize. Mouse hover, preference reload, and menu-driven exit also work; the native release-check resolver crash is fixed. Keyboard and touchscreen movement/jumping, scrolling through successive platform sections, coin collection, and restoration of the selected world-map node are verified. Enemy death/restart and mouse-driven level/world exit also work. Level completion, campaign persistence, and audio remain unverified; portrait controls overlap. |
