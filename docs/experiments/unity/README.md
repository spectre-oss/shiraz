# Unity experiments

Unity games combine the Android player lifecycle, native engine libraries,
Mono or IL2CPP code, and graphics/audio/input. Test the actual APK's ABI first:
an older 32-bit-only release cannot execute in Shiraz's current 64-bit path.

- [2048](2048.md): older Mono release; ABI rejection documented.
- [Lucas Doggiehood](LUCAS_DOGGIEHOOD.md): x86-64 IL2CPP scene, camera controls, house/Settings panels, quest reward persistence, and resizing verified; portrait clipping, intermittent startup stalls, audio, and termination reporting remain open.

Acceptance requires reaching a game scene and observing a response to game input.
A splash screen or native library load is not playable-game evidence.
For headless walkthroughs, deliver `focus 1` through Shiraz's live-input channel
after the window appears; Unity may wait at its splash until the window gains focus.
