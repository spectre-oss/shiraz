# Joplin — React Native

## Artifact and environment

- Upstream: [Joplin Android 3.7.10](https://github.com/laurent22/joplin-android/releases/tag/android-v3.7.10).
- Artifact: `joplin-v3.7.10.apk`, unchanged; version code 2097819, min SDK 24.
- SHA-256: `2060b9c98c40a37c85138db641ed20a0eb7d1af13e3e17add434abcdb186f34a`.
- Framework evidence: `libreactnative.so`, `libhermes.so`, and native modules.
  APK includes ARM32/ARM64/x86/x86-64; this test selects x86-64.
- Test: 2026-10-28, x86-64 Linux VM with software graphics; source baseline
  `0b59ef2` plus compatibility changes under investigation.

## Reproduce, fix, retest

1. Startup failed in SoLoader's `ApplicationSoSource` because
   `ApplicationInfo.nativeLibraryDir` was null.
2. The runtime now records the actual extracted native-library directory in
   application metadata, including an existing empty directory for Java-only APKs.
   Retesting advanced to loading the React Native engine.
3. The native loader reported missing `AAssetManager_fromJava`. Publishing
   Shiraz's existing resource bridge in ART's global native symbol scope resolved
   that error. The next observed missing API was `AAsset_read`.
4. Implemented native asset reads, seeks, remaining length, and 64-bit length
   queries. Compressed and uncompressed asset regressions pass on the VM.
5. Optional recording-module reflection needed MediaRecorder listener types.
   Those declarations now exist; actual recording still fails explicitly.
6. Expo font discovery calls `AssetManager.list("fonts/")`. Directory listing
   now accepts one trailing separator while rejecting traversal and empty inner
   components. The ART listing regression passes.
7. Retesting reaches Hermes/JavaScript and installs Expo JSI modules. Startup
   then fails because `android.content.UriPermission` is missing. Error handling
   subsequently aborts with an invalid native stdio handle; that separate native
   interoperability failure remains open. No Joplin UI or note editing is verified.
8. Added URI-permission value types and empty persisted-grant queries. Taking a
   grant still fails explicitly because no host document picker issues one;
   existing URI parcel transport remains unsupported. Retesting passes module
   reflection and reaches React Native text measurement.
9. The app requested optimal StaticLayout breaking. Added bounded paragraph
   optimization for quality and balanced modes, preserving cluster boundaries.
   ART regressions verify distinct wrapping behavior. Automatic dictionary
   hyphen insertion is not implemented.
10. View mounting then needed TextView's disabled selection and default
    justification policies. Added those queries/setters; enabling unsupported
    read-only selection or justification still fails explicitly.
11. ContentResolver rejected local file-URI output. Added local file descriptor
    and output modes with owned stream closure; ART regression covers append,
    truncation, readback, and invalid arguments. Retesting advances to shortcut
    initialization, where Icon.createWithResource rejected a zero resource ID.
12. Resource Icon construction now retains the identifier and defers resolution;
    null package names remain invalid. Retesting passes shortcut initialization.
13. Safe-area measurement required ViewGroup descendant rectangle conversion.
    Added forward/inverse mapping through nested layout and scroll offsets, with
    a regression for unrelated views and unchanged output on failure.
14. React Native view mounting required Color.valueOf(int) and default Paint
    font-feature settings. Added immutable sRGB color values and default-feature
    queries; custom OpenType feature requests still fail explicitly. Regression
    checks pass, and the real app advances beyond those failures.
15. The current startup failure is missing View.setCameraDistance(float) during
    React Native transform setup. No usable note list or note editing is verified.

## Acceptance still required

Open the app, create and edit an offline note, navigate away and back, and verify
persistence after restart. Synchronization and account workflows are out of this
initial offline experiment. Current outcome: **startup blocked; investigation active**.
