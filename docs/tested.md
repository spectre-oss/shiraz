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
| [Obtainium](experiments/flutter/OBTAINIUM.md) | Flutter | Home, Settings, and Appearance rendered; touch changed the theme. Background package-query failure remains. |
| [Joplin](experiments/react-native/JOPLIN.md) | React Native | Native modules and JavaScript start after metadata/asset fixes; missing UriPermission and a native error-handling abort block startup. |
| [2048](experiments/unity/2048.md) | Unity | Release has only 32-bit native code; rejected by this 64-bit runtime. |
| [Lucas Doggiehood](experiments/unity/LUCAS_DOGGIEHOOD.md) | Unity | Call-state, EGL attributes, and native frame callbacks added; native sensor API now blocks engine loading. |
| [Unciv](experiments/libgdx/UNCIV.md) | libGDX | ES2 context and renderer queries work; missing GLES20 Java bindings block game rendering. |
