# Mindustry — Arc

Status: **main menu rendered; campaign walkthrough in progress**.

## Artifact

- Upstream: [Mindustry v160.7](https://github.com/Anuken/Mindustry/releases/tag/v160.7).
- Distribution: [F-Droid package](https://f-droid.org/packages/io.anuke.mindustry/),
  `io.anuke.mindustry_1115.apk`, version `8-fdroid-160.7`, code `1115`.
- SHA-256: `64bf33842d8f5495656c44868ea3d11ca5998bf6e36bbb428064dcc58c2e9f44`.
- Minimum SDK 21; target SDK 36. APK includes ARM32, ARM64, x86 and x86-64
  libraries. Tested x86-64 on the separate Linux VM with software graphics.
- Shiraz baseline `ba6fa7a` plus the GLES3 VAO bridge below. The APK is unchanged.

## Failure and fix

The rendering thread failed in Arc's `AndroidGL30.glGenVertexArrays` because
`android.opengl.GLES30` was absent. Added the class with real driver-backed VAO
generation, binding, validation and deletion, supporting Java arrays and
position-preserving IntBuffers with bounds and read-only-output checks.
Inherited GLES2 methods remain available. This is a partial GLES3 Java bridge,
not a claim that all ES3 entry points are implemented.

A real ES3-context regression creates separate VAOs, changes vertex state in
one, restores the other, renders a shader triangle and verifies its pixel.
It also checks bindings, deletion, buffer positions, offsets and rejected
undersized/read-only outputs. OpenJDK and ART both pass on the VM, and the
full VM `make check` suite passes with consistent source-build library lookup.

## Observations and remaining work

After the fix, assets load and the title menu renders, including Campaign,
Custom Game, Settings and other controls. The app reports about 56 seconds to
load in this run; concurrent VM verification work makes this unsuitable as a
clean startup benchmark. A resize to 640 × 360 renders the landscape menu.

Mouse input highlights Campaign; touchscreen input advances away from the
menu, but the frame then remains gray. A live ART thread dump places the GL
thread inside indexed rendering of a planet mesh; no missing-method exception
was reported at that point. This does not establish either successful campaign
rendering or a deadlock. Campaign rendering and actual gameplay still need
verification. No
claims are made for building, combat, saves, multiplayer, audio or responsive
portrait layout. The app automatically fetched its public server list during
startup; no multiplayer session was joined.
