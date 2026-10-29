# Mindustry — Arc

Status: **Ground Zero renders; tutorial gameplay walkthrough in progress**.

## Artifact

- Upstream: [Mindustry v160.7](https://github.com/Anuken/Mindustry/releases/tag/v160.7).
- Distribution: [F-Droid package](https://f-droid.org/packages/io.anuke.mindustry/),
  `io.anuke.mindustry_1115.apk`, version `8-fdroid-160.7`, code `1115`.
- SHA-256: `64bf33842d8f5495656c44868ea3d11ca5998bf6e36bbb428064dcc58c2e9f44`.
- Minimum SDK 21; target SDK 36. APK includes ARM32, ARM64, x86 and x86-64
  libraries. Tested x86-64 on the separate Linux VM with software graphics.
- Shiraz baseline `ba6fa7a` plus the GLES3 VAO and Java framebuffer-routing fixes below. The APK is unchanged.

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

Mouse input highlights Campaign; touchscreen input opens campaign selection.
Originally that transition left a gray frame. Temporary VM tracing found that
Java `glBindFramebuffer(..., 0)` could resolve directly to the host GL library,
bypassing the window adapter when the host library loaded first. Draws then
reported `GL_INVALID_FRAMEBUFFER_OPERATION` against the host default framebuffer.

The JNI entry point now calls a uniquely named adapter function, independent of
GL symbol lookup order. A regression preloads the host GL library, switches
from an offscreen framebuffer back to zero, and verifies both the mapped window
binding and a presented green pixel. The old implementation fails this lookup
order; the fix passes on OpenJDK and ART. Temporary tracing was removed.

The unchanged game now displays both animated campaign planet previews. Touch
selection of Serpulo, confirmation, and Ground Zero's Launch control work.
The game world loads with the copper tutorial; touch dragging changes the view,
and resizing from 640 × 360 to 960 × 540 redraws the world and controls. The
full VM check suite passes with the routing fix.

Dragging toward the highlighted copper patch and tapping it starts a visible
mining beam; the tutorial counter advances from `0/15` to `6/15`. This verifies
resource collection through normal touch input, not completion of the tutorial.
Building, combat, saves, multiplayer,
audio and responsive portrait layout are not established. The app automatically
fetched its public server list during startup; no multiplayer session was joined.
