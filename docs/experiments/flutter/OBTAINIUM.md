# Obtainium — Flutter

## Artifact and environment

- Upstream: [Obtainium v1.7.3](https://github.com/ImranR98/Obtainium/releases/tag/v1.7.3).
- Artifact: `app-x86_64-release.apk`, unchanged; version code 23611, min SDK 26.
- SHA-256: `678bb84c6019775e1b318cdef18e5e13764379b431e45f25fa2ffde5650e5f8e`.
- Framework evidence: APK contains `libflutter.so` and `libapp.so` for x86-64.
- Test: 2026-10-28; x86-64 Linux VM, software graphics, standalone ART. Source
  baseline `0b59ef2` plus the fixes described below. No ARM translation used.

## Reproduce, fix, retest

1. The unmodified app initially stopped in FileProvider initialization with
   `SecurityException: Provider must not be exported`.
2. ProviderHost constructed incomplete metadata and treated an omitted exported
   attribute as true. It now uses PackageManager's full provider metadata;
   PackageManager preserves the pre-17 default and the newer non-exported default.
   Retesting passed provider initialization and reached Flutter engine loading.
3. Native asset symbols existed in Shiraz's resource library but were not visible
   to the native linker. Preloading the platform/resource bridges into ART's
   process scope allowed the Flutter engine to load.
4. Observed the real **Apps / No apps / Add** home screen and an app-provided
   informational dialog. Using normal live touch input, opened Settings, then
   Appearance, and changed the theme to dark. Each resulting app screen was
   captured and inspected. Closing the window completed runtime cleanup.
5. Restarted the app and inspected the home screen: the selected dark theme
   persisted. This verifies that setting only, not every persisted app workflow.

## Current result and limits

**Interactive settings; not a full compatibility pass.** Background work reports unsupported
package-query flags (`134217728`, GET_SIGNING_CERTIFICATES). App installation,
updates, certificate handling, app-list persistence, and complete plugin behavior
are not verified. Do not infer that every Flutter app works from this result.
