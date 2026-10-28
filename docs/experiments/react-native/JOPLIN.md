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
   interoperability failure remains open. At that stage no Joplin UI or note editing was verified.
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
15. Added density-scaled camera-distance state for planar transforms. Perspective
    rotations remain unsupported. ART regression covers density changes and
    invalid input.
16. React Native attaches metadata spans to text. TextView now retains those
    markers while continuing to reject unsupported paragraph styles. The actual
    app renders its Welcome notebook and notes list.
17. Tapping Add required optional UI sound requests and planar View rotation.
    UI cues are muted; rotation now affects both rendering and inverse input
    coordinates. ART regressions cover transformed pixels, touches, and existing
    multitouch behavior. The real Add menu opens and its icon rotates.
18. New note uses amplified font sizes for cap-height measurement. Raised the
    bounded measurement range and added pre-allocation glyph coverage checks.
    ART regression verifies scaled bounds and rejection of oversized raster work.
    Retesting passes this measurement and reaches Layout.getLineBounds.
19. Added full-width line bounds and baseline reporting. ART layout regressions
    pass. Opening a note then required QwertyKeyListener. Added the desktop committed-text
    bridge; ART tests verify Unicode deletion, navigation, and no duplicate
    insertion from physical key-down events.
20. Added selection/insertion action-mode callback registration, configurable
    selection highlight color, and access to the actual text Paint flags.
    Floating Android selection toolbars remain unsupported. ART editor regression
    covers custom highlight pixels and transparent selection alongside existing
    input and reflow behavior. Retesting then needed widget font-feature queries
    and selectable-text setup on attachment. Added default font-feature access
    and selection policy for editable widgets, retaining the explicit failure
    for read-only selection. ART editing tests pass. Retesting now reaches a
    React Native CustomLineHeightSpan rejected by TextView's paragraph-style
    validation. Per-line height rendering is the next runtime issue; note editing
    and persistence are still not verified.

## Acceptance still required

Open the app, create and edit an offline note, navigate away and back, and verify
persistence after restart. Synchronization and account workflows are out of this
initial offline experiment. Current outcome: **notes list and Add menu render; note creation blocked**.
