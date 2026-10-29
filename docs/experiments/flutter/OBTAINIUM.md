# Obtainium — Flutter

## Artifact and environment

- Upstream: [Obtainium v1.7.3](https://github.com/ImranR98/Obtainium/releases/tag/v1.7.3).
- Artifact: `app-x86_64-release.apk`, unchanged; version code 23611, min SDK 26.
- SHA-256: `678bb84c6019775e1b318cdef18e5e13764379b431e45f25fa2ffde5650e5f8e`.
- Framework evidence: APK contains `libflutter.so` and `libapp.so` for x86-64.
- Test: 2026-10-28–29; x86-64 Linux VM, software graphics, standalone ART. Source
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
6. Focusing the Add app URL field crashed because `DynamicLayout` was missing.
   Added editable layout reflow using the existing software line breaker, with
   weak change observers and regression coverage for edits and metric spans.
   Retesting accepted typed URL text and displayed the app's related options.
7. Resized that screen to landscape, inspected the relayout and retained text,
   tapped Back using the new dimensions, and returned the home screen to portrait.

8. Added `GET_SIGNING_CERTIFICATES`, `PackageInfo.signingInfo`, and copied
   signer/history queries backed by the existing bounded v2 certificate reader.
   This matches Shiraz's API-27 profile; it does not implement v3 rotation
   history or cryptographically verify APK authenticity. VM regression checks
   cover package-query integration, requested/unrequested metadata, array
   isolation, multi-signer history semantics, and malformed-block rejection.
   The unchanged APK passes its previous unsupported-flags failure, then fails
   on missing `PackageInfo.splitNames`. Added that field with null for the
   currently supported monolithic APKs. Retesting passes it and next fails on
   missing `PackageInfo.sharedUserId` during the background package inventory.

9. Preserved shared-user ID/label, base revision, and install-location metadata
   from the manifest through the launcher and PackageInfo. Exposed monolithic
   split-revision metadata and the non-APEX state. Retesting passes the previous
   shared-user failure and next requires the common package icon field. Added
   icon/logo/banner fields and application resource-ID propagation. Text and
   binary-manifest regressions, launcher transfer checks, and package-query
   integration pass on the VM. The unchanged APK passes those failures and
   next stops at missing `ApplicationInfo.backupAgentName`. These declaration
   fields do not implement Android shared-UID isolation or backup services.

10. Added application declaration metadata for backup/manage-space/factory
    class names, permission, process name, task affinity, category, UI options,
    compile SDK, and screen-width limits. Values pass from manifest parsing
    through launcher metadata into ApplicationInfo; backup services are not
    implemented by these fields. The existing device-protected Context uses
    the same app directory, which is reflected in its metadata. The unchanged
    APK passes the missing fields and reaches the home screen, but package
    inventory dereferences a null storage UUID. Reporting Android's standard
    default-internal-storage UUID fixes that failure; the next retest stops
    at missing `ApplicationInfo.isVirtualPreload()`. VM parser, transfer, and
    package-query regressions cover the added metadata.

11. Added `isVirtualPreload()` with the correct false result for Shiraz's
    directly installed APKs. Retesting passes it and reaches missing `gids`.
    Added flag-dependent Android group metadata: null when not requested and
    an empty array when requested, since Shiraz assigns no Android permission
    groups. The next failure is the missing activity list. Added activity,
    receiver, and provider arrays using existing manifest metadata and query
    flags. VM regressions check requested/unrequested lists, disabled-activity
    inclusion, and independent result objects. The unchanged APK passes those
    failures and next stops at `PackageInfo.instrumentation`.

12. Added bounded instrumentation declaration parsing, launcher transfer, and
    flag-dependent PackageInfo queries, including target package and declared
    profiling/test flags. No instrumentation runner is started. Retesting passes
    that missing field and reaches missing permission metadata. Added declared
    permission names/protection levels and requested permission arrays; granted
    bits follow existing permission checks. VM regressions verify limits,
    target-package transfer, request flags, and that denied permissions are not
    marked granted. The unchanged APK passes these failures and next stops at
    missing `PackageInfo.configPreferences`.

13. Added bounded uses-configuration, uses-feature, and feature-group parsing,
    launcher transfer, and GET_CONFIGURATIONS snapshots. VM tests cover GLES
    requirements, optional/named features, groups, input declarations, limits,
    requested/unrequested metadata, and independent result objects. Retesting
    the unchanged APK reaches the home and Add screens without the preceding
    package-metadata failures. Entering its upstream repository URL displays
    GitHub options. Submitting Add then exposes missing
    `PackageManager.resolveService(Intent, int)` and a Cronet native-link error
    for `ATrace_beginSection`; release retrieval does not complete.

14. Added `resolveService` using the manifest-backed service query and ordered
    results by filter priority; service queries no longer return an activity
    from the camera adapter. VM tests verify priority, explicit-component, and
    no-match behavior. Added the NDK tracing entry points with tracing disabled,
    matching the absence of an Android tracing session. Rebuilt framework and
    native library on the VM, then repeated Add with the same upstream URL.
    Cronet now loads and the prior service-resolution/tracing failures pass.
    Initialization next fails on `ConnectivityManager.getLinkProperties(Network)`
    while querying DNS status. No completed release retrieval is claimed.

15. Added host resolver snapshots for `getLinkProperties`, including numeric
    IPv4/IPv6 DNS servers and search domains, plus default-network enumeration
    through `getAllNetworks`. There is no Android-managed Private DNS service.
    VM regressions cover parsing, malformed input, immutable snapshots, and
    unknown networks; the full check suite passes. Repeating Add in the
    unchanged APK passes both queries. Cronet next stops at the missing
    handler-based `registerNetworkCallback` overload. Release retrieval remains
    unverified.

16. Added handler-based network callback registration. Initial callbacks are
    queued on the supplied handler; unregistering cancels pending delivery,
    duplicate registrations fail, and a stopped handler does not retain a
    registration. This remains a default-network snapshot adapter, not a
    continuous network-change monitor. Repeating Add passes the previous
    missing-method failure. Cronet then aborts loading `NetworkActiveNotifier`;
    VM inspection confirms that the APK contains the class but its implemented
    framework interface, `ConnectivityManager.OnNetworkActiveListener`, is
    missing. No release retrieval completes.

17. Added `OnNetworkActiveListener` and registration/removal APIs. The host has
    no Android radio idle timers, so `isDefaultNetworkActive` reports active;
    this is not an Internet-reachability claim and no radio transitions are
    synthesized. This follows the no-idle-timer behavior in
    [Android's network service](https://android.googlesource.com/platform/frameworks/base/+/30b6573ec7aa09d14f397772d16f3d69ca2617c9/services/core/java/com/android/server/NetworkManagementService.java).
    Full VM checks pass. Repeating Add now retrieves release information and
    displays “Pick an APK” with x86_64 release choices. Selecting the default
    package and Continue proceeds to archive metadata inspection, then crashes
    on missing `PackageManager.getPackageArchiveInfo(String, int)`. A saved app
    entry and installation are not yet verified.

18. Added bounded, read-only package-archive identity inspection using the
    existing binary-XML parser. It reads version information (including resource
    strings), SDK declarations, application class and resource references;
    installation state stays unset. Existing signer-metadata flags are supported,
    but this does not verify APK authenticity. Other archive query flags fail
    explicitly. VM regressions cover metadata, malformed input and unsupported
    flags. The first app retest exposed a null storage UUID during serialization;
    supplying the default storage UUID passes that failure. Full VM checks pass.
    Repeating Add and selecting an x86_64 package now passes archive inspection,
    then crashes on missing `PackageManager.getApplicationIcon(String)`.

19. Added application-icon query overloads and `PackageItemInfo.loadIcon`.
    VM regressions verify declared bitmap loading, independent drawable instances,
    unknown-package failure, and a neutral host fallback for applications without
    a usable icon. Full VM checks pass. The unchanged APK now completes Add and
    reaches its details screen with version/release information. The entry remains
    in the app list after restart. Its adaptive-icon XML still raises a handled
    plugin error; the visible app-provided image does not prove adaptive-icon
    support. Opening the saved entry and resizing to landscape produces a white
    surface, which remains white after returning to portrait. This later flow
    does not inherit the earlier settings-screen resize result.

## Current result and limits

**Release retrieval, Add, and app-list persistence verified; not a full
compatibility pass.** Package metadata and icon-query missing-method failures
are passed. Adaptive-icon XML remains unsupported, and the saved-entry resize
flow produced a persistent white surface. Installation, updates, certificate
verification, and complete plugin behavior are not verified. Do not infer that
every Flutter app works from this result.
