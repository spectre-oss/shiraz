# Shiraz documentation

- [Tested applications](tested.md): observed compatibility and known limits.
- [Droidloom comparison and game targets](others/droidloom.md): architecture, translator lessons, and pending acceptance work.
- [Framework experiments](experiments/README.md): per-platform and per-app investigations.
- [Development](DEVELOPMENT.md): project structure, environment setup, builds, tests, and contributions.
- [AI-assisted contributions](AI_CONTRIBUTING.md): isolated app testing, evidence collection, bug reports, and fixes.
- [Builder](BUILDER.md): package an APK into an AppImage locally in a browser.
- [Web](WEB.md): run apps on a Linux host and interact through a browser.
- [macOS goal](macos_final_goal.md): native macOS (Apple Silicon, Metal) port target and API checklist.

Linux prerelease artifacts are linked from the root [README status](../README.md#status).

The macOS M5 loader/ABI work is in progress. Native-library and OsmAndCore JNI
gates are developer evidence; full OsmAnd application functionality, resizing,
persistence and performance acceptance remain separate M6 work. See the root
[README status](../README.md#status) for the current public scope.

Shiraz is experimental and does not provide Android-style app isolation. Read
the isolation requirements before running APKs. Private session logs and operator
notes belong outside the Git repository.
