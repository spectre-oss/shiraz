# Flutter experiments

Flutter apps combine Android lifecycle/plugin APIs with a native engine, Dart
code, and their own rendering. A Java activity starting does not prove Flutter
engine initialization or input works. Native ABI, NDK asset access, EGL/GLES,
fonts, and each app's plugins must be checked separately.

- [Obtainium](OBTAINIUM.md): rendered UI, release retrieval, saved entries, resizing, and manual export with folder access retained across restart, and an import round trip from its own JSON. Download-path compatibility and installation remain open.

- [Ember Quest](EMBER_QUEST.md): Flutter/Flame scene and menu render after fixing CLI native font setup. Direct landscape launch displays the full scene and Play dismisses the menu; keyboard gameplay and portrait-to-landscape resizing remain unresolved.

Check real frames, touch interactions, navigation, and restart behavior. Report
plugin failures separately from the core Flutter renderer.
