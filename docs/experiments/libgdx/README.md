# libGDX experiments

libGDX games use Android Java APIs and native graphics/audio support. Fonts,
input devices, window lifecycle, and EGL/GLES behavior can fail independently.

- [Unciv](UNCIV.md): offline unit actions, city founding, six turns through
  research and Worker completion, restart persistence, and responsive resizing.
  Downloaded music and pause/resume produce PCM in a VM file sink; rendering
  limitations, physical audio output and timing remain open.

Verify a rendered menu, a user-selected game flow, and input-driven state changes
before describing a game as playable.
