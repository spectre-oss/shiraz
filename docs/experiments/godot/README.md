# Godot experiments

Godot Android games combine a Java activity and plugins with a native engine,
project assets, graphics, audio, and input. Engine version, renderer, plugins,
and APK architecture all affect compatibility. An ARM64-only APK on an x86-64
host also requires the optional native-code translation path.

- [Abra-Ca Bubble](ABRA_CA_BUBBLE.md): Godot 4.3, ARM64; startup compatibility
  investigation in progress.

Acceptance requires a visible game scene, working gameplay input, resizing,
and any save/restart behavior offered by the game. Loading the engine or showing
a splash does not establish playable-game support.
