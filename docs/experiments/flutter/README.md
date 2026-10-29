# Flutter experiments

Flutter apps combine Android lifecycle/plugin APIs with a native engine, Dart
code, and their own rendering. A Java activity starting does not prove Flutter
engine initialization or input works. Native ABI, NDK asset access, EGL/GLES,
fonts, and each app's plugins must be checked separately.

- [Obtainium](OBTAINIUM.md): rendered UI, release retrieval, saved entries, resizing, and manual export with folder access retained across restart. Download-path compatibility and installation remain open.

Check real frames, touch interactions, navigation, and restart behavior. Report
plugin failures separately from the core Flutter renderer.
