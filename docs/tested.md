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
| [Obtainium](experiments/flutter/OBTAINIUM.md) | Flutter | Settings, text entry, resizing, back navigation, and theme persistence verified. Background package-query failure remains. |
| [Joplin](experiments/react-native/JOPLIN.md) | React Native | Notes list, Add menu, note screen, title input, and title persistence across restart verified after line-height fixes. Body editing remains unverified. |
| [2048](experiments/unity/2048.md) | Unity | Release has only 32-bit native code; rejected by this 64-bit runtime. |
| [Lucas Doggiehood](experiments/unity/LUCAS_DOGGIEHOOD.md) | Unity | Deployed runtime renders an animated neighborhood, responds to house/Settings input, and pans the camera. Rebuilt ART has a black-frame regression; native resize, gameplay persistence, and clean shutdown remain open. |
| [Unciv](experiments/libgdx/UNCIV.md) | libGDX | Offline map, unit exploration, city founding, save/reload across restart, and portrait relayout verified; rendering stability and longer gameplay remain under test. |
