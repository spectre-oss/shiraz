# Missing Android libraries

Generated 2026-11-13 from the working tree at `7599b42` (VERSION 0.347.0, with uncommitted framework changes) against the pinned Android 15 / API 35 SDK jar (`platform-35_r02.zip`).

"Missing" means the class name is absent from `build/framework/classes`. A class being present says nothing about its methods or behavior, so the "partly present" packages below are not working packages. `android.R` resource classes are excluded. `java.*` and `javax.*` come from the ART core libraries, not this tree, and are not measured here.

**941 of 4,445 `android.*` class names present (21.2%); 3,504 missing.** The compatibility page snapshot (0.338.0) showed 933 of 4,445.

Sections: [framework areas](#1-framework-areas) · [packages with nothing](#2-packages-with-no-classes-at-all) · [packages partly present](#3-packages-partly-present) · [other namespaces and optional jars](#4-other-namespaces-and-optional-sdk-jars) · [NDK libraries](#5-ndk-native-libraries) · [every missing class](#6-every-missing-class)

## 1. Framework areas

Sorted by number of missing classes.

| Area | Present | Reference | Missing |
|---|--:|--:|--:|
| `android.media` | 88 | 436 | 348 |
| `android.net` | 25 | 317 | 292 |
| `android.app` | 54 | 343 | 289 |
| `android.view` | 161 | 397 | 236 |
| `android.icu` | 0 | 214 | 214 |
| `android.provider` | 19 | 232 | 213 |
| `android.health` | 0 | 182 | 182 |
| `android.telephony` | 5 | 179 | 174 |
| `android.adservices` | 0 | 145 | 145 |
| `android.service` | 4 | 146 | 142 |
| `android.hardware` | 19 | 138 | 119 |
| `android.widget` | 83 | 189 | 106 |
| `android.content` | 74 | 178 | 104 |
| `android.graphics` | 101 | 197 | 96 |
| `android.os` | 62 | 143 | 81 |
| `android.renderscript` | 0 | 73 | 73 |
| `android.text` | 88 | 159 | 71 |
| `android.bluetooth` | 4 | 64 | 60 |
| `android.security` | 3 | 59 | 56 |
| `android.telecom` | 5 | 45 | 40 |
| `android.webkit` | 21 | 60 | 39 |
| `android.util` | 30 | 67 | 37 |
| `android.location` | 5 | 41 | 36 |
| `android.nfc` | 2 | 32 | 30 |
| `android.accessibilityservice` | 0 | 25 | 25 |
| `android.preference` | 1 | 24 | 23 |
| `android.transition` | 3 | 26 | 23 |
| `android.drm` | 0 | 22 | 22 |
| `android.print` | 0 | 22 | 22 |
| `android.database` | 37 | 58 | 21 |
| `android.companion` | 0 | 19 | 19 |
| `android.credentials` | 0 | 19 | 19 |
| `android.opengl` | 17 | 36 | 19 |
| `android.speech` | 6 | 25 | 19 |
| `android.system` | 0 | 14 | 14 |
| `android.gesture` | 0 | 13 | 13 |
| `android.accounts` | 2 | 13 | 11 |
| `android.inputmethodservice` | 2 | 13 | 11 |
| `android.animation` | 19 | 29 | 10 |
| `android.window` | 0 | 10 | 10 |
| `android.mtp` | 0 | 7 | 7 |
| `android.sax` | 0 | 7 | 7 |
| `android.annotation` | 0 | 5 | 5 |
| `android.printservice` | 0 | 5 | 5 |
| `android.se` | 0 | 5 | 5 |
| `android.appwidget` | 1 | 5 | 4 |
| `android` | 0 | 3 | 3 |
| `android.crypto` | 0 | 2 | 2 |
| `android.devicelock` | 0 | 2 | 2 |

## 2. Packages with no classes at all

127 of 186 packages, 1,762 classes.

| Package | Classes |
|---|--:|
| `android.health.connect.datatypes` | 143 |
| `android.icu.text` | 118 |
| `android.renderscript` | 73 |
| `android.app.appsearch` | 66 |
| `android.adservices.ondevicepersonalization` | 53 |
| `android.media.tv` | 50 |
| `android.service.autofill` | 45 |
| `android.view.textclassifier` | 44 |
| `android.icu.util` | 41 |
| `android.net.ipsec.ike` | 41 |
| `android.media.audiofx` | 39 |
| `android.app.admin` | 30 |
| `android.hardware.camera2` | 30 |
| `android.adservices.adselection` | 29 |
| `android.icu.number` | 28 |
| `android.net.wifi.p2p` | 26 |
| `android.security.identity` | 26 |
| `android.accessibilityservice` | 25 |
| `android.hardware.camera2.params` | 25 |
| `android.icu.lang` | 25 |
| `android.net.wifi.aware` | 25 |
| `android.os.strictmode` | 23 |
| `android.drm` | 22 |
| `android.media.metrics` | 22 |
| `android.telephony.mbms` | 22 |
| `android.health.connect` | 21 |
| `android.print` | 21 |
| `android.adservices.customaudience` | 20 |
| `android.view.translation` | 20 |
| `android.credentials` | 19 |
| `android.service.credentials` | 17 |
| `android.companion` | 16 |
| `android.hardware.biometrics` | 16 |
| `android.app.usage` | 15 |
| `android.speech` | 15 |
| `android.adservices.common` | 14 |
| `android.media.midi` | 14 |
| `android.service.voice` | 14 |
| `android.system` | 14 |
| `android.telephony.ims` | 14 |
| `android.adservices.measurement` | 13 |
| `android.gesture` | 13 |
| `android.net.eap` | 13 |
| `android.graphics.pdf` | 12 |
| `android.health.connect.datatypes.units` | 11 |
| `android.net.sip` | 11 |
| `android.net.vcn` | 11 |
| `android.nfc.tech` | 11 |
| `android.view.contentcapture` | 11 |
| `android.graphics.text` | 10 |
| `android.net.ipsec.ike.exceptions` | 10 |
| `android.net.wifi.rtt` | 10 |
| `android.view.inspector` | 10 |
| `android.window` | 10 |
| `android.hardware.usb` | 9 |
| `android.media.tv.interactive` | 9 |
| `android.service.carrier` | 9 |
| `android.service.chooser` | 9 |
| `android.service.quickaccesswallet` | 9 |
| `android.telephony.data` | 9 |
| `android.net.nsd` | 8 |
| `android.os.health` | 8 |
| `android.service.controls.templates` | 8 |
| `android.adservices.topics` | 7 |
| `android.app.sdksandbox` | 7 |
| `android.app.slice` | 7 |
| `android.hardware.lights` | 7 |
| `android.health.connect.changelog` | 7 |
| `android.mtp` | 7 |
| `android.sax` | 7 |
| `android.graphics.pdf.models` | 6 |
| `android.net.wifi.p2p.nsd` | 6 |
| `android.nfc.cardemulation` | 6 |
| `android.annotation` | 5 |
| `android.app.appsearch.observer` | 5 |
| `android.content.om` | 5 |
| `android.graphics.pdf.content` | 5 |
| `android.net.wifi.hotspot2.pps` | 5 |
| `android.printservice` | 5 |
| `android.se.omapi` | 5 |
| `android.service.controls` | 5 |
| `android.service.controls.actions` | 5 |
| `android.app.assist` | 4 |
| `android.hardware.fingerprint` | 4 |
| `android.media.effect` | 4 |
| `android.media.projection` | 4 |
| `android.net.rtp` | 4 |
| `android.telephony.euicc` | 4 |
| `android.view.displayhash` | 4 |
| `android.widget.inline` | 4 |
| `android` | 3 |
| `android.adservices.signals` | 3 |
| `android.app.blob` | 3 |
| `android.app.people` | 3 |
| `android.companion.virtual` | 3 |
| `android.content.res.loader` | 3 |
| `android.adservices.adid` | 2 |
| `android.adservices.appsetid` | 2 |
| `android.app.sdksandbox.sdkprovider` | 2 |
| `android.content.pm.verify.domain` | 2 |
| `android.crypto.hpke` | 2 |
| `android.devicelock` | 2 |
| `android.graphics.pdf.models.selection` | 2 |
| `android.icu.math` | 2 |
| `android.location.provider` | 2 |
| `android.net.wifi.hotspot2` | 2 |
| `android.service.quicksettings` | 2 |
| `android.service.textservice` | 2 |
| `android.service.wallpaper` | 2 |
| `android.telephony.ims.feature` | 2 |
| `android.adservices` | 1 |
| `android.adservices.exceptions` | 1 |
| `android.app.appsearch.exceptions` | 1 |
| `android.app.appsearch.util` | 1 |
| `android.app.role` | 1 |
| `android.location.altitude` | 1 |
| `android.net.wifi.hotspot2.omadm` | 1 |
| `android.os.ext` | 1 |
| `android.print.pdf` | 1 |
| `android.service.assist.classification` | 1 |
| `android.service.dreams` | 1 |
| `android.service.persistentdata` | 1 |
| `android.service.restrictions` | 1 |
| `android.service.vr` | 1 |
| `android.telephony.emergency` | 1 |
| `android.telephony.ims.stub` | 1 |
| `android.util.proto` | 1 |

## 3. Packages partly present

58 packages, 1,742 classes missing.

| Package | Present | Reference | Missing |
|---|--:|--:|--:|
| `android.provider` | 19 | 232 | 213 |
| `android.media` | 73 | 272 | 199 |
| `android.app` | 40 | 178 | 138 |
| `android.telephony` | 3 | 120 | 117 |
| `android.widget` | 83 | 185 | 102 |
| `android.view` | 107 | 188 | 81 |
| `android.graphics` | 62 | 113 | 51 |
| `android.net` | 21 | 71 | 50 |
| `android.content` | 37 | 85 | 48 |
| `android.os` | 60 | 107 | 47 |
| `android.content.pm` | 25 | 68 | 43 |
| `android.view.inputmethod` | 12 | 55 | 43 |
| `android.bluetooth` | 1 | 42 | 41 |
| `android.telecom` | 5 | 45 | 40 |
| `android.webkit` | 21 | 60 | 39 |
| `android.net.wifi` | 2 | 39 | 37 |
| `android.util` | 30 | 66 | 36 |
| `android.location` | 5 | 38 | 33 |
| `android.net.http` | 1 | 32 | 31 |
| `android.text.style` | 30 | 61 | 31 |
| `android.text` | 39 | 64 | 25 |
| `android.preference` | 1 | 24 | 23 |
| `android.transition` | 3 | 26 | 23 |
| `android.hardware` | 15 | 36 | 21 |
| `android.bluetooth.le` | 3 | 22 | 19 |
| `android.opengl` | 17 | 36 | 19 |
| `android.security` | 1 | 18 | 17 |
| `android.nfc` | 2 | 15 | 13 |
| `android.security.keystore` | 2 | 15 | 13 |
| `android.view.accessibility` | 10 | 23 | 13 |
| `android.text.method` | 13 | 25 | 12 |
| `android.accounts` | 2 | 13 | 11 |
| `android.database` | 16 | 27 | 11 |
| `android.inputmethodservice` | 2 | 13 | 11 |
| `android.animation` | 19 | 29 | 10 |
| `android.database.sqlite` | 21 | 31 | 10 |
| `android.service.notification` | 1 | 10 | 9 |
| `android.graphics.drawable` | 29 | 36 | 7 |
| `android.hardware.display` | 2 | 8 | 6 |
| `android.media.session` | 11 | 17 | 6 |
| `android.view.textservice` | 5 | 10 | 5 |
| `android.appwidget` | 1 | 5 | 4 |
| `android.speech.tts` | 6 | 10 | 4 |
| `android.telephony.gsm` | 1 | 5 | 4 |
| `android.app.backup` | 8 | 11 | 3 |
| `android.app.job` | 6 | 9 | 3 |
| `android.content.res` | 12 | 15 | 3 |
| `android.view.animation` | 23 | 26 | 3 |
| `android.graphics.drawable.shapes` | 4 | 6 | 2 |
| `android.os.storage` | 2 | 4 | 2 |
| `android.text.util` | 3 | 5 | 2 |
| `android.view.autofill` | 4 | 6 | 2 |
| `android.graphics.fonts` | 6 | 7 | 1 |
| `android.hardware.input` | 2 | 3 | 1 |
| `android.media.browse` | 4 | 5 | 1 |
| `android.net.ssl` | 1 | 2 | 1 |
| `android.service.media` | 3 | 4 | 1 |
| `android.text.format` | 3 | 4 | 1 |

Every class name present in 1 packages: `android.telephony.cdma`.

## 4. Other namespaces and optional SDK jars

Non-`android.*` packages shipped in the SDK `android.jar`, matched against the same build directory. A zero here can also mean the runtime supplies the package from somewhere other than `build/framework/classes`; that was not checked.

| Package | Present | Reference |
|---|--:|--:|
| `androidx.annotation` | 0 | 2 |
| `dalvik.annotation` | 0 | 2 |
| `dalvik.annotation.optimization` | 0 | 2 |
| `dalvik.bytecode` | 0 | 2 |
| `dalvik.system` | 0 | 10 |
| `org.apache.http.conn` | 0 | 1 |
| `org.apache.http.conn.scheme` | 0 | 3 |
| `org.apache.http.conn.ssl` | 0 | 6 |
| `org.apache.http.params` | 0 | 3 |
| `org.json` | 0 | 5 |
| `org.w3c.dom` | 0 | 28 |
| `org.w3c.dom.ls` | 0 | 8 |
| `org.xml.sax` | 0 | 17 |
| `org.xml.sax.ext` | 0 | 8 |
| `org.xml.sax.helpers` | 0 | 10 |
| `org.xmlpull.v1` | 0 | 4 |
| `org.xmlpull.v1.sax2` | 0 | 1 |

Optional SDK libraries (apps request them with `<uses-library>`):

| Jar | Present | Reference |
|---|--:|--:|
| `android.car.jar` | 0 | 82 |
| `android.test.base.jar` | 0 | 24 |
| `android.test.mock.jar` | 0 | 10 |
| `android.test.runner.jar` | 0 | 29 |
| `org.apache.http.legacy.jar` | 0 | 407 |
| `uiautomator.jar` | 0 | 10 |

Not covered: AndroidX, Material, Play services and other libraries that apps bundle in their own DEX, and Google proprietary APIs (`com.google.android.gms.*`).

## 5. NDK native libraries

Evidence here is weaker than for the class list. It comes from the exported symbols of the macOS build (`nm -gU build/macos/libandroid.so` and `libjnigraphics.so`) plus a read of `native/`, `Makefile` and `mk/`. No Linux build output was on this machine, and no NDK sysroot with library map files is in the tree (`.deps/bionic-headers` holds bionic libc headers only), so there is no per-function comparison against the NDK. The library list is the NDK stable set written from memory; check it against an NDK sysroot before relying on it.

### Libraries with no source or build target

| Library | API | Typical users |
|---|---|---|
| `libvulkan.so` | Vulkan | Game engines (Unity, Godot, Unreal), Skia and Flutter Impeller Vulkan backends |
| `libaaudio.so` | AAudio (`AAudio*`) | Oboe, game engines, low-latency audio apps |
| `libcamera2ndk.so` | Camera2 NDK (`ACamera*`) | Camera and scanner apps |
| `libamidi.so` | Native MIDI (`AMidi*`) | Music apps |
| `libOpenMAXAL.so` | OpenMAX AL | Legacy media players |
| `libneuralnetworks.so` | NNAPI (`ANeuralNetworks*`) | TensorFlow Lite delegates |
| `libbinder_ndk.so` | NDK Binder (`AIBinder*`) | Apps with native AIDL services |
| `libicu.so` | ICU4C (`u_*`, `ubrk_*`, …) | Apps using NDK ICU; no target with this name found |

### Library names with no file of their own

| Library | State |
|---|---|
| `libmediandk.so` | `AMediaCodec`, `AMediaFormat`, `AMediaCrypto`, `AMediaDrm` and `AImageReader` symbols are exported from `libandroid.so`. No build target or symlink named `libmediandk.so` was found, unlike `libnativewindow.so`, which is symlinked. Whether the guest loader maps the name was not checked. |
| `libsync.so` | `sync_*` symbols (2) are exported from `libandroid.so`. The only `libsync.so` target is a macOS guest test fixture. |

### API families missing from libraries that exist

Exported from `libandroid.so` in the macOS build, by symbol count: `AMediaCodec` 18, `AMediaFormat` 13, `AImageReader` 13, `AMediaDrm` 12, `AImage` 12, `ANativeWindow` 10, `AMediaCodecCryptoInfo` 9, `AHardwareBuffer` 9, `ALooper` 8, `ASensorManager` 7, `AFont` 7, `ATrace` 6, `ASensorEventQueue` 5, `ASensor` 5, `AChoreographer` 5, `AMediaCrypto` 4, `ASystemFontIterator` 3, `ASharedMemory` 3. `libjnigraphics.so` exports `AndroidBitmap_getInfo`, `_lockPixels` and `_unlockPixels` only.

Not exported there, and no definition found in `native/`:

| Family | Header |
|---|---|
| `AAsset_*`, `AAssetManager_*`, `AAssetDir_*` | `asset_manager.h`. Exception to "no definition": the names appear in `native/resources.cpp` (`libshiraz-resources.so`), but `libandroid.so` does not export them, so an app linking `libandroid.so` for assets may not resolve them. Not verified. |
| `ANativeActivity_*`, `AInputEvent_*`, `AInputQueue_*`, `AKeyEvent_*`, `AMotionEvent_*` | `native_activity.h`, `input.h` (needed by `NativeActivity` and `GameActivity` apps) |
| `AConfiguration_*` | `configuration.h` |
| `AStorageManager_*`, `AObbInfo_*` | `storage_manager.h`, `obb.h` |
| `ASurfaceControl_*`, `ASurfaceTransaction_*` | `surface_control.h` |
| `ASurfaceTexture_*` | `surface_texture.h` |
| `AImageDecoder_*` | `imagedecoder.h` |
| `APerformanceHint_*`, `AThermal_*` | `performance_hint.h`, `thermal.h` |
| `AMediaExtractor_*`, `AMediaMuxer_*`, `AMediaDataSource_*` | `media/NdkMedia*.h` |
| `AndroidBitmap_compress`, `_getDataSpace`, `_getHardwareBuffer` | `bitmap.h` |

### Present, coverage not measured

`libOpenSLES.so`, `libEGL.so`, `libGLESv1_CM.so`, `libGLESv2.so`, `libGLESv3.so`, `liblog.so`, `libnativehelper.so`, and the bionic set `libc.so`, `libm.so`, `libdl.so`, `libz.so`, `libstdc++.so` are referenced by build targets or proxies. Which of them resolve on each host (Linux, macOS guest) was not verified; `build/macos` has no `libOpenSLES` or `libGLESv3` file.


## 6. Every missing class

Nested classes appear as `Outer$Inner`.

<details><summary><code>android</code>: 3 of 3 missing</summary>

`Manifest$permission`, `Manifest$permission_group`, `Manifest`

</details>

<details><summary><code>android.accessibilityservice</code>: 25 of 25 missing</summary>

`AccessibilityButtonController$AccessibilityButtonCallback`, `AccessibilityButtonController`, `AccessibilityGestureEvent`, `AccessibilityService$GestureResultCallback`, `AccessibilityService$MagnificationController$OnMagnificationChangedListener`, `AccessibilityService$MagnificationController`, `AccessibilityService$ScreenshotResult`, `AccessibilityService$SoftKeyboardController$OnShowModeChangedListener`, `AccessibilityService$SoftKeyboardController`, `AccessibilityService$TakeScreenshotCallback`, `AccessibilityService`, `AccessibilityServiceInfo`, `BrailleDisplayController$BrailleDisplayCallback`, `BrailleDisplayController`, `FingerprintGestureController$FingerprintGestureCallback`, `FingerprintGestureController`, `GestureDescription$Builder`, `GestureDescription$StrokeDescription`, `GestureDescription`, `InputMethod$AccessibilityInputConnection`, `InputMethod`, `MagnificationConfig$Builder`, `MagnificationConfig`, `TouchInteractionController$Callback`, `TouchInteractionController`

</details>

<details><summary><code>android.accounts</code>: 11 of 13 missing</summary>

`AbstractAccountAuthenticator`, `AccountAuthenticatorActivity`, `AccountAuthenticatorResponse`, `AccountManagerCallback`, `AccountManagerFuture`, `AccountsException`, `AuthenticatorDescription`, `AuthenticatorException`, `NetworkErrorException`, `OnAccountsUpdateListener`, `OperationCanceledException`

</details>

<details><summary><code>android.adservices</code>: 1 of 1 missing</summary>

`AdServicesState`

</details>

<details><summary><code>android.adservices.adid</code>: 2 of 2 missing</summary>

`AdId`, `AdIdManager`

</details>

<details><summary><code>android.adservices.adselection</code>: 29 of 29 missing</summary>

`AdSelectionConfig$Builder`, `AdSelectionConfig`, `AdSelectionFromOutcomesConfig$Builder`, `AdSelectionFromOutcomesConfig`, `AdSelectionManager`, `AdSelectionOutcome$Builder`, `AdSelectionOutcome`, `AdWithBid`, `AddAdSelectionFromOutcomesOverrideRequest`, `AddAdSelectionOverrideRequest`, `DecisionLogic`, `GetAdSelectionDataOutcome`, `GetAdSelectionDataRequest$Builder`, `GetAdSelectionDataRequest`, `PerBuyerDecisionLogic`, `PersistAdSelectionResultRequest$Builder`, `PersistAdSelectionResultRequest`, `RemoveAdSelectionFromOutcomesOverrideRequest`, `RemoveAdSelectionOverrideRequest`, `ReportEventRequest$Builder`, `ReportEventRequest`, `ReportImpressionRequest`, `SetAppInstallAdvertisersRequest$Builder`, `SetAppInstallAdvertisersRequest`, `SignedContextualAds$Builder`, `SignedContextualAds`, `TestAdSelectionManager`, `UpdateAdCounterHistogramRequest$Builder`, `UpdateAdCounterHistogramRequest`

</details>

<details><summary><code>android.adservices.appsetid</code>: 2 of 2 missing</summary>

`AppSetId`, `AppSetIdManager`

</details>

<details><summary><code>android.adservices.common</code>: 14 of 14 missing</summary>

`AdData$Builder`, `AdData`, `AdFilters$Builder`, `AdFilters`, `AdSelectionSignals`, `AdServicesOutcomeReceiver`, `AdServicesPermissions`, `AdTechIdentifier`, `AppInstallFilters$Builder`, `AppInstallFilters`, `FrequencyCapFilters$Builder`, `FrequencyCapFilters`, `KeyedFrequencyCap$Builder`, `KeyedFrequencyCap`

</details>

<details><summary><code>android.adservices.customaudience</code>: 20 of 20 missing</summary>

`AddCustomAudienceOverrideRequest$Builder`, `AddCustomAudienceOverrideRequest`, `CustomAudience$Builder`, `CustomAudience`, `CustomAudienceManager`, `FetchAndJoinCustomAudienceRequest$Builder`, `FetchAndJoinCustomAudienceRequest`, `JoinCustomAudienceRequest$Builder`, `JoinCustomAudienceRequest`, `LeaveCustomAudienceRequest$Builder`, `LeaveCustomAudienceRequest`, `PartialCustomAudience$Builder`, `PartialCustomAudience`, `RemoveCustomAudienceOverrideRequest$Builder`, `RemoveCustomAudienceOverrideRequest`, `ScheduleCustomAudienceUpdateRequest$Builder`, `ScheduleCustomAudienceUpdateRequest`, `TestCustomAudienceManager`, `TrustedBiddingData$Builder`, `TrustedBiddingData`

</details>

<details><summary><code>android.adservices.exceptions</code>: 1 of 1 missing</summary>

`AdServicesException`

</details>

<details><summary><code>android.adservices.measurement</code>: 13 of 13 missing</summary>

`DeletionRequest$Builder`, `DeletionRequest`, `MeasurementManager`, `SourceRegistrationRequest$Builder`, `SourceRegistrationRequest`, `WebSourceParams$Builder`, `WebSourceParams`, `WebSourceRegistrationRequest$Builder`, `WebSourceRegistrationRequest`, `WebTriggerParams$Builder`, `WebTriggerParams`, `WebTriggerRegistrationRequest$Builder`, `WebTriggerRegistrationRequest`

</details>

<details><summary><code>android.adservices.ondevicepersonalization</code>: 53 of 53 missing</summary>

`AppInfo`, `DownloadCompletedInput`, `DownloadCompletedOutput$Builder`, `DownloadCompletedOutput`, `EventInput`, `EventLogRecord$Builder`, `EventLogRecord`, `EventOutput$Builder`, `EventOutput`, `EventUrlProvider`, `ExecuteInput`, `ExecuteOutput$Builder`, `ExecuteOutput`, `FederatedComputeInput$Builder`, `FederatedComputeInput`, `FederatedComputeScheduler$Params`, `FederatedComputeScheduler`, `InferenceInput$Builder`, `InferenceInput$Params$Builder`, `InferenceInput$Params`, `InferenceInput`, `InferenceOutput$Builder`, `InferenceOutput`, `IsolatedService`, `IsolatedServiceException`, `IsolatedWorker`, `KeyValueStore`, `LogReader`, `ModelManager`, `MutableKeyValueStore`, `OnDevicePersonalizationException`, `OnDevicePersonalizationManager$ExecuteResult`, `OnDevicePersonalizationManager`, `RenderInput`, `RenderOutput$Builder`, `RenderOutput`, `RenderingConfig$Builder`, `RenderingConfig`, `RequestLogRecord$Builder`, `RequestLogRecord`, `RequestToken`, `SurfacePackageToken`, `TrainingExampleRecord$Builder`, `TrainingExampleRecord`, `TrainingExamplesInput`, `TrainingExamplesOutput$Builder`, `TrainingExamplesOutput`, `TrainingInterval$Builder`, `TrainingInterval`, `UserData`, `WebTriggerInput`, `WebTriggerOutput$Builder`, `WebTriggerOutput`

</details>

<details><summary><code>android.adservices.signals</code>: 3 of 3 missing</summary>

`ProtectedSignalsManager`, `UpdateSignalsRequest$Builder`, `UpdateSignalsRequest`

</details>

<details><summary><code>android.adservices.topics</code>: 7 of 7 missing</summary>

`EncryptedTopic`, `GetTopicsRequest$Builder`, `GetTopicsRequest`, `GetTopicsResponse$Builder`, `GetTopicsResponse`, `Topic`, `TopicsManager`

</details>

<details><summary><code>android.animation</code>: 10 of 29 missing</summary>

`Animator$AnimatorPauseListener`, `BidirectionalTypeConverter`, `FloatArrayEvaluator`, `IntArrayEvaluator`, `Keyframe`, `PointFEvaluator`, `RectEvaluator`, `TimeAnimator$TimeListener`, `TimeAnimator`, `ValueAnimator$DurationScaleChangeListener`

</details>

<details><summary><code>android.annotation</code>: 5 of 5 missing</summary>

`FlaggedApi`, `NonNull`, `Nullable`, `SuppressLint`, `TargetApi`

</details>

<details><summary><code>android.app</code>: 138 of 178 missing</summary>

`ActionBar$OnMenuVisibilityListener`, `ActionBar$OnNavigationListener`, `ActionBar$Tab`, `ActionBar$TabListener`, `Activity$ScreenCaptureCallback`, `ActivityGroup`, `ActivityManager$AppTask`, `ActivityManager$ProcessErrorStateInfo`, `ActivityManager$RecentTaskInfo`, `ActivityManager$RunningServiceInfo`, `ActivityManager$RunningTaskInfo`, `ActivityManager$TaskDescription$Builder`, `ActivityOptions`, `AlarmManager$OnAlarmListener`, `AliasActivity`, `AppComponentFactory`, `AppOpsManager$OnOpActiveChangedListener`, `AppOpsManager$OnOpNotedCallback`, `Application$OnProvideAssistDataListener`, `ApplicationErrorReport$AnrInfo`, `ApplicationErrorReport$BatteryInfo`, `ApplicationErrorReport$CrashInfo`, `ApplicationErrorReport$RunningServiceInfo`, `ApplicationErrorReport`, `ApplicationExitInfo`, `ApplicationStartInfo`, `AsyncNotedAppOp`, `AuthenticationRequiredException`, `AutomaticZenRule$Builder`, `AutomaticZenRule`, `BackgroundServiceStartNotAllowedException`, `BroadcastOptions`, `ComponentCaller`, `DatePickerDialog$OnDateSetListener`, `DatePickerDialog`, `DialogFragment`, `DirectAction$Builder`, `DirectAction`, `ExpandableListActivity`, `ForegroundServiceStartNotAllowedException`, `ForegroundServiceTypeException`, `Fragment$InstantiationException`, `Fragment$SavedState`, `FragmentBreadCrumbs$OnBreadCrumbClickListener`, `FragmentBreadCrumbs`, `FragmentContainer`, `FragmentController`, `FragmentHostCallback`, `FragmentManager$BackStackEntry`, `FragmentManager$FragmentLifecycleCallbacks`, `FragmentManager$OnBackStackChangedListener`, `FragmentManagerNonConfig`, `GameManager`, `GameState`, `GrammaticalInflectionManager`, `Instrumentation$ActivityMonitor`, `Instrumentation$ActivityResult`, `Instrumentation`, `IntentService`, `InvalidForegroundServiceTypeException`, `KeyguardManager$KeyguardLockedStateListener`, `KeyguardManager$OnKeyguardExitResult`, `LauncherActivity$IconResizer`, `LauncherActivity$ListItem`, `LauncherActivity`, `ListActivity`, `ListFragment`, `LoaderManager$LoaderCallbacks`, `LoaderManager`, `LocalActivityManager`, `LocaleConfig`, `LocaleManager`, `MediaRouteActionProvider`, `MediaRouteButton`, `MissingForegroundServiceTypeException`, `Notification$Action$Extender`, `Notification$Action$WearableExtender`, `Notification$BigPictureStyle`, `Notification$BubbleMetadata$Builder`, `Notification$BubbleMetadata`, `Notification$CallStyle`, `Notification$CarExtender$Builder`, `Notification$CarExtender$UnreadConversation`, `Notification$CarExtender`, `Notification$DecoratedCustomViewStyle`, `Notification$DecoratedMediaCustomViewStyle`, `Notification$Extender`, `Notification$InboxStyle`, `Notification$MessagingStyle$Message`, `Notification$MessagingStyle`, `Notification$TvExtender`, `Notification$WearableExtender`, `NotificationChannelGroup`, `NotificationManager$Policy`, `PendingIntent$OnFinished`, `Person$Builder`, `Person`, `PictureInPictureParams$Builder`, `PictureInPictureParams`, `PictureInPictureUiState`, `Presentation`, `RecoverableSecurityException`, `RemoteAction`, `RemoteInput$Builder`, `RemoteInput`, `SearchManager$OnCancelListener`, `SearchManager$OnDismissListener`, `SearchManager`, `SearchableInfo`, `ServiceStartNotAllowedException`, `SharedElementCallback$OnSharedElementsReadyListener`, `SharedElementCallback`, `StartForegroundCalledOnStoppedServiceException`, `StatusBarManager`, `SyncNotedAppOp`, `TabActivity`, `TaskInfo`, `TaskStackBuilder`, `TimePickerDialog$OnTimeSetListener`, `TimePickerDialog`, `UiAutomation$AccessibilityEventFilter`, `UiAutomation$OnAccessibilityEventListener`, `UiAutomation`, `UiModeManager$ContrastChangeListener`, `VoiceInteractor$AbortVoiceRequest`, `VoiceInteractor$CommandRequest`, `VoiceInteractor$CompleteVoiceRequest`, `VoiceInteractor$ConfirmationRequest`, `VoiceInteractor$PickOptionRequest$Option`, `VoiceInteractor$PickOptionRequest`, `VoiceInteractor$Prompt`, `VoiceInteractor$Request`, `VoiceInteractor`, `WallpaperColors`, `WallpaperInfo`, `WallpaperManager$OnColorsChangedListener`, `WallpaperManager`, `ZygotePreload`

</details>

<details><summary><code>android.app.admin</code>: 30 of 30 missing</summary>

`ConnectEvent`, `DelegatedAdminReceiver`, `DeviceAdminInfo`, `DeviceAdminReceiver`, `DeviceAdminService`, `DevicePolicyIdentifiers`, `DevicePolicyManager$InstallSystemUpdateCallback`, `DevicePolicyManager$OnClearApplicationUserDataListener`, `DevicePolicyManager`, `DevicePolicyResources`, `DevicePolicyResourcesManager`, `DnsEvent`, `FactoryResetProtectionPolicy$Builder`, `FactoryResetProtectionPolicy`, `FreezePeriod`, `ManagedSubscriptionsPolicy`, `NetworkEvent`, `PackagePolicy`, `PolicyUpdateReceiver`, `PolicyUpdateResult`, `PreferentialNetworkServiceConfig$Builder`, `PreferentialNetworkServiceConfig`, `SecurityLog$SecurityEvent`, `SecurityLog`, `SystemUpdateInfo`, `SystemUpdatePolicy$ValidationFailedException`, `SystemUpdatePolicy`, `TargetUser`, `UnsafeStateException`, `WifiSsidPolicy`

</details>

<details><summary><code>android.app.appsearch</code>: 66 of 66 missing</summary>

`AppSearchBatchResult$Builder`, `AppSearchBatchResult`, `AppSearchManager$SearchContext$Builder`, `AppSearchManager$SearchContext`, `AppSearchManager`, `AppSearchResult`, `AppSearchSchema$BooleanPropertyConfig$Builder`, `AppSearchSchema$BooleanPropertyConfig`, `AppSearchSchema$Builder`, `AppSearchSchema$BytesPropertyConfig$Builder`, `AppSearchSchema$BytesPropertyConfig`, `AppSearchSchema$DocumentPropertyConfig$Builder`, `AppSearchSchema$DocumentPropertyConfig`, `AppSearchSchema$DoublePropertyConfig$Builder`, `AppSearchSchema$DoublePropertyConfig`, `AppSearchSchema$LongPropertyConfig$Builder`, `AppSearchSchema$LongPropertyConfig`, `AppSearchSchema$PropertyConfig`, `AppSearchSchema$StringPropertyConfig$Builder`, `AppSearchSchema$StringPropertyConfig`, `AppSearchSchema`, `AppSearchSession`, `BatchResultCallback`, `EnterpriseGlobalSearchSession`, `GenericDocument$Builder`, `GenericDocument`, `GetByDocumentIdRequest$Builder`, `GetByDocumentIdRequest`, `GetSchemaResponse$Builder`, `GetSchemaResponse`, `GlobalSearchSession`, `JoinSpec$Builder`, `JoinSpec`, `Migrator`, `PackageIdentifier`, `PropertyPath$PathSegment`, `PropertyPath`, `PutDocumentsRequest$Builder`, `PutDocumentsRequest`, `RemoveByDocumentIdRequest$Builder`, `RemoveByDocumentIdRequest`, `ReportSystemUsageRequest$Builder`, `ReportSystemUsageRequest`, `ReportUsageRequest$Builder`, `ReportUsageRequest`, `SchemaVisibilityConfig$Builder`, `SchemaVisibilityConfig`, `SearchResult$Builder`, `SearchResult$MatchInfo$Builder`, `SearchResult$MatchInfo`, `SearchResult$MatchRange`, `SearchResult`, `SearchResults`, `SearchSpec$Builder`, `SearchSpec`, `SearchSuggestionResult$Builder`, `SearchSuggestionResult`, `SearchSuggestionSpec$Builder`, `SearchSuggestionSpec`, `SetSchemaRequest$Builder`, `SetSchemaRequest`, `SetSchemaResponse$Builder`, `SetSchemaResponse$MigrationFailure`, `SetSchemaResponse`, `StorageInfo$Builder`, `StorageInfo`

</details>

<details><summary><code>android.app.appsearch.exceptions</code>: 1 of 1 missing</summary>

`AppSearchException`

</details>

<details><summary><code>android.app.appsearch.observer</code>: 5 of 5 missing</summary>

`DocumentChangeInfo`, `ObserverCallback`, `ObserverSpec$Builder`, `ObserverSpec`, `SchemaChangeInfo`

</details>

<details><summary><code>android.app.appsearch.util</code>: 1 of 1 missing</summary>

`DocumentIdUtil`

</details>

<details><summary><code>android.app.assist</code>: 4 of 4 missing</summary>

`AssistContent`, `AssistStructure$ViewNode`, `AssistStructure$WindowNode`, `AssistStructure`

</details>

<details><summary><code>android.app.backup</code>: 3 of 11 missing</summary>

`FileBackupHelper`, `FullBackupDataOutput`, `RestoreObserver`

</details>

<details><summary><code>android.app.blob</code>: 3 of 3 missing</summary>

`BlobHandle`, `BlobStoreManager$Session`, `BlobStoreManager`

</details>

<details><summary><code>android.app.job</code>: 3 of 9 missing</summary>

`JobServiceEngine`, `JobWorkItem$Builder`, `JobWorkItem`

</details>

<details><summary><code>android.app.people</code>: 3 of 3 missing</summary>

`ConversationStatus$Builder`, `ConversationStatus`, `PeopleManager`

</details>

<details><summary><code>android.app.role</code>: 1 of 1 missing</summary>

`RoleManager`

</details>

<details><summary><code>android.app.sdksandbox</code>: 7 of 7 missing</summary>

`AppOwnedSdkSandboxInterface`, `LoadSdkException`, `RequestSurfacePackageException`, `SandboxedSdk`, `SandboxedSdkProvider`, `SdkSandboxManager$SdkSandboxProcessDeathCallback`, `SdkSandboxManager`

</details>

<details><summary><code>android.app.sdksandbox.sdkprovider</code>: 2 of 2 missing</summary>

`SdkSandboxActivityHandler`, `SdkSandboxController`

</details>

<details><summary><code>android.app.slice</code>: 7 of 7 missing</summary>

`Slice$Builder`, `Slice`, `SliceItem`, `SliceManager`, `SliceMetrics`, `SliceProvider`, `SliceSpec`

</details>

<details><summary><code>android.app.usage</code>: 15 of 15 missing</summary>

`ConfigurationStats`, `EventStats`, `ExternalStorageStats`, `NetworkStats$Bucket`, `NetworkStats`, `NetworkStatsManager$UsageCallback`, `NetworkStatsManager`, `StorageStats`, `StorageStatsManager`, `UsageEvents$Event`, `UsageEvents`, `UsageEventsQuery$Builder`, `UsageEventsQuery`, `UsageStats`, `UsageStatsManager`

</details>

<details><summary><code>android.appwidget</code>: 4 of 5 missing</summary>

`AppWidgetHost`, `AppWidgetHostView`, `AppWidgetProvider`, `AppWidgetProviderInfo`

</details>

<details><summary><code>android.bluetooth</code>: 41 of 42 missing</summary>

`BluetoothA2dp`, `BluetoothAdapter$LeScanCallback`, `BluetoothAdapter`, `BluetoothAssignedNumbers`, `BluetoothClass$Device$Major`, `BluetoothClass$Device`, `BluetoothClass$Service`, `BluetoothClass`, `BluetoothCodecConfig$Builder`, `BluetoothCodecConfig`, `BluetoothCodecStatus$Builder`, `BluetoothCodecStatus`, `BluetoothCodecType`, `BluetoothCsipSetCoordinator`, `BluetoothGatt`, `BluetoothGattCallback`, `BluetoothGattCharacteristic`, `BluetoothGattDescriptor`, `BluetoothGattServer`, `BluetoothGattServerCallback`, `BluetoothGattService`, `BluetoothHeadset`, `BluetoothHealth`, `BluetoothHealthAppConfiguration`, `BluetoothHealthCallback`, `BluetoothHearingAid`, `BluetoothHidDevice$Callback`, `BluetoothHidDevice`, `BluetoothHidDeviceAppQosSettings`, `BluetoothHidDeviceAppSdpSettings`, `BluetoothLeAudio`, `BluetoothLeAudioCodecConfig$Builder`, `BluetoothLeAudioCodecConfig`, `BluetoothLeAudioCodecStatus`, `BluetoothManager`, `BluetoothProfile$ServiceListener`, `BluetoothProfile`, `BluetoothServerSocket`, `BluetoothSocket`, `BluetoothSocketException`, `BluetoothStatusCodes`

</details>

<details><summary><code>android.bluetooth.le</code>: 19 of 22 missing</summary>

`AdvertiseCallback`, `AdvertiseData$Builder`, `AdvertiseData`, `AdvertiseSettings$Builder`, `AdvertiseSettings`, `AdvertisingSet`, `AdvertisingSetCallback`, `AdvertisingSetParameters$Builder`, `AdvertisingSetParameters`, `BluetoothLeAdvertiser`, `BluetoothLeScanner`, `PeriodicAdvertisingParameters$Builder`, `PeriodicAdvertisingParameters`, `ScanFilter$Builder`, `ScanFilter`, `ScanSettings$Builder`, `ScanSettings`, `TransportBlock`, `TransportDiscoveryData`

</details>

<details><summary><code>android.companion</code>: 16 of 16 missing</summary>

`AssociatedDevice`, `AssociationInfo`, `AssociationRequest$Builder`, `AssociationRequest`, `BluetoothDeviceFilter$Builder`, `BluetoothDeviceFilter`, `BluetoothLeDeviceFilter$Builder`, `BluetoothLeDeviceFilter`, `CompanionDeviceManager$Callback`, `CompanionDeviceManager`, `CompanionDeviceService`, `CompanionException`, `DeviceFilter`, `DeviceNotAssociatedException`, `WifiDeviceFilter$Builder`, `WifiDeviceFilter`

</details>

<details><summary><code>android.companion.virtual</code>: 3 of 3 missing</summary>

`VirtualDevice`, `VirtualDeviceManager$VirtualDeviceListener`, `VirtualDeviceManager`

</details>

<details><summary><code>android.content</code>: 48 of 85 missing</summary>

`AbstractThreadedSyncAdapter`, `AsyncQueryHandler$WorkerArgs`, `AsyncQueryHandler$WorkerHandler`, `AsyncQueryHandler`, `AsyncTaskLoader`, `AttributionSource$Builder`, `AttributionSource`, `ClipData$Item$Builder`, `ContentProvider$CallingIdentity`, `ContentProvider$PipeDataWriter`, `ContentProviderOperation$Builder`, `ContentProviderOperation`, `ContentProviderResult`, `ContentQueryMap`, `ContentResolver$MimeTypeInfo`, `Context$BindServiceFlags`, `ContextParams$Builder`, `ContextParams`, `CursorLoader`, `Entity$NamedContentValues`, `Entity`, `EntityIterator`, `Intent$FilterComparison`, `Intent$ShortcutIconResource`, `IntentFilter$AuthorityEntry`, `Loader$ForceLoadContentObserver`, `Loader$OnLoadCanceledListener`, `Loader$OnLoadCompleteListener`, `Loader`, `LocusId`, `MutableContextWrapper`, `OperationApplicationException`, `PeriodicSync`, `QuickViewConstants`, `ReceiverCallNotAllowedException`, `RestrictionEntry`, `RestrictionsManager`, `SearchRecentSuggestionsProvider`, `SyncAdapterType`, `SyncContext`, `SyncInfo`, `SyncRequest$Builder`, `SyncRequest`, `SyncResult`, `SyncStats`, `SyncStatusObserver`, `UriRelativeFilter`, `UriRelativeFilterGroup`

</details>

<details><summary><code>android.content.om</code>: 5 of 5 missing</summary>

`FabricatedOverlay`, `OverlayIdentifier`, `OverlayInfo`, `OverlayManager`, `OverlayManagerTransaction`

</details>

<details><summary><code>android.content.pm</code>: 43 of 68 missing</summary>

`ActivityInfo$WindowLayout`, `ApkChecksum`, `ApplicationInfo$DisplayNameComparator`, `ArchivedActivityInfo`, `ArchivedPackageInfo`, `Attribution`, `Capability$Builder`, `Capability`, `CapabilityParams$Builder`, `CapabilityParams`, `ChangedPackages`, `Checksum`, `CrossProfileApps`, `InstallSourceInfo`, `LabeledIntent`, `LauncherActivityInfo`, `LauncherApps$ArchiveCompatibilityParams`, `LauncherApps$Callback`, `LauncherApps$PinItemRequest`, `LauncherApps$ShortcutQuery`, `LauncherApps`, `LauncherUserInfo`, `ModuleInfo`, `PackageInstaller$InstallConstraints$Builder`, `PackageInstaller$InstallConstraints`, `PackageInstaller$InstallConstraintsResult`, `PackageInstaller$PreapprovalDetails$Builder`, `PackageInstaller$PreapprovalDetails`, `PackageInstaller$UnarchivalState`, `PackageItemInfo$DisplayNameComparator`, `PackageManager$ApplicationInfoFlags`, `PackageManager$ComponentEnabledSetting`, `PackageManager$ComponentInfoFlags`, `PackageManager$OnChecksumsReadyListener`, `PackageManager$PackageInfoFlags`, `PackageManager$Property`, `PackageManager$ResolveInfoFlags`, `PackageStats`, `PathPermission`, `PermissionGroupInfo`, `ResolveInfo$DisplayNameComparator`, `SharedLibraryInfo`, `VersionedPackage`

</details>

<details><summary><code>android.content.pm.verify.domain</code>: 2 of 2 missing</summary>

`DomainVerificationManager`, `DomainVerificationUserState`

</details>

<details><summary><code>android.content.res</code>: 3 of 15 missing</summary>

`FontScaleConverter`, `ObbInfo`, `ObbScanner`

</details>

<details><summary><code>android.content.res.loader</code>: 3 of 3 missing</summary>

`AssetsProvider`, `ResourcesLoader`, `ResourcesProvider`

</details>

<details><summary><code>android.credentials</code>: 19 of 19 missing</summary>

`ClearCredentialStateException`, `ClearCredentialStateRequest`, `CreateCredentialException`, `CreateCredentialRequest$Builder`, `CreateCredentialRequest`, `CreateCredentialResponse`, `Credential`, `CredentialDescription`, `CredentialManager`, `CredentialOption$Builder`, `CredentialOption`, `GetCredentialException`, `GetCredentialRequest$Builder`, `GetCredentialRequest`, `GetCredentialResponse`, `PrepareGetCredentialResponse$PendingGetCredentialHandle`, `PrepareGetCredentialResponse`, `RegisterCredentialDescriptionRequest`, `UnregisterCredentialDescriptionRequest`

</details>

<details><summary><code>android.crypto.hpke</code>: 2 of 2 missing</summary>

`HpkeSpi`, `XdhKeySpec`

</details>

<details><summary><code>android.database</code>: 11 of 27 missing</summary>

`AbstractCursor$SelfContentObserver`, `ContentObservable`, `CrossProcessCursorWrapper`, `CursorJoiner$Result`, `CursorJoiner`, `CursorWindowAllocationException`, `CursorWrapper`, `DatabaseUtils$InsertHelper`, `DefaultDatabaseErrorHandler`, `MergeCursor`, `StaleDataException`

</details>

<details><summary><code>android.database.sqlite</code>: 10 of 31 missing</summary>

`SQLiteAccessPermException`, `SQLiteDatabase$OpenParams$Builder`, `SQLiteDatabase$OpenParams`, `SQLiteDatatypeMismatchException`, `SQLiteDiskIOException`, `SQLiteMisuseException`, `SQLiteOutOfMemoryException`, `SQLiteQueryBuilder`, `SQLiteRawStatement`, `SQLiteTableLockedException`

</details>

<details><summary><code>android.devicelock</code>: 2 of 2 missing</summary>

`DeviceId`, `DeviceLockManager`

</details>

<details><summary><code>android.drm</code>: 22 of 22 missing</summary>

`DrmConvertedStatus`, `DrmErrorEvent`, `DrmEvent`, `DrmInfo`, `DrmInfoEvent`, `DrmInfoRequest`, `DrmInfoStatus`, `DrmManagerClient$OnErrorListener`, `DrmManagerClient$OnEventListener`, `DrmManagerClient$OnInfoListener`, `DrmManagerClient`, `DrmRights`, `DrmStore$Action`, `DrmStore$ConstraintsColumns`, `DrmStore$DrmObjectType`, `DrmStore$Playback`, `DrmStore$RightsStatus`, `DrmStore`, `DrmSupportInfo`, `DrmUtils$ExtendedMetadataParser`, `DrmUtils`, `ProcessedData`

</details>

<details><summary><code>android.gesture</code>: 13 of 13 missing</summary>

`Gesture`, `GestureLibraries`, `GestureLibrary`, `GestureOverlayView$OnGestureListener`, `GestureOverlayView$OnGesturePerformedListener`, `GestureOverlayView$OnGesturingListener`, `GestureOverlayView`, `GesturePoint`, `GestureStore`, `GestureStroke`, `GestureUtils`, `OrientedBoundingBox`, `Prediction`

</details>

<details><summary><code>android.graphics</code>: 51 of 113 missing</summary>

`BlendModeColorFilter`, `BlurMaskFilter$Blur`, `BlurMaskFilter`, `Camera`, `Canvas$EdgeType`, `Canvas$VertexMode`, `ColorSpace$Adaptation`, `ColorSpace$Connector`, `ColorSpace$RenderIntent`, `ColorSpace$Rgb$TransferParameters`, `ComposePathEffect`, `ComposeShader`, `DiscretePathEffect`, `DrawFilter`, `EmbossMaskFilter`, `Gainmap`, `HardwareBufferRenderer$RenderRequest`, `HardwareBufferRenderer$RenderResult`, `HardwareBufferRenderer`, `HardwareRenderer$FrameRenderRequest`, `HardwareRenderer`, `ImageDecoder$DecodeException`, `ImageDecoder$ImageInfo`, `ImageDecoder$OnHeaderDecodedListener`, `ImageDecoder$OnPartialImageListener`, `ImageDecoder$Source`, `ImageDecoder`, `Interpolator$Result`, `Interpolator`, `LightingColorFilter`, `MaskFilter`, `Matrix44`, `Mesh`, `MeshSpecification$Attribute`, `MeshSpecification$Varying`, `MeshSpecification`, `Movie`, `PaintFlagsDrawFilter`, `ParcelableColorSpace`, `PathDashPathEffect$Style`, `PathDashPathEffect`, `PathIterator$Segment`, `PathIterator`, `PostProcessor`, `RecordingCanvas`, `RenderEffect`, `RenderNode`, `RuntimeShader`, `SumPathEffect`, `SweepGradient`, `YuvImage`

</details>

<details><summary><code>android.graphics.drawable</code>: 7 of 36 missing</summary>

`AnimatedImageDrawable`, `ColorStateListDrawable`, `DrawableContainer$DrawableContainerState`, `Icon$OnDrawableLoadedListener`, `LevelListDrawable`, `PaintDrawable`, `ShapeDrawable$ShaderFactory`

</details>

<details><summary><code>android.graphics.drawable.shapes</code>: 2 of 6 missing</summary>

`ArcShape`, `PathShape`

</details>

<details><summary><code>android.graphics.fonts</code>: 1 of 7 missing</summary>

`SystemFonts`

</details>

<details><summary><code>android.graphics.pdf</code>: 12 of 12 missing</summary>

`LoadParams$Builder`, `LoadParams`, `PdfDocument$Page`, `PdfDocument$PageInfo$Builder`, `PdfDocument$PageInfo`, `PdfDocument`, `PdfRenderer$Page`, `PdfRenderer`, `PdfRendererPreV$Page`, `PdfRendererPreV`, `RenderParams$Builder`, `RenderParams`

</details>

<details><summary><code>android.graphics.pdf.content</code>: 5 of 5 missing</summary>

`PdfPageGotoLinkContent$Destination`, `PdfPageGotoLinkContent`, `PdfPageImageContent`, `PdfPageLinkContent`, `PdfPageTextContent`

</details>

<details><summary><code>android.graphics.pdf.models</code>: 6 of 6 missing</summary>

`FormEditRecord$Builder`, `FormEditRecord`, `FormWidgetInfo$Builder`, `FormWidgetInfo`, `ListItem`, `PageMatchBounds`

</details>

<details><summary><code>android.graphics.pdf.models.selection</code>: 2 of 2 missing</summary>

`PageSelection`, `SelectionBoundary`

</details>

<details><summary><code>android.graphics.text</code>: 10 of 10 missing</summary>

`LineBreakConfig$Builder`, `LineBreakConfig`, `LineBreaker$Builder`, `LineBreaker$ParagraphConstraints`, `LineBreaker$Result`, `LineBreaker`, `MeasuredText$Builder`, `MeasuredText`, `PositionedGlyphs`, `TextRunShaper`

</details>

<details><summary><code>android.hardware</code>: 21 of 36 missing</summary>

`BatteryState`, `Camera$AutoFocusMoveCallback`, `Camera$Face`, `Camera$FaceDetectionListener`, `Camera$OnZoomChangeListener`, `ConsumerIrManager$CarrierFrequencyRange`, `ConsumerIrManager`, `DataSpace`, `GeomagneticField`, `HardwareBuffer`, `OverlayProperties`, `SensorAdditionalInfo`, `SensorDirectChannel`, `SensorEventCallback`, `SensorListener`, `SensorManager$DynamicSensorCallback`, `SensorPrivacyManager$Sensors`, `SensorPrivacyManager`, `SyncFence`, `TriggerEvent`, `TriggerEventListener`

</details>

<details><summary><code>android.hardware.biometrics</code>: 16 of 16 missing</summary>

`BiometricManager$Authenticators`, `BiometricManager$Strings`, `BiometricManager`, `BiometricPrompt$AuthenticationCallback`, `BiometricPrompt$AuthenticationResult`, `BiometricPrompt$Builder`, `BiometricPrompt$CryptoObject`, `BiometricPrompt`, `PromptContentItem`, `PromptContentItemBulletedText`, `PromptContentItemPlainText`, `PromptContentView`, `PromptContentViewWithMoreOptionsButton$Builder`, `PromptContentViewWithMoreOptionsButton`, `PromptVerticalListContentView$Builder`, `PromptVerticalListContentView`

</details>

<details><summary><code>android.hardware.camera2</code>: 30 of 30 missing</summary>

`CameraAccessException`, `CameraCaptureSession$CaptureCallback`, `CameraCaptureSession$StateCallback`, `CameraCaptureSession`, `CameraCharacteristics$Key`, `CameraCharacteristics`, `CameraConstrainedHighSpeedCaptureSession`, `CameraDevice$CameraDeviceSetup`, `CameraDevice$StateCallback`, `CameraDevice`, `CameraExtensionCharacteristics`, `CameraExtensionSession$ExtensionCaptureCallback`, `CameraExtensionSession$StateCallback`, `CameraExtensionSession$StillCaptureLatency`, `CameraExtensionSession`, `CameraManager$AvailabilityCallback`, `CameraManager$TorchCallback`, `CameraManager`, `CameraMetadata`, `CameraOfflineSession$CameraOfflineSessionCallback`, `CameraOfflineSession`, `CaptureFailure`, `CaptureRequest$Builder`, `CaptureRequest$Key`, `CaptureRequest`, `CaptureResult$Key`, `CaptureResult`, `DngCreator`, `MultiResolutionImageReader`, `TotalCaptureResult`

</details>

<details><summary><code>android.hardware.camera2.params</code>: 25 of 25 missing</summary>

`BlackLevelPattern`, `Capability`, `ColorSpaceProfiles`, `ColorSpaceTransform`, `DeviceStateSensorOrientationMap$Builder`, `DeviceStateSensorOrientationMap`, `DynamicRangeProfiles`, `ExtensionSessionConfiguration`, `Face$Builder`, `Face`, `InputConfiguration`, `LensIntrinsicsSample`, `LensShadingMap`, `MandatoryStreamCombination$MandatoryStreamInformation`, `MandatoryStreamCombination`, `MeteringRectangle`, `MultiResolutionStreamConfigurationMap`, `MultiResolutionStreamInfo`, `OisSample`, `OutputConfiguration`, `RecommendedStreamConfigurationMap`, `RggbChannelVector`, `SessionConfiguration`, `StreamConfigurationMap`, `TonemapCurve`

</details>

<details><summary><code>android.hardware.display</code>: 6 of 8 missing</summary>

`DeviceProductInfo`, `HdrConversionMode`, `VirtualDisplay$Callback`, `VirtualDisplay`, `VirtualDisplayConfig$Builder`, `VirtualDisplayConfig`

</details>

<details><summary><code>android.hardware.fingerprint</code>: 4 of 4 missing</summary>

`FingerprintManager$AuthenticationCallback`, `FingerprintManager$AuthenticationResult`, `FingerprintManager$CryptoObject`, `FingerprintManager`

</details>

<details><summary><code>android.hardware.input</code>: 1 of 3 missing</summary>

`HostUsiVersion`

</details>

<details><summary><code>android.hardware.lights</code>: 7 of 7 missing</summary>

`Light`, `LightState$Builder`, `LightState`, `LightsManager$LightsSession`, `LightsManager`, `LightsRequest$Builder`, `LightsRequest`

</details>

<details><summary><code>android.hardware.usb</code>: 9 of 9 missing</summary>

`UsbAccessory`, `UsbConfiguration`, `UsbConstants`, `UsbDevice`, `UsbDeviceConnection`, `UsbEndpoint`, `UsbInterface`, `UsbManager`, `UsbRequest`

</details>

<details><summary><code>android.health.connect</code>: 21 of 21 missing</summary>

`AggregateRecordsGroupedByDurationResponse`, `AggregateRecordsGroupedByPeriodResponse`, `AggregateRecordsRequest$Builder`, `AggregateRecordsRequest`, `AggregateRecordsResponse`, `HealthConnectException`, `HealthConnectManager`, `HealthPermissions`, `InsertRecordsResponse`, `LocalTimeRangeFilter$Builder`, `LocalTimeRangeFilter`, `ReadRecordsRequest`, `ReadRecordsRequestUsingFilters$Builder`, `ReadRecordsRequestUsingFilters`, `ReadRecordsRequestUsingIds$Builder`, `ReadRecordsRequestUsingIds`, `ReadRecordsResponse`, `RecordIdFilter`, `TimeInstantRangeFilter$Builder`, `TimeInstantRangeFilter`, `TimeRangeFilter`

</details>

<details><summary><code>android.health.connect.changelog</code>: 7 of 7 missing</summary>

`ChangeLogTokenRequest$Builder`, `ChangeLogTokenRequest`, `ChangeLogTokenResponse`, `ChangeLogsRequest$Builder`, `ChangeLogsRequest`, `ChangeLogsResponse$DeletedLog`, `ChangeLogsResponse`

</details>

<details><summary><code>android.health.connect.datatypes</code>: 143 of 143 missing</summary>

`ActiveCaloriesBurnedRecord$Builder`, `ActiveCaloriesBurnedRecord`, `AggregationType`, `AppInfo`, `BasalBodyTemperatureRecord$Builder`, `BasalBodyTemperatureRecord`, `BasalMetabolicRateRecord$Builder`, `BasalMetabolicRateRecord`, `BloodGlucoseRecord$Builder`, `BloodGlucoseRecord$RelationToMealType`, `BloodGlucoseRecord$SpecimenSource`, `BloodGlucoseRecord`, `BloodPressureRecord$BloodPressureMeasurementLocation`, `BloodPressureRecord$BodyPosition`, `BloodPressureRecord$Builder`, `BloodPressureRecord`, `BodyFatRecord$Builder`, `BodyFatRecord`, `BodyTemperatureMeasurementLocation`, `BodyTemperatureRecord$Builder`, `BodyTemperatureRecord`, `BodyWaterMassRecord$Builder`, `BodyWaterMassRecord`, `BoneMassRecord$Builder`, `BoneMassRecord`, `CervicalMucusRecord$Builder`, `CervicalMucusRecord$CervicalMucusAppearance`, `CervicalMucusRecord$CervicalMucusSensation`, `CervicalMucusRecord`, `CyclingPedalingCadenceRecord$Builder`, `CyclingPedalingCadenceRecord$CyclingPedalingCadenceRecordSample`, `CyclingPedalingCadenceRecord`, `DataOrigin$Builder`, `DataOrigin`, `Device$Builder`, `Device`, `DistanceRecord$Builder`, `DistanceRecord`, `ElevationGainedRecord$Builder`, `ElevationGainedRecord`, `ExerciseCompletionGoal$ActiveCaloriesBurnedGoal`, `ExerciseCompletionGoal$DistanceGoal`, `ExerciseCompletionGoal$DistanceWithVariableRestGoal`, `ExerciseCompletionGoal$DurationGoal`, `ExerciseCompletionGoal$RepetitionsGoal`, `ExerciseCompletionGoal$StepsGoal`, `ExerciseCompletionGoal$TotalCaloriesBurnedGoal`, `ExerciseCompletionGoal$UnknownGoal`, `ExerciseCompletionGoal$UnspecifiedGoal`, `ExerciseCompletionGoal`, `ExerciseLap$Builder`, `ExerciseLap`, `ExercisePerformanceGoal$AmrapGoal`, `ExercisePerformanceGoal$CadenceGoal`, `ExercisePerformanceGoal$HeartRateGoal`, `ExercisePerformanceGoal$PowerGoal`, `ExercisePerformanceGoal$RateOfPerceivedExertionGoal`, `ExercisePerformanceGoal$SpeedGoal`, `ExercisePerformanceGoal$UnknownGoal`, `ExercisePerformanceGoal$WeightGoal`, `ExercisePerformanceGoal`, `ExerciseRoute$Location$Builder`, `ExerciseRoute$Location`, `ExerciseRoute`, `ExerciseSegment$Builder`, `ExerciseSegment`, `ExerciseSegmentType`, `ExerciseSessionRecord$Builder`, `ExerciseSessionRecord`, `ExerciseSessionType`, `FloorsClimbedRecord$Builder`, `FloorsClimbedRecord`, `HeartRateRecord$Builder`, `HeartRateRecord$HeartRateSample`, `HeartRateRecord`, `HeartRateVariabilityRmssdRecord$Builder`, `HeartRateVariabilityRmssdRecord`, `HeightRecord$Builder`, `HeightRecord`, `HydrationRecord$Builder`, `HydrationRecord`, `InstantRecord`, `IntermenstrualBleedingRecord$Builder`, `IntermenstrualBleedingRecord`, `IntervalRecord`, `LeanBodyMassRecord$Builder`, `LeanBodyMassRecord`, `MealType`, `MenstruationFlowRecord$Builder`, `MenstruationFlowRecord$MenstruationFlowType`, `MenstruationFlowRecord`, `MenstruationPeriodRecord$Builder`, `MenstruationPeriodRecord`, `Metadata$Builder`, `Metadata`, `NutritionRecord$Builder`, `NutritionRecord`, `OvulationTestRecord$Builder`, `OvulationTestRecord$OvulationTestResult`, `OvulationTestRecord`, `OxygenSaturationRecord$Builder`, `OxygenSaturationRecord`, `PlannedExerciseBlock$Builder`, `PlannedExerciseBlock`, `PlannedExerciseSessionRecord$Builder`, `PlannedExerciseSessionRecord`, `PlannedExerciseStep$Builder`, `PlannedExerciseStep`, `PowerRecord$Builder`, `PowerRecord$PowerRecordSample`, `PowerRecord`, `Record`, `RespiratoryRateRecord$Builder`, `RespiratoryRateRecord`, `RestingHeartRateRecord$Builder`, `RestingHeartRateRecord`, `SexualActivityRecord$Builder`, `SexualActivityRecord$SexualActivityProtectionUsed`, `SexualActivityRecord`, `SkinTemperatureRecord$Builder`, `SkinTemperatureRecord$Delta`, `SkinTemperatureRecord`, `SleepSessionRecord$Builder`, `SleepSessionRecord$Stage`, `SleepSessionRecord$StageType`, `SleepSessionRecord`, `SpeedRecord$Builder`, `SpeedRecord$SpeedRecordSample`, `SpeedRecord`, `StepsCadenceRecord$Builder`, `StepsCadenceRecord$StepsCadenceRecordSample`, `StepsCadenceRecord`, `StepsRecord$Builder`, `StepsRecord`, `TotalCaloriesBurnedRecord$Builder`, `TotalCaloriesBurnedRecord`, `Vo2MaxRecord$Builder`, `Vo2MaxRecord$Vo2MaxMeasurementMethod`, `Vo2MaxRecord`, `WeightRecord$Builder`, `WeightRecord`, `WheelchairPushesRecord$Builder`, `WheelchairPushesRecord`

</details>

<details><summary><code>android.health.connect.datatypes.units</code>: 11 of 11 missing</summary>

`BloodGlucose`, `Energy`, `Length`, `Mass`, `Percentage`, `Power`, `Pressure`, `Temperature`, `TemperatureDelta`, `Velocity`, `Volume`

</details>

<details><summary><code>android.icu.lang</code>: 25 of 25 missing</summary>

`UCharacter$BidiPairedBracketType`, `UCharacter$DecompositionType`, `UCharacter$EastAsianWidth`, `UCharacter$GraphemeClusterBreak`, `UCharacter$HangulSyllableType`, `UCharacter$IndicPositionalCategory`, `UCharacter$IndicSyllabicCategory`, `UCharacter$JoiningGroup`, `UCharacter$JoiningType`, `UCharacter$LineBreak`, `UCharacter$NumericType`, `UCharacter$SentenceBreak`, `UCharacter$UnicodeBlock`, `UCharacter$VerticalOrientation`, `UCharacter$WordBreak`, `UCharacter`, `UCharacterCategory`, `UCharacterDirection`, `UCharacterEnums$ECharacterCategory`, `UCharacterEnums$ECharacterDirection`, `UCharacterEnums`, `UProperty$NameChoice`, `UProperty`, `UScript$ScriptUsage`, `UScript`

</details>

<details><summary><code>android.icu.math</code>: 2 of 2 missing</summary>

`BigDecimal`, `MathContext`

</details>

<details><summary><code>android.icu.number</code>: 28 of 28 missing</summary>

`CompactNotation`, `CurrencyPrecision`, `FormattedNumber`, `FormattedNumberRange`, `FractionPrecision`, `IntegerWidth`, `LocalizedNumberFormatter`, `LocalizedNumberRangeFormatter`, `Notation`, `NumberFormatter$DecimalSeparatorDisplay`, `NumberFormatter$GroupingStrategy`, `NumberFormatter$RoundingPriority`, `NumberFormatter$SignDisplay`, `NumberFormatter$TrailingZeroDisplay`, `NumberFormatter$UnitWidth`, `NumberFormatter`, `NumberFormatterSettings`, `NumberRangeFormatter$RangeCollapse`, `NumberRangeFormatter$RangeIdentityFallback`, `NumberRangeFormatter$RangeIdentityResult`, `NumberRangeFormatter`, `NumberRangeFormatterSettings`, `Precision`, `Scale`, `ScientificNotation`, `SimpleNotation`, `UnlocalizedNumberFormatter`, `UnlocalizedNumberRangeFormatter`

</details>

<details><summary><code>android.icu.text</code>: 118 of 118 missing</summary>

`AlphabeticIndex$Bucket$LabelType`, `AlphabeticIndex$Bucket`, `AlphabeticIndex$ImmutableIndex`, `AlphabeticIndex$Record`, `AlphabeticIndex`, `Bidi`, `BidiClassifier`, `BidiRun`, `BreakIterator`, `CaseMap$Fold`, `CaseMap$Lower`, `CaseMap$Title`, `CaseMap$Upper`, `CaseMap`, `CollationElementIterator`, `CollationKey$BoundMode`, `CollationKey`, `Collator$ReorderCodes`, `Collator`, `CompactDecimalFormat$CompactStyle`, `CompactDecimalFormat`, `ConstrainedFieldPosition`, `CurrencyPluralInfo`, `DateFormat$BooleanAttribute`, `DateFormat$Field`, `DateFormat$HourCycle`, `DateFormat`, `DateFormatSymbols`, `DateIntervalFormat$FormattedDateInterval`, `DateIntervalFormat`, `DateIntervalInfo$PatternInfo`, `DateIntervalInfo`, `DateTimePatternGenerator$DisplayWidth`, `DateTimePatternGenerator$PatternInfo`, `DateTimePatternGenerator`, `DecimalFormat`, `DecimalFormatSymbols`, `DisplayContext$Type`, `DisplayContext`, `DisplayOptions$Builder`, `DisplayOptions$Capitalization`, `DisplayOptions$DisplayLength`, `DisplayOptions$GrammaticalCase`, `DisplayOptions$NameStyle`, `DisplayOptions$NounClass`, `DisplayOptions$PluralCategory`, `DisplayOptions$SubstituteHandling`, `DisplayOptions`, `Edits$Iterator`, `Edits`, `FormattedValue`, `IDNA$Error`, `IDNA$Info`, `IDNA`, `ListFormatter$FormattedList`, `ListFormatter$Type`, `ListFormatter$Width`, `ListFormatter`, `LocaleDisplayNames$DialectHandling`, `LocaleDisplayNames$UiListItem`, `LocaleDisplayNames`, `MeasureFormat$FormatWidth`, `MeasureFormat`, `MessageFormat$Field`, `MessageFormat`, `MessagePattern$ApostropheMode`, `MessagePattern$ArgType`, `MessagePattern$Part$Type`, `MessagePattern$Part`, `MessagePattern`, `Normalizer$QuickCheckResult`, `Normalizer`, `Normalizer2$Mode`, `Normalizer2`, `NumberFormat$Field`, `NumberFormat`, `NumberingSystem`, `PluralFormat`, `PluralRules$PluralType`, `PluralRules`, `RelativeDateTimeFormatter$AbsoluteUnit`, `RelativeDateTimeFormatter$Direction`, `RelativeDateTimeFormatter$FormattedRelativeDateTime`, `RelativeDateTimeFormatter$RelativeDateTimeUnit`, `RelativeDateTimeFormatter$RelativeUnit`, `RelativeDateTimeFormatter$Style`, `RelativeDateTimeFormatter`, `Replaceable`, `RuleBasedCollator`, `ScientificNumberFormatter`, `SearchIterator$ElementComparisonType`, `SearchIterator`, `SelectFormat`, `SimpleDateFormat`, `StringPrepParseException`, `StringSearch`, `SymbolTable`, `TimeZoneFormat$GMTOffsetPatternType`, `TimeZoneFormat$ParseOption`, `TimeZoneFormat$Style`, `TimeZoneFormat$TimeType`, `TimeZoneFormat`, `TimeZoneNames$NameType`, `TimeZoneNames`, `Transliterator$Position`, `Transliterator`, `UCharacterIterator`, `UFormat`, `UnicodeFilter`, `UnicodeMatcher`, `UnicodeSet$ComparisonStyle`, `UnicodeSet$EntryRange`, `UnicodeSet$SpanCondition`, `UnicodeSet`, `UnicodeSetIterator`, `UnicodeSetSpanner$CountMethod`, `UnicodeSetSpanner$TrimOption`, `UnicodeSetSpanner`

</details>

<details><summary><code>android.icu.util</code>: 41 of 41 missing</summary>

`BuddhistCalendar`, `Calendar$WeekData`, `Calendar`, `ChineseCalendar`, `CopticCalendar`, `Currency$CurrencyUsage`, `Currency`, `CurrencyAmount`, `DateInterval`, `EthiopicCalendar`, `Freezable`, `GregorianCalendar`, `HebrewCalendar`, `ICUUncheckedIOException`, `IllformedLocaleException`, `IndianCalendar`, `IslamicCalendar$CalculationType`, `IslamicCalendar`, `JapaneseCalendar`, `LocaleData$MeasurementSystem`, `LocaleData$PaperSize`, `LocaleData`, `Measure`, `MeasureUnit$Complexity`, `MeasureUnit$MeasurePrefix`, `MeasureUnit`, `Output`, `RangeValueIterator$Element`, `RangeValueIterator`, `TaiwanCalendar`, `TimeUnit`, `TimeZone$SystemTimeZoneType`, `TimeZone`, `ULocale$AvailableType`, `ULocale$Builder`, `ULocale$Category`, `ULocale`, `UniversalTimeScale`, `ValueIterator$Element`, `ValueIterator`, `VersionInfo`

</details>

<details><summary><code>android.inputmethodservice</code>: 11 of 13 missing</summary>

`AbstractInputMethodService$AbstractInputMethodImpl`, `AbstractInputMethodService$AbstractInputMethodSessionImpl`, `ExtractEditText`, `InputMethodService$InputMethodImpl`, `InputMethodService$InputMethodSessionImpl`, `InputMethodService$Insets`, `Keyboard$Key`, `Keyboard$Row`, `Keyboard`, `KeyboardView$OnKeyboardActionListener`, `KeyboardView`

</details>

<details><summary><code>android.location</code>: 33 of 38 missing</summary>

`Address`, `Criteria`, `Geocoder$GeocodeListener`, `Geocoder`, `GnssAntennaInfo$Builder`, `GnssAntennaInfo$Listener`, `GnssAntennaInfo$PhaseCenterOffset`, `GnssAntennaInfo$SphericalCorrections`, `GnssAntennaInfo`, `GnssAutomaticGainControl$Builder`, `GnssAutomaticGainControl`, `GnssCapabilities$Builder`, `GnssCapabilities`, `GnssClock`, `GnssMeasurement`, `GnssMeasurementRequest$Builder`, `GnssMeasurementRequest`, `GnssMeasurementsEvent$Builder`, `GnssMeasurementsEvent$Callback`, `GnssMeasurementsEvent`, `GnssNavigationMessage$Callback`, `GnssNavigationMessage`, `GnssSignalType`, `GnssStatus$Builder`, `GpsSatellite`, `GpsStatus$Listener`, `GpsStatus$NmeaListener`, `GpsStatus`, `LocationProvider`, `LocationRequest$Builder`, `LocationRequest`, `OnNmeaMessageListener`, `SettingInjectorService`

</details>

<details><summary><code>android.location.altitude</code>: 1 of 1 missing</summary>

`AltitudeConverter`

</details>

<details><summary><code>android.location.provider</code>: 2 of 2 missing</summary>

`ProviderProperties$Builder`, `ProviderProperties`

</details>

<details><summary><code>android.media</code>: 199 of 272 missing</summary>

`ApplicationMediaCapabilities$Builder`, `ApplicationMediaCapabilities`, `AsyncPlayer`, `AudioDescriptor`, `AudioManager$AudioPlaybackCallback`, `AudioManager$AudioRecordingCallback`, `AudioManager$OnCommunicationDeviceChangedListener`, `AudioManager$OnModeChangedListener`, `AudioManager$OnPreferredMixerAttributesChangedListener`, `AudioMetadata$Format`, `AudioMetadata$Key`, `AudioMetadata`, `AudioMetadataMap`, `AudioMetadataReadMap`, `AudioMixerAttributes$Builder`, `AudioMixerAttributes`, `AudioPlaybackCaptureConfiguration$Builder`, `AudioPlaybackCaptureConfiguration`, `AudioPlaybackConfiguration`, `AudioPresentation$Builder`, `AudioPresentation`, `AudioProfile`, `AudioRecord$MetricsConstants`, `AudioRecord$OnRecordPositionUpdateListener`, `AudioRecord$OnRoutingChangedListener`, `AudioRecordingConfiguration`, `AudioRecordingMonitor`, `AudioTrack$MetricsConstants`, `AudioTrack$OnCodecFormatChangedListener`, `AudioTrack$OnPlaybackPositionUpdateListener`, `AudioTrack$OnRoutingChangedListener`, `AudioTrack$StreamEventCallback`, `CameraProfile`, `DeniedByServerException`, `DrmInitData$SchemeInitData`, `DrmInitData`, `EncoderProfiles$AudioProfile`, `EncoderProfiles$VideoProfile`, `EncoderProfiles`, `ExifInterface`, `FaceDetector$Face`, `FaceDetector`, `ImageReader$Builder`, `ImageReader$OnImageAvailableListener`, `ImageReader`, `ImageWriter$Builder`, `ImageWriter$OnImageReleasedListener`, `ImageWriter`, `JetPlayer$OnJetEventListener`, `JetPlayer`, `LoudnessCodecController$OnLoudnessCodecUpdateListener`, `LoudnessCodecController`, `MediaActionSound`, `MediaCas$EventListener`, `MediaCas$PluginDescriptor`, `MediaCas$Session`, `MediaCas`, `MediaCasException$DeniedByServerException`, `MediaCasException$InsufficientResourceException`, `MediaCasException$NotProvisionedException`, `MediaCasException$ResourceBusyException`, `MediaCasException$UnsupportedCasException`, `MediaCasException`, `MediaCasStateException`, `MediaCodec$CryptoException`, `MediaCodec$IncompatibleWithBlockModelException`, `MediaCodec$InvalidBufferFlagsException`, `MediaCodec$LinearBlock`, `MediaCodec$MetricsConstants`, `MediaCodec$OnFirstTunnelFrameReadyListener`, `MediaCodec$OnFrameRenderedListener`, `MediaCodec$OutputFrame`, `MediaCodec$ParameterDescriptor`, `MediaCodec$QueueRequest`, `MediaCodecInfo$EncoderCapabilities`, `MediaCodecInfo$VideoCapabilities$PerformancePoint`, `MediaCommunicationManager`, `MediaController2$Builder`, `MediaController2$ControllerCallback`, `MediaController2`, `MediaCryptoException`, `MediaDescrambler`, `MediaDrm$CryptoSession`, `MediaDrm$ErrorCodes`, `MediaDrm$KeyRequest`, `MediaDrm$KeyStatus`, `MediaDrm$LogMessage`, `MediaDrm$MediaDrmStateException`, `MediaDrm$MetricsConstants`, `MediaDrm$OnEventListener`, `MediaDrm$OnExpirationUpdateListener`, `MediaDrm$OnKeyStatusChangeListener`, `MediaDrm$OnSessionLostStateListener`, `MediaDrm$PlaybackComponent`, `MediaDrm$ProvisionRequest`, `MediaDrm$SessionException`, `MediaDrm`, `MediaDrmException`, `MediaDrmResetException`, `MediaDrmThrowable`, `MediaExtractor$CasInfo`, `MediaExtractor$MetricsConstants`, `MediaExtractor`, `MediaFeature$HdrType`, `MediaFeature`, `MediaFormat$QpOffsetRect`, `MediaMetadataEditor`, `MediaMetadataRetriever$BitmapParams`, `MediaParser$InputReader`, `MediaParser$OutputConsumer`, `MediaParser$ParsingException`, `MediaParser$SeekMap`, `MediaParser$SeekPoint`, `MediaParser$SeekableInputReader`, `MediaParser$TrackData`, `MediaParser$UnrecognizedInputFormatException`, `MediaParser`, `MediaPlayer$DrmInfo`, `MediaPlayer$MetricsConstants`, `MediaPlayer$NoDrmSchemeException`, `MediaPlayer$OnDrmConfigHelper`, `MediaPlayer$OnDrmInfoListener`, `MediaPlayer$OnDrmPreparedListener`, `MediaPlayer$OnMediaTimeDiscontinuityListener`, `MediaPlayer$OnSubtitleDataListener`, `MediaPlayer$OnTimedMetaDataAvailableListener`, `MediaPlayer$OnTimedTextListener`, `MediaPlayer$ProvisioningNetworkErrorException`, `MediaPlayer$ProvisioningServerErrorException`, `MediaPlayer$TrackInfo`, `MediaRecorder$MetricsConstants`, `MediaRoute2Info$Builder`, `MediaRoute2Info`, `MediaRoute2ProviderService`, `MediaRouter2$ControllerCallback`, `MediaRouter2$OnGetControllerHintsListener`, `MediaRouter2$RouteCallback`, `MediaRouter2$RoutingController`, `MediaRouter2$ScanRequest$Builder`, `MediaRouter2$ScanRequest`, `MediaRouter2$ScanToken`, `MediaRouter2$TransferCallback`, `MediaRouter2`, `MediaScannerConnection$MediaScannerConnectionClient`, `MediaScannerConnection$OnScanCompletedListener`, `MediaScannerConnection`, `MediaSession2$Builder`, `MediaSession2$ControllerInfo`, `MediaSession2$SessionCallback`, `MediaSession2`, `MediaSession2Service$MediaNotification`, `MediaSession2Service`, `MediaSync$Callback`, `MediaSync$OnErrorListener`, `MediaSync`, `MediaSyncEvent`, `MediaTimestamp`, `MicrophoneDirection`, `MicrophoneInfo$Coordinate3F`, `MicrophoneInfo`, `NotProvisionedException`, `RemoteControlClient$MetadataEditor`, `RemoteControlClient$OnGetPlaybackPositionListener`, `RemoteControlClient$OnMetadataUpdateListener`, `RemoteControlClient$OnPlaybackPositionUpdateListener`, `RemoteControlClient`, `RemoteController$MetadataEditor`, `RemoteController$OnClientUpdateListener`, `RemoteController`, `ResourceBusyException`, `RouteDiscoveryPreference$Builder`, `RouteDiscoveryPreference`, `RouteListingPreference$Builder`, `RouteListingPreference$Item$Builder`, `RouteListingPreference$Item`, `RouteListingPreference`, `RoutingSessionInfo$Builder`, `RoutingSessionInfo`, `Session2Command$Result`, `Session2Command`, `Session2CommandGroup$Builder`, `Session2CommandGroup`, `Session2Token`, `Spatializer$OnHeadTrackerAvailableListener`, `Spatializer$OnSpatializerStateChangedListener`, `Spatializer`, `SubtitleData`, `SyncParams`, `ThumbnailUtils`, `TimedMetaData`, `TimedText`, `ToneGenerator`, `UnsupportedSchemeException`, `VolumeAutomation`, `VolumeProvider`, `VolumeShaper$Configuration$Builder`, `VolumeShaper$Configuration`, `VolumeShaper$Operation`, `VolumeShaper`

</details>

<details><summary><code>android.media.audiofx</code>: 39 of 39 missing</summary>

`AcousticEchoCanceler`, `AudioEffect$Descriptor`, `AudioEffect$OnControlStatusChangeListener`, `AudioEffect$OnEnableStatusChangeListener`, `AudioEffect`, `AutomaticGainControl`, `BassBoost$OnParameterChangeListener`, `BassBoost$Settings`, `BassBoost`, `DynamicsProcessing$BandBase`, `DynamicsProcessing$BandStage`, `DynamicsProcessing$Channel`, `DynamicsProcessing$Config$Builder`, `DynamicsProcessing$Config`, `DynamicsProcessing$Eq`, `DynamicsProcessing$EqBand`, `DynamicsProcessing$Limiter`, `DynamicsProcessing$Mbc`, `DynamicsProcessing$MbcBand`, `DynamicsProcessing$Stage`, `DynamicsProcessing`, `EnvironmentalReverb$OnParameterChangeListener`, `EnvironmentalReverb$Settings`, `EnvironmentalReverb`, `Equalizer$OnParameterChangeListener`, `Equalizer$Settings`, `Equalizer`, `HapticGenerator`, `LoudnessEnhancer`, `NoiseSuppressor`, `PresetReverb$OnParameterChangeListener`, `PresetReverb$Settings`, `PresetReverb`, `Virtualizer$OnParameterChangeListener`, `Virtualizer$Settings`, `Virtualizer`, `Visualizer$MeasurementPeakRms`, `Visualizer$OnDataCaptureListener`, `Visualizer`

</details>

<details><summary><code>android.media.browse</code>: 1 of 5 missing</summary>

`MediaBrowser$ItemCallback`

</details>

<details><summary><code>android.media.effect</code>: 4 of 4 missing</summary>

`Effect`, `EffectContext`, `EffectFactory`, `EffectUpdateListener`

</details>

<details><summary><code>android.media.metrics</code>: 22 of 22 missing</summary>

`BundleSession`, `EditingEndedEvent$Builder`, `EditingEndedEvent`, `EditingSession`, `Event`, `LogSessionId`, `MediaItemInfo$Builder`, `MediaItemInfo`, `MediaMetricsManager`, `NetworkEvent$Builder`, `NetworkEvent`, `PlaybackErrorEvent$Builder`, `PlaybackErrorEvent`, `PlaybackMetrics$Builder`, `PlaybackMetrics`, `PlaybackSession`, `PlaybackStateEvent$Builder`, `PlaybackStateEvent`, `RecordingSession`, `TrackChangeEvent$Builder`, `TrackChangeEvent`, `TranscodingSession`

</details>

<details><summary><code>android.media.midi</code>: 14 of 14 missing</summary>

`MidiDevice$MidiConnection`, `MidiDevice`, `MidiDeviceInfo$PortInfo`, `MidiDeviceInfo`, `MidiDeviceService`, `MidiDeviceStatus`, `MidiInputPort`, `MidiManager$DeviceCallback`, `MidiManager$OnDeviceOpenedListener`, `MidiManager`, `MidiOutputPort`, `MidiReceiver`, `MidiSender`, `MidiUmpDeviceService`

</details>

<details><summary><code>android.media.projection</code>: 4 of 4 missing</summary>

`MediaProjection$Callback`, `MediaProjection`, `MediaProjectionConfig`, `MediaProjectionManager`

</details>

<details><summary><code>android.media.session</code>: 6 of 17 missing</summary>

`MediaController$PlaybackInfo`, `MediaSessionManager$OnActiveSessionsChangedListener`, `MediaSessionManager$OnMediaKeyEventSessionChangedListener`, `MediaSessionManager$OnSession2TokensChangedListener`, `MediaSessionManager$RemoteUserInfo`, `MediaSessionManager`

</details>

<details><summary><code>android.media.tv</code>: 50 of 50 missing</summary>

`AdBuffer`, `AdRequest`, `AdResponse`, `AitInfo`, `BroadcastInfoRequest`, `BroadcastInfoResponse`, `CommandRequest`, `CommandResponse`, `DsmccRequest`, `DsmccResponse`, `PesRequest`, `PesResponse`, `SectionRequest`, `SectionResponse`, `StreamEventRequest`, `StreamEventResponse`, `TableRequest`, `TableResponse$Builder`, `TableResponse`, `TimelineRequest`, `TimelineResponse`, `TsRequest`, `TsResponse`, `TvContentRating`, `TvContract$BaseTvColumns`, `TvContract$Channels$Logo`, `TvContract$Channels`, `TvContract$PreviewPrograms`, `TvContract$Programs$Genres`, `TvContract$Programs`, `TvContract$RecordedPrograms`, `TvContract$WatchNextPrograms`, `TvContract`, `TvInputInfo$Builder`, `TvInputInfo`, `TvInputManager$TvInputCallback`, `TvInputManager`, `TvInputService$HardwareSession`, `TvInputService$RecordingSession`, `TvInputService$Session`, `TvInputService`, `TvRecordingClient$RecordingCallback`, `TvRecordingClient`, `TvRecordingInfo`, `TvTrackInfo$Builder`, `TvTrackInfo`, `TvView$OnUnhandledInputEventListener`, `TvView$TimeShiftPositionCallback`, `TvView$TvInputCallback`, `TvView`

</details>

<details><summary><code>android.media.tv.interactive</code>: 9 of 9 missing</summary>

`AppLinkInfo`, `TvInteractiveAppManager$TvInteractiveAppCallback`, `TvInteractiveAppManager`, `TvInteractiveAppService$Session`, `TvInteractiveAppService`, `TvInteractiveAppServiceInfo`, `TvInteractiveAppView$OnUnhandledInputEventListener`, `TvInteractiveAppView$TvInteractiveAppCallback`, `TvInteractiveAppView`

</details>

<details><summary><code>android.mtp</code>: 7 of 7 missing</summary>

`MtpConstants`, `MtpDevice`, `MtpDeviceInfo`, `MtpEvent`, `MtpObjectInfo$Builder`, `MtpObjectInfo`, `MtpStorageInfo`

</details>

<details><summary><code>android.net</code>: 50 of 71 missing</summary>

`CaptivePortal`, `ConnectivityDiagnosticsManager$ConnectivityDiagnosticsCallback`, `ConnectivityDiagnosticsManager$ConnectivityReport`, `ConnectivityDiagnosticsManager$DataStallReport`, `ConnectivityDiagnosticsManager`, `DhcpInfo`, `DnsResolver$Callback`, `DnsResolver$DnsException`, `DnsResolver`, `EthernetNetworkSpecifier`, `Ikev2VpnProfile$Builder`, `Ikev2VpnProfile`, `InetAddresses`, `IpConfiguration$Builder`, `IpConfiguration`, `IpPrefix`, `IpSecAlgorithm`, `IpSecManager$ResourceUnavailableException`, `IpSecManager$SecurityParameterIndex`, `IpSecManager$SpiUnavailableException`, `IpSecManager$UdpEncapsulationSocket`, `IpSecManager`, `IpSecTransform$Builder`, `IpSecTransform`, `IpSecTransformState$Builder`, `IpSecTransformState`, `LinkAddress`, `MacAddress`, `MailTo`, `NetworkSpecifier`, `PlatformVpnProfile`, `Proxy`, `RouteInfo`, `SSLCertificateSocketFactory`, `SSLSessionCache`, `SocketKeepalive$Callback`, `SocketKeepalive`, `StaticIpConfiguration$Builder`, `StaticIpConfiguration`, `TelephonyNetworkSpecifier$Builder`, `TelephonyNetworkSpecifier`, `TransportInfo`, `UrlQuerySanitizer$IllegalCharacterValueSanitizer`, `UrlQuerySanitizer$ParameterValuePair`, `UrlQuerySanitizer$ValueSanitizer`, `UrlQuerySanitizer`, `VpnManager`, `VpnProfileState`, `VpnService$Builder`, `VpnService`

</details>

<details><summary><code>android.net.eap</code>: 13 of 13 missing</summary>

`EapAkaInfo$Builder`, `EapAkaInfo`, `EapInfo`, `EapSessionConfig$Builder`, `EapSessionConfig$EapAkaConfig`, `EapSessionConfig$EapAkaOption$Builder`, `EapSessionConfig$EapAkaOption`, `EapSessionConfig$EapAkaPrimeConfig`, `EapSessionConfig$EapMethodConfig`, `EapSessionConfig$EapMsChapV2Config`, `EapSessionConfig$EapSimConfig`, `EapSessionConfig$EapTtlsConfig`, `EapSessionConfig`

</details>

<details><summary><code>android.net.http</code>: 31 of 32 missing</summary>

`BidirectionalStream$Builder`, `BidirectionalStream$Callback`, `BidirectionalStream`, `CallbackException`, `ConnectionMigrationOptions$Builder`, `ConnectionMigrationOptions`, `DnsOptions$Builder`, `DnsOptions$StaleDnsOptions$Builder`, `DnsOptions$StaleDnsOptions`, `DnsOptions`, `HeaderBlock`, `HttpEngine$Builder`, `HttpEngine`, `HttpException`, `HttpResponseCache`, `InlineExecutionProhibitedException`, `NetworkException`, `QuicException`, `QuicOptions$Builder`, `QuicOptions`, `SslCertificate$DName`, `SslCertificate`, `SslError`, `UploadDataProvider`, `UploadDataSink`, `UrlRequest$Builder`, `UrlRequest$Callback`, `UrlRequest$Status`, `UrlRequest$StatusListener`, `UrlRequest`, `UrlResponseInfo`

</details>

<details><summary><code>android.net.ipsec.ike</code>: 41 of 41 missing</summary>

`ChildSaProposal$Builder`, `ChildSaProposal`, `ChildSessionCallback`, `ChildSessionConfiguration$Builder`, `ChildSessionConfiguration`, `ChildSessionParams`, `IkeDerAsn1DnIdentification`, `IkeFqdnIdentification`, `IkeIdentification`, `IkeIpv4AddrIdentification`, `IkeIpv6AddrIdentification`, `IkeKeyIdIdentification`, `IkeRfc822AddrIdentification`, `IkeSaProposal$Builder`, `IkeSaProposal`, `IkeSession`, `IkeSessionCallback`, `IkeSessionConfiguration$Builder`, `IkeSessionConfiguration`, `IkeSessionConnectionInfo`, `IkeSessionParams$Builder`, `IkeSessionParams$IkeAuthConfig`, `IkeSessionParams$IkeAuthDigitalSignLocalConfig`, `IkeSessionParams$IkeAuthDigitalSignRemoteConfig`, `IkeSessionParams$IkeAuthEapConfig`, `IkeSessionParams$IkeAuthPskConfig`, `IkeSessionParams`, `IkeTrafficSelector`, `IkeTunnelConnectionParams`, `SaProposal`, `TransportModeChildSessionParams$Builder`, `TransportModeChildSessionParams`, `TunnelModeChildSessionParams$Builder`, `TunnelModeChildSessionParams$ConfigRequestIpv4Address`, `TunnelModeChildSessionParams$ConfigRequestIpv4DhcpServer`, `TunnelModeChildSessionParams$ConfigRequestIpv4DnsServer`, `TunnelModeChildSessionParams$ConfigRequestIpv4Netmask`, `TunnelModeChildSessionParams$ConfigRequestIpv6Address`, `TunnelModeChildSessionParams$ConfigRequestIpv6DnsServer`, `TunnelModeChildSessionParams$TunnelModeChildConfigRequest`, `TunnelModeChildSessionParams`

</details>

<details><summary><code>android.net.ipsec.ike.exceptions</code>: 10 of 10 missing</summary>

`IkeException`, `IkeIOException`, `IkeInternalException`, `IkeNetworkLostException`, `IkeNonProtocolException`, `IkeProtocolException`, `IkeTimeoutException`, `InvalidKeException`, `InvalidMajorVersionException`, `InvalidSelectorsException`

</details>

<details><summary><code>android.net.nsd</code>: 8 of 8 missing</summary>

`DiscoveryRequest$Builder`, `DiscoveryRequest`, `NsdManager$DiscoveryListener`, `NsdManager$RegistrationListener`, `NsdManager$ResolveListener`, `NsdManager$ServiceInfoCallback`, `NsdManager`, `NsdServiceInfo`

</details>

<details><summary><code>android.net.rtp</code>: 4 of 4 missing</summary>

`AudioCodec`, `AudioGroup`, `AudioStream`, `RtpStream`

</details>

<details><summary><code>android.net.sip</code>: 11 of 11 missing</summary>

`SipAudioCall$Listener`, `SipAudioCall`, `SipErrorCode`, `SipException`, `SipManager`, `SipProfile$Builder`, `SipProfile`, `SipRegistrationListener`, `SipSession$Listener`, `SipSession$State`, `SipSession`

</details>

<details><summary><code>android.net.ssl</code>: 1 of 2 missing</summary>

`SSLEngines`

</details>

<details><summary><code>android.net.vcn</code>: 11 of 11 missing</summary>

`VcnCellUnderlyingNetworkTemplate$Builder`, `VcnCellUnderlyingNetworkTemplate`, `VcnConfig$Builder`, `VcnConfig`, `VcnGatewayConnectionConfig$Builder`, `VcnGatewayConnectionConfig`, `VcnManager$VcnStatusCallback`, `VcnManager`, `VcnUnderlyingNetworkTemplate`, `VcnWifiUnderlyingNetworkTemplate$Builder`, `VcnWifiUnderlyingNetworkTemplate`

</details>

<details><summary><code>android.net.wifi</code>: 37 of 39 missing</summary>

`EasyConnectStatusCallback`, `MloLink`, `ScanResult$InformationElement`, `ScanResult`, `SoftApConfiguration`, `SupplicantState`, `WifiAvailableChannel`, `WifiConfiguration$AuthAlgorithm`, `WifiConfiguration$GroupCipher`, `WifiConfiguration$GroupMgmtCipher`, `WifiConfiguration$KeyMgmt`, `WifiConfiguration$PairwiseCipher`, `WifiConfiguration$Protocol`, `WifiConfiguration$Status`, `WifiConfiguration`, `WifiEnterpriseConfig$Eap`, `WifiEnterpriseConfig$Phase2`, `WifiEnterpriseConfig`, `WifiInfo$Builder`, `WifiManager$AddNetworkResult`, `WifiManager$InterfaceCreationImpact`, `WifiManager$LocalOnlyConnectionFailureListener`, `WifiManager$LocalOnlyHotspotCallback`, `WifiManager$LocalOnlyHotspotReservation`, `WifiManager$MulticastLock`, `WifiManager$ScanResultsCallback`, `WifiManager$SubsystemRestartTrackingCallback`, `WifiManager$SuggestionConnectionStatusListener`, `WifiManager$SuggestionUserApprovalStatusListener`, `WifiManager$WifiLock`, `WifiManager$WpsCallback`, `WifiNetworkSpecifier$Builder`, `WifiNetworkSpecifier`, `WifiNetworkSuggestion$Builder`, `WifiNetworkSuggestion`, `WifiSsid`, `WpsInfo`

</details>

<details><summary><code>android.net.wifi.aware</code>: 25 of 25 missing</summary>

`AttachCallback`, `AwarePairingConfig$Builder`, `AwarePairingConfig`, `AwareResources`, `Characteristics`, `DiscoverySession`, `DiscoverySessionCallback`, `IdentityChangedListener`, `ParcelablePeerHandle`, `PeerHandle`, `PublishConfig$Builder`, `PublishConfig`, `PublishDiscoverySession`, `ServiceDiscoveryInfo`, `SubscribeConfig$Builder`, `SubscribeConfig`, `SubscribeDiscoverySession`, `WifiAwareChannelInfo`, `WifiAwareDataPathSecurityConfig$Builder`, `WifiAwareDataPathSecurityConfig`, `WifiAwareManager`, `WifiAwareNetworkInfo`, `WifiAwareNetworkSpecifier$Builder`, `WifiAwareNetworkSpecifier`, `WifiAwareSession`

</details>

<details><summary><code>android.net.wifi.hotspot2</code>: 2 of 2 missing</summary>

`ConfigParser`, `PasspointConfiguration`

</details>

<details><summary><code>android.net.wifi.hotspot2.omadm</code>: 1 of 1 missing</summary>

`PpsMoParser`

</details>

<details><summary><code>android.net.wifi.hotspot2.pps</code>: 5 of 5 missing</summary>

`Credential$CertificateCredential`, `Credential$SimCredential`, `Credential$UserCredential`, `Credential`, `HomeSp`

</details>

<details><summary><code>android.net.wifi.p2p</code>: 26 of 26 missing</summary>

`WifiP2pConfig$Builder`, `WifiP2pConfig`, `WifiP2pDevice`, `WifiP2pDeviceList`, `WifiP2pDiscoveryConfig$Builder`, `WifiP2pDiscoveryConfig`, `WifiP2pGroup`, `WifiP2pInfo`, `WifiP2pManager$ActionListener`, `WifiP2pManager$Channel`, `WifiP2pManager$ChannelListener`, `WifiP2pManager$ConnectionInfoListener`, `WifiP2pManager$DeviceInfoListener`, `WifiP2pManager$DiscoveryStateListener`, `WifiP2pManager$DnsSdServiceResponseListener`, `WifiP2pManager$DnsSdTxtRecordListener`, `WifiP2pManager$ExternalApproverRequestListener`, `WifiP2pManager$GroupInfoListener`, `WifiP2pManager$NetworkInfoListener`, `WifiP2pManager$P2pStateListener`, `WifiP2pManager$PeerListListener`, `WifiP2pManager$ServiceResponseListener`, `WifiP2pManager$UpnpServiceResponseListener`, `WifiP2pManager$WifiP2pListener`, `WifiP2pManager`, `WifiP2pWfdInfo`

</details>

<details><summary><code>android.net.wifi.p2p.nsd</code>: 6 of 6 missing</summary>

`WifiP2pDnsSdServiceInfo`, `WifiP2pDnsSdServiceRequest`, `WifiP2pServiceInfo`, `WifiP2pServiceRequest`, `WifiP2pUpnpServiceInfo`, `WifiP2pUpnpServiceRequest`

</details>

<details><summary><code>android.net.wifi.rtt</code>: 10 of 10 missing</summary>

`CivicLocationKeys`, `RangingRequest$Builder`, `RangingRequest`, `RangingResult$Builder`, `RangingResult`, `RangingResultCallback`, `ResponderConfig$Builder`, `ResponderConfig`, `ResponderLocation`, `WifiRttManager`

</details>

<details><summary><code>android.nfc</code>: 13 of 15 missing</summary>

`AvailableNfcAntenna`, `FormatException`, `NdefMessage`, `NdefRecord`, `NfcAdapter$CreateBeamUrisCallback`, `NfcAdapter$CreateNdefMessageCallback`, `NfcAdapter$OnNdefPushCompleteCallback`, `NfcAdapter$OnTagRemovedListener`, `NfcAdapter$ReaderCallback`, `NfcAntennaInfo`, `NfcEvent`, `Tag`, `TagLostException`

</details>

<details><summary><code>android.nfc.cardemulation</code>: 6 of 6 missing</summary>

`CardEmulation`, `HostApduService`, `HostNfcFService`, `NfcFCardEmulation`, `OffHostApduService`, `PollingFrame`

</details>

<details><summary><code>android.nfc.tech</code>: 11 of 11 missing</summary>

`IsoDep`, `MifareClassic`, `MifareUltralight`, `Ndef`, `NdefFormatable`, `NfcA`, `NfcB`, `NfcBarcode`, `NfcF`, `NfcV`, `TagTechnology`

</details>

<details><summary><code>android.opengl</code>: 19 of 36 missing</summary>

`EGL15`, `EGLImage`, `EGLSync`, `ETC1`, `ETC1Util$ETC1Texture`, `ETC1Util`, `GLDebugHelper`, `GLES10`, `GLES10Ext`, `GLES11`, `GLES11Ext`, `GLES31`, `GLES31Ext$DebugProcKHR`, `GLES31Ext`, `GLES32$DebugProc`, `GLES32`, `GLU`, `Matrix`, `Visibility`

</details>

<details><summary><code>android.os</code>: 47 of 107 missing</summary>

`BatteryManager`, `BugreportManager$BugreportCallback`, `BugreportManager`, `Build$Partition`, `CombinedVibration$ParallelCombination`, `CombinedVibration`, `CpuUsageInfo`, `DeadSystemException`, `Debug$InstructionCount`, `DropBoxManager$Entry`, `DropBoxManager`, `FileUriExposedException`, `FileUtils$ProgressListener`, `FileUtils`, `HardwarePropertiesManager`, `LimitExceededException`, `MessageQueue$OnFileDescriptorEventListener`, `OutcomeReceiver`, `ParcelFileDescriptor$FileDescriptorDetachedException`, `ParcelFileDescriptor$OnCloseListener`, `ParcelFormatException`, `PatternMatcher`, `PerformanceHintManager$Session`, `PerformanceHintManager`, `PowerManager$OnThermalStatusChangedListener`, `PowerManager$WakeLockStateListener`, `PowerMonitor`, `PowerMonitorReadings`, `ProfilingManager`, `ProfilingResult`, `ProxyFileDescriptorCallback`, `RecoverySystem$ProgressListener`, `RecoverySystem`, `SecurityStateManager`, `SharedMemory`, `StrictMode$OnThreadViolationListener`, `StrictMode$OnVmViolationListener`, `TestLooperManager`, `TokenWatcher`, `TransactionTooLargeException`, `UserManager$UserOperationException`, `VibrationAttributes$Builder`, `VibrationAttributes`, `VibrationEffect$Composition`, `VibrationEffect`, `VibratorManager`, `WorkDuration`

</details>

<details><summary><code>android.os.ext</code>: 1 of 1 missing</summary>

`SdkExtensions`

</details>

<details><summary><code>android.os.health</code>: 8 of 8 missing</summary>

`HealthStats`, `PackageHealthStats`, `PidHealthStats`, `ProcessHealthStats`, `ServiceHealthStats`, `SystemHealthManager`, `TimerStat`, `UidHealthStats`

</details>

<details><summary><code>android.os.storage</code>: 2 of 4 missing</summary>

`OnObbStateChangeListener`, `StorageManager$StorageVolumeCallback`

</details>

<details><summary><code>android.os.strictmode</code>: 23 of 23 missing</summary>

`CleartextNetworkViolation`, `ContentUriWithoutPermissionViolation`, `CredentialProtectedWhileLockedViolation`, `CustomViolation`, `DiskReadViolation`, `DiskWriteViolation`, `ExplicitGcViolation`, `FileUriExposedViolation`, `ImplicitDirectBootViolation`, `IncorrectContextUseViolation`, `InstanceCountViolation`, `IntentReceiverLeakedViolation`, `LeakedClosableViolation`, `NetworkViolation`, `NonSdkApiUsedViolation`, `ResourceMismatchViolation`, `ServiceConnectionLeakedViolation`, `SqliteObjectLeakedViolation`, `UnbufferedIoViolation`, `UnsafeIntentLaunchViolation`, `UntaggedSocketViolation`, `Violation`, `WebViewMethodCalledOnWrongThreadViolation`

</details>

<details><summary><code>android.preference</code>: 23 of 24 missing</summary>

`CheckBoxPreference`, `DialogPreference`, `EditTextPreference`, `ListPreference`, `MultiSelectListPreference`, `Preference$BaseSavedState`, `Preference$OnPreferenceChangeListener`, `Preference$OnPreferenceClickListener`, `Preference`, `PreferenceActivity$Header`, `PreferenceActivity`, `PreferenceCategory`, `PreferenceDataStore`, `PreferenceFragment$OnPreferenceStartFragmentCallback`, `PreferenceFragment`, `PreferenceGroup`, `PreferenceManager$OnActivityDestroyListener`, `PreferenceManager$OnActivityResultListener`, `PreferenceManager$OnActivityStopListener`, `PreferenceScreen`, `RingtonePreference`, `SwitchPreference`, `TwoStatePreference`

</details>

<details><summary><code>android.print</code>: 21 of 21 missing</summary>

`PageRange`, `PrintAttributes$Builder`, `PrintAttributes$Margins`, `PrintAttributes$MediaSize`, `PrintAttributes$Resolution`, `PrintAttributes`, `PrintDocumentAdapter$LayoutResultCallback`, `PrintDocumentAdapter$WriteResultCallback`, `PrintDocumentAdapter`, `PrintDocumentInfo$Builder`, `PrintDocumentInfo`, `PrintJob`, `PrintJobId`, `PrintJobInfo$Builder`, `PrintJobInfo`, `PrintManager`, `PrinterCapabilitiesInfo$Builder`, `PrinterCapabilitiesInfo`, `PrinterId`, `PrinterInfo$Builder`, `PrinterInfo`

</details>

<details><summary><code>android.print.pdf</code>: 1 of 1 missing</summary>

`PrintedPdfDocument`

</details>

<details><summary><code>android.printservice</code>: 5 of 5 missing</summary>

`CustomPrinterIconCallback`, `PrintDocument`, `PrintJob`, `PrintService`, `PrinterDiscoverySession`

</details>

<details><summary><code>android.provider</code>: 213 of 232 missing</summary>

`AlarmClock`, `BaseColumns`, `BlockedNumberContract$BlockedNumbers`, `BlockedNumberContract`, `Browser`, `CalendarContract$Attendees`, `CalendarContract$AttendeesColumns`, `CalendarContract$CalendarAlerts`, `CalendarContract$CalendarAlertsColumns`, `CalendarContract$CalendarCache`, `CalendarContract$CalendarCacheColumns`, `CalendarContract$CalendarColumns`, `CalendarContract$CalendarEntity`, `CalendarContract$CalendarSyncColumns`, `CalendarContract$Calendars`, `CalendarContract$Colors`, `CalendarContract$ColorsColumns`, `CalendarContract$EventDays`, `CalendarContract$EventDaysColumns`, `CalendarContract$Events`, `CalendarContract$EventsColumns`, `CalendarContract$EventsEntity`, `CalendarContract$ExtendedProperties`, `CalendarContract$ExtendedPropertiesColumns`, `CalendarContract$Instances`, `CalendarContract$Reminders`, `CalendarContract$RemindersColumns`, `CalendarContract$SyncColumns`, `CalendarContract$SyncState`, `CalendarContract`, `CallLog$Calls`, `CallLog$Locations`, `CallLog`, `CloudMediaProvider$CloudMediaSurfaceController`, `CloudMediaProvider$CloudMediaSurfaceStateChangedCallback`, `CloudMediaProvider`, `CloudMediaProviderContract$AlbumColumns`, `CloudMediaProviderContract$MediaCollectionInfo`, `CloudMediaProviderContract$MediaColumns`, `CloudMediaProviderContract`, `Contacts$ContactMethods`, `Contacts$ContactMethodsColumns`, `Contacts$Extensions`, `Contacts$ExtensionsColumns`, `Contacts$GroupMembership`, `Contacts$Groups`, `Contacts$GroupsColumns`, `Contacts$Intents$Insert`, `Contacts$Intents$UI`, `Contacts$Intents`, `Contacts$OrganizationColumns`, `Contacts$Organizations`, `Contacts$People$ContactMethods`, `Contacts$People$Extensions`, `Contacts$People$Phones`, `Contacts$People`, `Contacts$PeopleColumns`, `Contacts$Phones`, `Contacts$PhonesColumns`, `Contacts$Photos`, `Contacts$PhotosColumns`, `Contacts$PresenceColumns`, `Contacts$Settings`, `Contacts$SettingsColumns`, `Contacts`, `ContactsContract$AggregationExceptions`, `ContactsContract$BaseSyncColumns`, `ContactsContract$CommonDataKinds$BaseTypes`, `ContactsContract$CommonDataKinds$Callable`, `ContactsContract$CommonDataKinds$CommonColumns`, `ContactsContract$CommonDataKinds$Contactables`, `ContactsContract$CommonDataKinds$Email`, `ContactsContract$CommonDataKinds$Event`, `ContactsContract$CommonDataKinds$GroupMembership`, `ContactsContract$CommonDataKinds$Identity`, `ContactsContract$CommonDataKinds$Im`, `ContactsContract$CommonDataKinds$Nickname`, `ContactsContract$CommonDataKinds$Note`, `ContactsContract$CommonDataKinds$Organization`, `ContactsContract$CommonDataKinds$Phone`, `ContactsContract$CommonDataKinds$Photo`, `ContactsContract$CommonDataKinds$Relation`, `ContactsContract$CommonDataKinds$SipAddress`, `ContactsContract$CommonDataKinds$StructuredName`, `ContactsContract$CommonDataKinds$StructuredPostal`, `ContactsContract$CommonDataKinds$Website`, `ContactsContract$CommonDataKinds`, `ContactsContract$ContactNameColumns`, `ContactsContract$ContactOptionsColumns`, `ContactsContract$ContactStatusColumns`, `ContactsContract$Contacts$AggregationSuggestions$Builder`, `ContactsContract$Contacts$AggregationSuggestions`, `ContactsContract$Contacts$Data`, `ContactsContract$Contacts$Entity`, `ContactsContract$Contacts$Photo`, `ContactsContract$Contacts`, `ContactsContract$ContactsColumns`, `ContactsContract$Data`, `ContactsContract$DataColumns`, `ContactsContract$DataColumnsWithJoins`, `ContactsContract$DataUsageFeedback`, `ContactsContract$DataUsageStatColumns`, `ContactsContract$DeletedContacts`, `ContactsContract$DeletedContactsColumns`, `ContactsContract$Directory`, `ContactsContract$DisplayNameSources`, `ContactsContract$DisplayPhoto`, `ContactsContract$FullNameStyle`, `ContactsContract$Groups`, `ContactsContract$GroupsColumns`, `ContactsContract$Intents$Insert`, `ContactsContract$Intents`, `ContactsContract$PhoneLookup`, `ContactsContract$PhoneLookupColumns`, `ContactsContract$PhoneticNameStyle`, `ContactsContract$PinnedPositions`, `ContactsContract$Presence`, `ContactsContract$PresenceColumns`, `ContactsContract$Profile`, `ContactsContract$ProfileSyncState`, `ContactsContract$ProviderStatus`, `ContactsContract$QuickContact`, `ContactsContract$RawContacts$Data`, `ContactsContract$RawContacts$DisplayPhoto`, `ContactsContract$RawContacts$Entity`, `ContactsContract$RawContacts`, `ContactsContract$RawContactsColumns`, `ContactsContract$RawContactsEntity`, `ContactsContract$SearchSnippets`, `ContactsContract$Settings`, `ContactsContract$SettingsColumns`, `ContactsContract$SimAccount`, `ContactsContract$SimContacts`, `ContactsContract$StatusColumns`, `ContactsContract$StatusUpdates`, `ContactsContract$SyncColumns`, `ContactsContract$SyncState`, `ContactsContract`, `DocumentsContract$Path`, `DocumentsContract$Root`, `DocumentsProvider`, `E2eeContactKeysManager$E2eeContactKey`, `E2eeContactKeysManager$E2eeSelfKey`, `E2eeContactKeysManager`, `FontRequest`, `FontsContract$Columns`, `FontsContract$FontFamilyResult`, `FontsContract$FontInfo`, `FontsContract$FontRequestCallback`, `FontsContract`, `LiveFolders`, `MediaStore$Audio$AlbumColumns`, `MediaStore$Audio$Albums`, `MediaStore$Audio$ArtistColumns`, `MediaStore$Audio$Artists$Albums`, `MediaStore$Audio$Artists`, `MediaStore$Audio$AudioColumns`, `MediaStore$Audio$Genres$Members`, `MediaStore$Audio$Genres`, `MediaStore$Audio$GenresColumns`, `MediaStore$Audio$Playlists$Members`, `MediaStore$Audio$Playlists`, `MediaStore$Audio$PlaylistsColumns`, `MediaStore$Audio$Radio`, `MediaStore$DownloadColumns`, `MediaStore$Files$FileColumns`, `MediaStore$Images$ImageColumns`, `MediaStore$PickerMediaColumns`, `MediaStore$Video$VideoColumns`, `OpenableColumns`, `SearchRecentSuggestions`, `Settings$NameValueTable`, `Settings$Panel`, `SettingsSlicesContract`, `SimPhonebookContract$ElementaryFiles`, `SimPhonebookContract$SimRecords`, `SimPhonebookContract`, `SyncStateContract$Columns`, `SyncStateContract$Constants`, `SyncStateContract$Helpers`, `SyncStateContract`, `Telephony$BaseMmsColumns`, `Telephony$CanonicalAddressesColumns`, `Telephony$CarrierId`, `Telephony$Carriers`, `Telephony$Mms$Addr`, `Telephony$Mms$Draft`, `Telephony$Mms$Inbox`, `Telephony$Mms$Intents`, `Telephony$Mms$Outbox`, `Telephony$Mms$Part`, `Telephony$Mms$Rate`, `Telephony$Mms$Sent`, `Telephony$Mms`, `Telephony$MmsSms$PendingMessages`, `Telephony$MmsSms`, `Telephony$ServiceStateTable`, `Telephony$Sms$Conversations`, `Telephony$Sms$Draft`, `Telephony$Sms$Inbox`, `Telephony$Sms$Intents`, `Telephony$Sms$Outbox`, `Telephony$Sms$Sent`, `Telephony$Sms`, `Telephony$TextBasedSmsColumns`, `Telephony$Threads`, `Telephony$ThreadsColumns`, `Telephony`, `UserDictionary$Words`, `UserDictionary`, `VoicemailContract$Status`, `VoicemailContract$Voicemails`, `VoicemailContract`

</details>

<details><summary><code>android.renderscript</code>: 73 of 73 missing</summary>

`Allocation$MipmapControl`, `Allocation$OnBufferAvailableListener`, `Allocation`, `AllocationAdapter`, `BaseObj`, `Byte2`, `Byte3`, `Byte4`, `Double2`, `Double3`, `Double4`, `Element$Builder`, `Element$DataKind`, `Element$DataType`, `Element`, `FieldPacker`, `Float2`, `Float3`, `Float4`, `Int2`, `Int3`, `Int4`, `Long2`, `Long3`, `Long4`, `Matrix2f`, `Matrix3f`, `Matrix4f`, `RSDriverException`, `RSIllegalArgumentException`, `RSInvalidStateException`, `RSRuntimeException`, `RenderScript$ContextType`, `RenderScript$Priority`, `RenderScript$RSErrorHandler`, `RenderScript$RSMessageHandler`, `RenderScript`, `Sampler$Builder`, `Sampler$Value`, `Sampler`, `Script$Builder`, `Script$FieldBase`, `Script$FieldID`, `Script$InvokeID`, `Script$KernelID`, `Script$LaunchOptions`, `Script`, `ScriptC`, `ScriptGroup$Binding`, `ScriptGroup$Builder`, `ScriptGroup$Builder2`, `ScriptGroup$Closure`, `ScriptGroup$Future`, `ScriptGroup$Input`, `ScriptGroup`, `ScriptIntrinsic`, `ScriptIntrinsic3DLUT`, `ScriptIntrinsicBLAS`, `ScriptIntrinsicBlend`, `ScriptIntrinsicBlur`, `ScriptIntrinsicColorMatrix`, `ScriptIntrinsicConvolve3x3`, `ScriptIntrinsicConvolve5x5`, `ScriptIntrinsicHistogram`, `ScriptIntrinsicLUT`, `ScriptIntrinsicResize`, `ScriptIntrinsicYuvToRGB`, `Short2`, `Short3`, `Short4`, `Type$Builder`, `Type$CubemapFace`, `Type`

</details>

<details><summary><code>android.sax</code>: 7 of 7 missing</summary>

`Element`, `ElementListener`, `EndElementListener`, `EndTextElementListener`, `RootElement`, `StartElementListener`, `TextElementListener`

</details>

<details><summary><code>android.se.omapi</code>: 5 of 5 missing</summary>

`Channel`, `Reader`, `SEService$OnConnectedListener`, `SEService`, `Session`

</details>

<details><summary><code>android.security</code>: 17 of 18 missing</summary>

`AppUriAuthenticationPolicy$Builder`, `AppUriAuthenticationPolicy`, `AttestedKeyPair`, `ConfirmationAlreadyPresentingException`, `ConfirmationCallback`, `ConfirmationNotAvailableException`, `ConfirmationPrompt$Builder`, `ConfirmationPrompt`, `FileIntegrityManager`, `KeyChain`, `KeyChainAliasCallback`, `KeyChainException`, `KeyPairGeneratorSpec$Builder`, `KeyPairGeneratorSpec`, `KeyStoreException`, `KeyStoreParameter$Builder`, `KeyStoreParameter`

</details>

<details><summary><code>android.security.identity</code>: 26 of 26 missing</summary>

`AccessControlProfile$Builder`, `AccessControlProfile`, `AccessControlProfileId`, `AlreadyPersonalizedException`, `AuthenticationKeyMetadata`, `CipherSuiteNotSupportedException`, `CredentialDataRequest$Builder`, `CredentialDataRequest`, `CredentialDataResult$Entries`, `CredentialDataResult`, `DocTypeNotSupportedException`, `EphemeralPublicKeyNotFoundException`, `IdentityCredential`, `IdentityCredentialException`, `IdentityCredentialStore`, `InvalidReaderSignatureException`, `InvalidRequestMessageException`, `MessageDecryptionException`, `NoAuthenticationKeyAvailableException`, `PersonalizationData$Builder`, `PersonalizationData`, `PresentationSession`, `ResultData`, `SessionTranscriptMismatchException`, `UnknownAuthenticationKeyException`, `WritableIdentityCredential`

</details>

<details><summary><code>android.security.keystore</code>: 13 of 15 missing</summary>

`BackendBusyException`, `KeyExpiredException`, `KeyInfo`, `KeyNotYetValidException`, `KeyPermanentlyInvalidatedException`, `KeyProperties`, `KeyProtection$Builder`, `KeyProtection`, `SecureKeyImportUnavailableException`, `StrongBoxUnavailableException`, `UserNotAuthenticatedException`, `UserPresenceUnavailableException`, `WrappedKeyEntry`

</details>

<details><summary><code>android.service.assist.classification</code>: 1 of 1 missing</summary>

`FieldClassification`

</details>

<details><summary><code>android.service.autofill</code>: 45 of 45 missing</summary>

`AutofillService`, `BatchUpdates$Builder`, `BatchUpdates`, `CharSequenceTransformation$Builder`, `CharSequenceTransformation`, `CustomDescription$Builder`, `CustomDescription`, `Dataset$Builder`, `Dataset`, `DateTransformation`, `DateValueSanitizer`, `Field$Builder`, `Field`, `FieldClassification$Match`, `FieldClassification`, `FillCallback`, `FillContext`, `FillEventHistory$Event`, `FillEventHistory`, `FillRequest`, `FillResponse$Builder`, `FillResponse`, `ImageTransformation$Builder`, `ImageTransformation`, `InlinePresentation`, `LuhnChecksumValidator`, `OnClickAction`, `Presentations$Builder`, `Presentations`, `RegexValidator`, `Sanitizer`, `SaveCallback`, `SaveInfo$Builder`, `SaveInfo`, `SaveRequest`, `SavedDatasetsInfo`, `SavedDatasetsInfoCallback`, `TextValueSanitizer`, `Transformation`, `UserData$Builder`, `UserData`, `Validator`, `Validators`, `VisibilitySetterAction$Builder`, `VisibilitySetterAction`

</details>

<details><summary><code>android.service.carrier</code>: 9 of 9 missing</summary>

`CarrierIdentifier`, `CarrierMessagingClientService`, `CarrierMessagingService$ResultCallback`, `CarrierMessagingService$SendMmsResult`, `CarrierMessagingService$SendMultipartSmsResult`, `CarrierMessagingService$SendSmsResult`, `CarrierMessagingService`, `CarrierService`, `MessagePdu`

</details>

<details><summary><code>android.service.chooser</code>: 9 of 9 missing</summary>

`AdditionalContentContract$Columns`, `AdditionalContentContract$CursorExtraKeys`, `AdditionalContentContract$MethodNames`, `AdditionalContentContract`, `ChooserAction$Builder`, `ChooserAction`, `ChooserResult`, `ChooserTarget`, `ChooserTargetService`

</details>

<details><summary><code>android.service.controls</code>: 5 of 5 missing</summary>

`Control$StatefulBuilder`, `Control$StatelessBuilder`, `Control`, `ControlsProviderService`, `DeviceTypes`

</details>

<details><summary><code>android.service.controls.actions</code>: 5 of 5 missing</summary>

`BooleanAction`, `CommandAction`, `ControlAction`, `FloatAction`, `ModeAction`

</details>

<details><summary><code>android.service.controls.templates</code>: 8 of 8 missing</summary>

`ControlButton`, `ControlTemplate`, `RangeTemplate`, `StatelessTemplate`, `TemperatureControlTemplate`, `ThumbnailTemplate`, `ToggleRangeTemplate`, `ToggleTemplate`

</details>

<details><summary><code>android.service.credentials</code>: 17 of 17 missing</summary>

`Action`, `BeginCreateCredentialRequest`, `BeginCreateCredentialResponse$Builder`, `BeginCreateCredentialResponse`, `BeginGetCredentialOption`, `BeginGetCredentialRequest$Builder`, `BeginGetCredentialRequest`, `BeginGetCredentialResponse$Builder`, `BeginGetCredentialResponse`, `CallingAppInfo`, `ClearCredentialStateRequest`, `CreateCredentialRequest`, `CreateEntry`, `CredentialEntry`, `CredentialProviderService`, `GetCredentialRequest`, `RemoteEntry`

</details>

<details><summary><code>android.service.dreams</code>: 1 of 1 missing</summary>

`DreamService`

</details>

<details><summary><code>android.service.media</code>: 1 of 4 missing</summary>

`CameraPrewarmService`

</details>

<details><summary><code>android.service.notification</code>: 9 of 10 missing</summary>

`Condition`, `ConditionProviderService`, `NotificationListenerService$Ranking`, `NotificationListenerService$RankingMap`, `NotificationListenerService`, `ZenDeviceEffects$Builder`, `ZenDeviceEffects`, `ZenPolicy$Builder`, `ZenPolicy`

</details>

<details><summary><code>android.service.persistentdata</code>: 1 of 1 missing</summary>

`PersistentDataBlockManager`

</details>

<details><summary><code>android.service.quickaccesswallet</code>: 9 of 9 missing</summary>

`GetWalletCardsCallback`, `GetWalletCardsError`, `GetWalletCardsRequest`, `GetWalletCardsResponse`, `QuickAccessWalletService`, `SelectWalletCardRequest`, `WalletCard$Builder`, `WalletCard`, `WalletServiceEvent`

</details>

<details><summary><code>android.service.quicksettings</code>: 2 of 2 missing</summary>

`Tile`, `TileService`

</details>

<details><summary><code>android.service.restrictions</code>: 1 of 1 missing</summary>

`RestrictionsReceiver`

</details>

<details><summary><code>android.service.textservice</code>: 2 of 2 missing</summary>

`SpellCheckerService$Session`, `SpellCheckerService`

</details>

<details><summary><code>android.service.voice</code>: 14 of 14 missing</summary>

`VisibleActivityInfo`, `VoiceInteractionService`, `VoiceInteractionSession$AbortVoiceRequest`, `VoiceInteractionSession$ActivityId`, `VoiceInteractionSession$AssistState`, `VoiceInteractionSession$CommandRequest`, `VoiceInteractionSession$CompleteVoiceRequest`, `VoiceInteractionSession$ConfirmationRequest`, `VoiceInteractionSession$Insets`, `VoiceInteractionSession$PickOptionRequest`, `VoiceInteractionSession$Request`, `VoiceInteractionSession$VisibleActivityCallback`, `VoiceInteractionSession`, `VoiceInteractionSessionService`

</details>

<details><summary><code>android.service.vr</code>: 1 of 1 missing</summary>

`VrListenerService`

</details>

<details><summary><code>android.service.wallpaper</code>: 2 of 2 missing</summary>

`WallpaperService$Engine`, `WallpaperService`

</details>

<details><summary><code>android.speech</code>: 15 of 15 missing</summary>

`AlternativeSpan`, `AlternativeSpans`, `ModelDownloadListener`, `RecognitionListener`, `RecognitionPart$Builder`, `RecognitionPart`, `RecognitionService$Callback`, `RecognitionService$SupportCallback`, `RecognitionService`, `RecognitionSupport$Builder`, `RecognitionSupport`, `RecognitionSupportCallback`, `RecognizerIntent`, `RecognizerResultsIntent`, `SpeechRecognizer`

</details>

<details><summary><code>android.speech.tts</code>: 4 of 10 missing</summary>

`SynthesisCallback`, `SynthesisRequest`, `TextToSpeech$EngineInfo`, `TextToSpeechService`

</details>

<details><summary><code>android.system</code>: 14 of 14 missing</summary>

`ErrnoException`, `Int64Ref`, `Os`, `OsConstants`, `StructCmsghdr`, `StructMsghdr`, `StructPollfd`, `StructStat`, `StructStatVfs`, `StructTimespec`, `StructTimeval`, `StructUtsname`, `SystemCleaner`, `VmSocketAddress`

</details>

<details><summary><code>android.telecom</code>: 40 of 45 missing</summary>

`Call$Callback`, `Call$Details`, `Call$RttCall`, `Call`, `CallAttributes$Builder`, `CallAttributes`, `CallAudioState`, `CallControl`, `CallControlCallback`, `CallEndpoint`, `CallEndpointException`, `CallEventCallback`, `CallException`, `CallRedirectionService`, `CallScreeningService$CallResponse$Builder`, `CallScreeningService$CallResponse`, `CallScreeningService`, `Conference`, `Conferenceable`, `Connection$RttModifyStatus`, `Connection$RttTextStream`, `Connection$VideoProvider`, `Connection`, `ConnectionRequest`, `DisconnectCause`, `GatewayInfo`, `InCallService$VideoCall$Callback`, `InCallService$VideoCall`, `InCallService`, `PhoneAccountSuggestion`, `QueryLocationException`, `RemoteConference$Callback`, `RemoteConference`, `RemoteConnection$Callback`, `RemoteConnection$VideoProvider$Callback`, `RemoteConnection$VideoProvider`, `RemoteConnection`, `StatusHints`, `VideoProfile$CameraCapabilities`, `VideoProfile`

</details>

<details><summary><code>android.telephony</code>: 117 of 120 missing</summary>

`AccessNetworkConstants$AccessNetworkType`, `AccessNetworkConstants$EutranBand`, `AccessNetworkConstants$GeranBand`, `AccessNetworkConstants$NgranBands`, `AccessNetworkConstants$UtranBand`, `AccessNetworkConstants`, `AvailableNetworkInfo$Builder`, `AvailableNetworkInfo`, `BarringInfo$BarringServiceInfo`, `BarringInfo`, `CarrierConfigManager$Apn`, `CarrierConfigManager$Bsf`, `CarrierConfigManager$CarrierConfigChangeListener`, `CarrierConfigManager$Gps`, `CarrierConfigManager$Ims`, `CarrierConfigManager$ImsEmergency`, `CarrierConfigManager$ImsRtt`, `CarrierConfigManager$ImsServiceEntitlement`, `CarrierConfigManager$ImsSms`, `CarrierConfigManager$ImsSs`, `CarrierConfigManager$ImsVoice`, `CarrierConfigManager$ImsVt`, `CarrierConfigManager$ImsWfc`, `CarrierConfigManager$Iwlan`, `CarrierConfigManager`, `CellIdentity`, `CellIdentityCdma`, `CellIdentityGsm`, `CellIdentityLte`, `CellIdentityNr`, `CellIdentityTdscdma`, `CellIdentityWcdma`, `CellInfo`, `CellInfoCdma`, `CellInfoGsm`, `CellInfoLte`, `CellInfoNr`, `CellInfoTdscdma`, `CellInfoWcdma`, `CellSignalStrength`, `CellSignalStrengthCdma`, `CellSignalStrengthGsm`, `CellSignalStrengthLte`, `CellSignalStrengthNr`, `CellSignalStrengthTdscdma`, `CellSignalStrengthWcdma`, `ClosedSubscriberGroupInfo`, `DataFailCause`, `DisconnectCause`, `IccOpenLogicalChannelResponse`, `MbmsDownloadSession`, `MbmsGroupCallSession`, `MbmsStreamingSession`, `NeighboringCellInfo`, `NetworkRegistrationInfo`, `NetworkScan`, `NetworkScanRequest`, `PhoneNumberFormattingTextWatcher`, `PhoneNumberUtils`, `PhysicalChannelConfig`, `PreciseDataConnectionState`, `RadioAccessSpecifier`, `ServiceState`, `SignalStrength`, `SignalStrengthUpdateRequest$Builder`, `SignalStrengthUpdateRequest`, `SignalThresholdInfo$Builder`, `SignalThresholdInfo`, `SmsManager$FinancialSmsCallback`, `SmsManager`, `SmsMessage$MessageClass`, `SmsMessage$SubmitPdu`, `SmsMessage`, `SubscriptionInfo`, `SubscriptionManager$OnOpportunisticSubscriptionsChangedListener`, `SubscriptionManager$OnSubscriptionsChangedListener`, `SubscriptionManager`, `SubscriptionPlan$Builder`, `SubscriptionPlan`, `TelephonyCallback$ActiveDataSubscriptionIdListener`, `TelephonyCallback$BarringInfoListener`, `TelephonyCallback$CallDisconnectCauseListener`, `TelephonyCallback$CallForwardingIndicatorListener`, `TelephonyCallback$CallStateListener`, `TelephonyCallback$CarrierNetworkListener`, `TelephonyCallback$CellInfoListener`, `TelephonyCallback$CellLocationListener`, `TelephonyCallback$DataActivationStateListener`, `TelephonyCallback$DataActivityListener`, `TelephonyCallback$DataConnectionStateListener`, `TelephonyCallback$DisplayInfoListener`, `TelephonyCallback$EmergencyNumberListListener`, `TelephonyCallback$ImsCallDisconnectCauseListener`, `TelephonyCallback$MessageWaitingIndicatorListener`, `TelephonyCallback$PhysicalChannelConfigListener`, `TelephonyCallback$PreciseDataConnectionStateListener`, `TelephonyCallback$RegistrationFailedListener`, `TelephonyCallback$ServiceStateListener`, `TelephonyCallback$SignalStrengthsListener`, `TelephonyCallback$UserMobileDataStateListener`, `TelephonyCallback`, `TelephonyDisplayInfo`, `TelephonyManager$CallComposerException`, `TelephonyManager$CellInfoCallback`, `TelephonyManager$ModemErrorException`, `TelephonyManager$NetworkSlicingException`, `TelephonyManager$TimeoutException`, `TelephonyManager$UssdResponseCallback`, `TelephonyScanManager$NetworkScanCallback`, `TelephonyScanManager`, `UiccCardInfo`, `UiccPortInfo`, `VisualVoicemailService$VisualVoicemailTask`, `VisualVoicemailService`, `VisualVoicemailSms`, `VisualVoicemailSmsFilterSettings$Builder`, `VisualVoicemailSmsFilterSettings`

</details>

<details><summary><code>android.telephony.data</code>: 9 of 9 missing</summary>

`ApnSetting$Builder`, `ApnSetting`, `NetworkSliceInfo$Builder`, `NetworkSliceInfo`, `NetworkSlicingConfig`, `RouteSelectionDescriptor`, `TrafficDescriptor$Builder`, `TrafficDescriptor`, `UrspRule`

</details>

<details><summary><code>android.telephony.emergency</code>: 1 of 1 missing</summary>

`EmergencyNumber`

</details>

<details><summary><code>android.telephony.euicc</code>: 4 of 4 missing</summary>

`DownloadableSubscription$Builder`, `DownloadableSubscription`, `EuiccInfo`, `EuiccManager`

</details>

<details><summary><code>android.telephony.gsm</code>: 4 of 5 missing</summary>

`SmsManager`, `SmsMessage$MessageClass`, `SmsMessage$SubmitPdu`, `SmsMessage`

</details>

<details><summary><code>android.telephony.ims</code>: 14 of 14 missing</summary>

`ImsException`, `ImsManager`, `ImsMmTelManager$CapabilityCallback`, `ImsMmTelManager`, `ImsRcsManager`, `ImsReasonInfo`, `ImsRegistrationAttributes`, `ImsStateCallback`, `ProvisioningManager$FeatureProvisioningCallback`, `ProvisioningManager`, `RcsUceAdapter`, `RegistrationManager$RegistrationCallback`, `RegistrationManager`, `SipDetails`

</details>

<details><summary><code>android.telephony.ims.feature</code>: 2 of 2 missing</summary>

`MmTelFeature$MmTelCapabilities`, `MmTelFeature`

</details>

<details><summary><code>android.telephony.ims.stub</code>: 1 of 1 missing</summary>

`ImsRegistrationImplBase`

</details>

<details><summary><code>android.telephony.mbms</code>: 22 of 22 missing</summary>

`DownloadProgressListener`, `DownloadRequest$Builder`, `DownloadRequest`, `DownloadStatusListener`, `FileInfo`, `FileServiceInfo`, `GroupCall`, `GroupCallCallback`, `MbmsDownloadReceiver`, `MbmsDownloadSessionCallback`, `MbmsErrors$DownloadErrors`, `MbmsErrors$GeneralErrors`, `MbmsErrors$GroupCallErrors`, `MbmsErrors$InitializationErrors`, `MbmsErrors$StreamingErrors`, `MbmsErrors`, `MbmsGroupCallSessionCallback`, `MbmsStreamingSessionCallback`, `ServiceInfo`, `StreamingService`, `StreamingServiceCallback`, `StreamingServiceInfo`

</details>

<details><summary><code>android.text</code>: 25 of 64 missing</summary>

`AlteredCharSequence`, `Annotation`, `AutoText`, `BidiFormatter$Builder`, `ClipboardManager`, `DynamicLayout$Builder`, `EmojiConsistency`, `GraphemeClusterSegmentFinder`, `Highlights$Builder`, `Highlights`, `InputFilter$AllCaps`, `Layout$Builder`, `Layout$TextInclusionStrategy`, `LoginFilter$PasswordFilterGMail`, `LoginFilter$UsernameFilterGMail`, `LoginFilter$UsernameFilterGeneric`, `LoginFilter`, `PrecomputedText$Params$Builder`, `PrecomputedText$Params`, `PrecomputedText`, `SegmentFinder$PrescribedSegmentFinder`, `SegmentFinder`, `TextShaper$GlyphsConsumer`, `TextShaper`, `WordSegmentFinder`

</details>

<details><summary><code>android.text.format</code>: 1 of 4 missing</summary>

`Time`

</details>

<details><summary><code>android.text.method</code>: 12 of 25 missing</summary>

`ArrowKeyMovementMethod`, `BaseMovementMethod`, `CharacterPickerDialog`, `DateKeyListener`, `DateTimeKeyListener`, `HideReturnsTransformationMethod`, `MultiTapKeyListener`, `ReplacementTransformationMethod`, `ScrollingMovementMethod`, `SingleLineTransformationMethod`, `TimeKeyListener`, `Touch`

</details>

<details><summary><code>android.text.style</code>: 31 of 61 missing</summary>

`DrawableMarginSpan`, `EasyEditSpan`, `IconMarginSpan`, `LeadingMarginSpan$LeadingMarginSpan2`, `LineBackgroundSpan$Standard`, `LineBackgroundSpan`, `LineBreakConfigSpan`, `LineHeightSpan$Standard`, `MaskFilterSpan`, `QuoteSpan`, `SuggestionRangeSpan`, `SuggestionSpan`, `TabStopSpan$Standard`, `TabStopSpan`, `TextAppearanceSpan`, `TtsSpan$Builder`, `TtsSpan$CardinalBuilder`, `TtsSpan$DateBuilder`, `TtsSpan$DecimalBuilder`, `TtsSpan$DigitsBuilder`, `TtsSpan$ElectronicBuilder`, `TtsSpan$FractionBuilder`, `TtsSpan$MeasureBuilder`, `TtsSpan$MoneyBuilder`, `TtsSpan$OrdinalBuilder`, `TtsSpan$SemioticClassBuilder`, `TtsSpan$TelephoneBuilder`, `TtsSpan$TextBuilder`, `TtsSpan$TimeBuilder`, `TtsSpan$VerbatimBuilder`, `TtsSpan`

</details>

<details><summary><code>android.text.util</code>: 2 of 5 missing</summary>

`Rfc822Token`, `Rfc822Tokenizer`

</details>

<details><summary><code>android.transition</code>: 23 of 26 missing</summary>

`ArcMotion`, `AutoTransition`, `ChangeBounds`, `ChangeClipBounds`, `ChangeImageTransform`, `ChangeScroll`, `ChangeTransform`, `CircularPropagation`, `Explode`, `Fade`, `PathMotion`, `PatternPathMotion`, `Scene`, `SidePropagation`, `Slide`, `Transition$EpicenterCallback`, `TransitionInflater`, `TransitionListenerAdapter`, `TransitionManager`, `TransitionPropagation`, `TransitionSet`, `Visibility`, `VisibilityPropagation`

</details>

<details><summary><code>android.util</code>: 36 of 66 missing</summary>

`ArrayMap`, `ArraySet`, `Base64DataException`, `Base64InputStream`, `CloseGuard`, `Config`, `DebugUtils`, `Dumpable`, `DumpableContainer`, `EventLog$Event`, `EventLog`, `EventLogTags$Description`, `EventLogTags`, `FloatMath`, `Half`, `IntProperty`, `LayoutDirection`, `LogPrinter`, `MonthDisplayHelper`, `MutableBoolean`, `MutableByte`, `MutableChar`, `MutableDouble`, `MutableFloat`, `MutableInt`, `MutableLong`, `MutableShort`, `PrintStreamPrinter`, `PrintWriterPrinter`, `Printer`, `Rational`, `StatsLog`, `StringBuilderPrinter`, `TimeFormatException`, `TimeUtils`, `TimingLogger`

</details>

<details><summary><code>android.util.proto</code>: 1 of 1 missing</summary>

`ProtoOutputStream`

</details>

<details><summary><code>android.view</code>: 81 of 188 missing</summary>

`ActionProvider$VisibilityListener`, `ActionProvider`, `AttachedSurfaceControl$OnBufferTransformHintChangedListener`, `AttachedSurfaceControl`, `Choreographer$FrameData`, `Choreographer$FrameTimeline`, `Choreographer$VsyncCallback`, `CollapsibleActionView`, `ContentInfo$Builder`, `ContentInfo`, `DisplayCutout$Builder`, `DisplayShape`, `DragAndDropPermissions`, `FocusFinder`, `FrameStats`, `HapticFeedbackConstants`, `InputDevice$ViewBehavior`, `InputQueue$Callback`, `InputQueue`, `KeyCharacterMap$UnavailableException`, `KeyboardShortcutGroup`, `KeyboardShortcutInfo`, `MotionPredictor`, `OnReceiveContentListener`, `OrientationListener`, `PixelCopy$Request$Builder`, `PixelCopy$Request`, `PixelCopy$Result`, `RoundedCorner`, `ScrollCaptureCallback`, `ScrollCaptureSession`, `ScrollCaptureTarget`, `ScrollFeedbackProvider`, `SearchEvent`, `SurfaceControl$Builder`, `SurfaceControl$Transaction`, `SurfaceControl$TransactionCommittedListener`, `SurfaceControl$TransactionStats`, `SurfaceControl$TrustedPresentationThresholds`, `SurfaceControl`, `SurfaceControlInputReceiver`, `SurfaceControlViewHost$SurfacePackage`, `SurfaceControlViewHost`, `VerifiedInputEvent`, `VerifiedKeyEvent`, `VerifiedMotionEvent`, `View$DragShadowBuilder`, `View$OnCapturedPointerListener`, `View$OnContextClickListener`, `View$OnHoverListener`, `View$OnUnhandledKeyEventListener`, `ViewDebug$CapturedViewProperty`, `ViewDebug$ExportedProperty`, `ViewDebug$FlagToString`, `ViewDebug$HierarchyTraceType`, `ViewDebug$IntToString`, `ViewDebug$RecyclerTraceType`, `ViewDebug`, `ViewGroupOverlay`, `ViewStructure$HtmlInfo$Builder`, `ViewStructure$HtmlInfo`, `ViewStructure`, `ViewTreeObserver$OnGlobalFocusChangeListener`, `ViewTreeObserver$OnWindowAttachListener`, `ViewTreeObserver$OnWindowVisibilityChangeListener`, `Window$OnRestrictedCaptionAreaChangedListener`, `WindowAnimationFrameStats`, `WindowContentFrameStats`, `WindowId$FocusObserver`, `WindowInsets$Side`, `WindowInsets$Type`, `WindowInsetsAnimation$Bounds`, `WindowInsetsAnimation$Callback`, `WindowInsetsAnimation`, `WindowInsetsAnimationControlListener`, `WindowInsetsAnimationController`, `WindowInsetsController$OnControllableInsetsChangedListener`, `WindowInsetsController`, `WindowManager$BadTokenException`, `WindowManager$InvalidDisplayException`, `WindowMetrics`

</details>

<details><summary><code>android.view.accessibility</code>: 13 of 23 missing</summary>

`AccessibilityEventSource`, `AccessibilityManager$AccessibilityServicesStateChangeListener`, `AccessibilityManager$AudioDescriptionRequestedChangeListener`, `AccessibilityNodeInfo$CollectionInfo$Builder`, `AccessibilityNodeInfo$CollectionInfo`, `AccessibilityNodeInfo$CollectionItemInfo$Builder`, `AccessibilityNodeInfo$CollectionItemInfo`, `AccessibilityNodeInfo$ExtraRenderingInfo`, `AccessibilityNodeInfo$RangeInfo`, `AccessibilityNodeInfo$TouchDelegateInfo`, `AccessibilityRequestPreparer`, `AccessibilityWindowInfo`, `CaptioningManager$CaptioningChangeListener`

</details>

<details><summary><code>android.view.animation</code>: 3 of 26 missing</summary>

`GridLayoutAnimationController$AnimationParameters`, `GridLayoutAnimationController`, `LayoutAnimationController$AnimationParameters`

</details>

<details><summary><code>android.view.autofill</code>: 2 of 6 missing</summary>

`VirtualViewFillInfo$Builder`, `VirtualViewFillInfo`

</details>

<details><summary><code>android.view.contentcapture</code>: 11 of 11 missing</summary>

`ContentCaptureCondition`, `ContentCaptureContext$Builder`, `ContentCaptureContext`, `ContentCaptureManager`, `ContentCaptureSession`, `ContentCaptureSessionId`, `DataRemovalRequest$Builder`, `DataRemovalRequest$LocusIdRequest`, `DataRemovalRequest`, `DataShareRequest`, `DataShareWriteAdapter`

</details>

<details><summary><code>android.view.displayhash</code>: 4 of 4 missing</summary>

`DisplayHash`, `DisplayHashManager`, `DisplayHashResultCallback`, `VerifiedDisplayHash`

</details>

<details><summary><code>android.view.inputmethod</code>: 43 of 55 missing</summary>

`CompletionInfo`, `ConnectionlessHandwritingCallback`, `CorrectionInfo`, `DeleteGesture$Builder`, `DeleteGesture`, `DeleteRangeGesture$Builder`, `DeleteRangeGesture`, `EditorBoundsInfo$Builder`, `EditorBoundsInfo`, `HandwritingGesture`, `InlineSuggestion`, `InlineSuggestionInfo`, `InlineSuggestionsRequest$Builder`, `InlineSuggestionsRequest`, `InlineSuggestionsResponse`, `InputBinding`, `InputContentInfo`, `InputMethod$SessionCallback`, `InputMethod`, `InputMethodSession$EventCallback`, `InputMethodSession`, `InsertGesture$Builder`, `InsertGesture`, `InsertModeGesture$Builder`, `InsertModeGesture`, `JoinOrSplitGesture$Builder`, `JoinOrSplitGesture`, `PreviewableHandwritingGesture`, `RemoveSpaceGesture$Builder`, `RemoveSpaceGesture`, `SelectGesture$Builder`, `SelectGesture`, `SelectRangeGesture$Builder`, `SelectRangeGesture`, `SurroundingText`, `TextAppearanceInfo$Builder`, `TextAppearanceInfo`, `TextAttribute$Builder`, `TextAttribute`, `TextBoundsInfo$Builder`, `TextBoundsInfo`, `TextBoundsInfoResult`, `TextSnapshot`

</details>

<details><summary><code>android.view.inspector</code>: 10 of 10 missing</summary>

`InspectionCompanion$UninitializedPropertyMapException`, `InspectionCompanion`, `InspectionCompanionProvider`, `IntFlagMapping`, `PropertyMapper$PropertyConflictException`, `PropertyMapper`, `PropertyReader$PropertyTypeMismatchException`, `PropertyReader`, `StaticInspectionCompanionProvider`, `WindowInspector`

</details>

<details><summary><code>android.view.textclassifier</code>: 44 of 44 missing</summary>

`ConversationAction$Builder`, `ConversationAction`, `ConversationActions$Message$Builder`, `ConversationActions$Message`, `ConversationActions$Request$Builder`, `ConversationActions$Request`, `ConversationActions`, `SelectionEvent`, `TextClassification$Builder`, `TextClassification$Request$Builder`, `TextClassification$Request`, `TextClassification`, `TextClassificationContext$Builder`, `TextClassificationContext`, `TextClassificationManager`, `TextClassificationSessionFactory`, `TextClassificationSessionId`, `TextClassifier$EntityConfig$Builder`, `TextClassifier$EntityConfig`, `TextClassifier`, `TextClassifierEvent$Builder`, `TextClassifierEvent$ConversationActionsEvent$Builder`, `TextClassifierEvent$ConversationActionsEvent`, `TextClassifierEvent$LanguageDetectionEvent$Builder`, `TextClassifierEvent$LanguageDetectionEvent`, `TextClassifierEvent$TextLinkifyEvent$Builder`, `TextClassifierEvent$TextLinkifyEvent`, `TextClassifierEvent$TextSelectionEvent$Builder`, `TextClassifierEvent$TextSelectionEvent`, `TextClassifierEvent`, `TextLanguage$Builder`, `TextLanguage$Request$Builder`, `TextLanguage$Request`, `TextLanguage`, `TextLinks$Builder`, `TextLinks$Request$Builder`, `TextLinks$Request`, `TextLinks$TextLink`, `TextLinks$TextLinkSpan`, `TextLinks`, `TextSelection$Builder`, `TextSelection$Request$Builder`, `TextSelection$Request`, `TextSelection`

</details>

<details><summary><code>android.view.textservice</code>: 5 of 10 missing</summary>

`SpellCheckerInfo`, `SpellCheckerSession$SpellCheckerSessionParams$Builder`, `SpellCheckerSession$SpellCheckerSessionParams`, `SpellCheckerSubtype`, `TextInfo`

</details>

<details><summary><code>android.view.translation</code>: 20 of 20 missing</summary>

`TranslationCapability`, `TranslationContext$Builder`, `TranslationContext`, `TranslationManager`, `TranslationRequest$Builder`, `TranslationRequest`, `TranslationRequestValue`, `TranslationResponse$Builder`, `TranslationResponse`, `TranslationResponseValue$Builder`, `TranslationResponseValue`, `TranslationSpec`, `Translator`, `UiTranslationManager`, `UiTranslationStateCallback`, `ViewTranslationCallback`, `ViewTranslationRequest$Builder`, `ViewTranslationRequest`, `ViewTranslationResponse$Builder`, `ViewTranslationResponse`

</details>

<details><summary><code>android.webkit</code>: 39 of 60 missing</summary>

`ClientCertRequest`, `CookieSyncManager`, `DateSorter`, `HttpAuthHandler`, `JavascriptInterface`, `JsPromptResult`, `PluginStub`, `RenderProcessGoneDetail`, `SafeBrowsingResponse`, `ServiceWorkerClient`, `ServiceWorkerController`, `ServiceWorkerWebSettings`, `TracingConfig$Builder`, `TracingConfig`, `TracingController`, `WebBackForwardList`, `WebChromeClient$FileChooserParams`, `WebHistoryItem`, `WebIconDatabase$IconListener`, `WebIconDatabase`, `WebMessage`, `WebMessagePort$WebMessageCallback`, `WebMessagePort`, `WebResourceError`, `WebResourceRequest`, `WebResourceResponse`, `WebSettings$LayoutAlgorithm`, `WebSettings$TextSize`, `WebSettings$ZoomDensity`, `WebStorage$Origin`, `WebStorage$QuotaUpdater`, `WebView$HitTestResult`, `WebView$PictureListener`, `WebView$VisualStateCallback`, `WebView$WebViewTransport`, `WebViewDatabase`, `WebViewFragment`, `WebViewRenderProcess`, `WebViewRenderProcessClient`

</details>

<details><summary><code>android.widget</code>: 102 of 185 missing</summary>

`AbsListView$MultiChoiceModeListener`, `AbsListView$RecyclerListener`, `AbsSpinner`, `AbsoluteLayout$LayoutParams`, `AbsoluteLayout`, `ActionMenuView$LayoutParams`, `ActionMenuView$OnMenuItemClickListener`, `ActionMenuView`, `AdapterView$AdapterContextMenuInfo`, `AdapterViewAnimator`, `AdapterViewFlipper`, `Advanceable`, `AlphabetIndexer`, `AnalogClock`, `CalendarView$OnDateChangeListener`, `CalendarView`, `Chronometer$OnChronometerTickListener`, `Chronometer`, `CursorAdapter`, `CursorTreeAdapter`, `DatePicker$OnDateChangedListener`, `DatePicker`, `DialerFilter`, `DigitalClock`, `ExpandableListView$ExpandableListContextMenuInfo`, `FilterQueryProvider`, `Gallery$LayoutParams`, `Gallery`, `GridLayout$Alignment`, `GridLayout$LayoutParams`, `GridLayout$Spec`, `GridLayout`, `ImageSwitcher`, `ListPopupWindow`, `Magnifier$Builder`, `Magnifier`, `MediaController$MediaPlayerControl`, `MediaController`, `MultiAutoCompleteTextView$CommaTokenizer`, `MultiAutoCompleteTextView$Tokenizer`, `MultiAutoCompleteTextView`, `NumberPicker$Formatter`, `NumberPicker$OnScrollListener`, `NumberPicker$OnValueChangeListener`, `NumberPicker`, `QuickContactBadge`, `RadioGroup$LayoutParams`, `RadioGroup$OnCheckedChangeListener`, `RadioGroup`, `RatingBar$OnRatingBarChangeListener`, `RatingBar`, `RemoteViews$ActionException`, `RemoteViews$DrawInstructions$Builder`, `RemoteViews$DrawInstructions`, `RemoteViews$RemoteCollectionItems$Builder`, `RemoteViews$RemoteCollectionItems`, `RemoteViews$RemoteResponse`, `RemoteViews$RemoteView`, `RemoteViews$RemoteViewOutlineProvider`, `RemoteViewsService$RemoteViewsFactory`, `RemoteViewsService`, `ResourceCursorAdapter`, `ResourceCursorTreeAdapter`, `SearchView$OnSuggestionListener`, `SectionIndexer`, `ShareActionProvider$OnShareTargetSelectedListener`, `ShareActionProvider`, `SimpleAdapter$ViewBinder`, `SimpleAdapter`, `SimpleCursorAdapter$CursorToStringConverter`, `SimpleCursorAdapter$ViewBinder`, `SimpleCursorAdapter`, `SimpleCursorTreeAdapter$ViewBinder`, `SimpleCursorTreeAdapter`, `SimpleExpandableListAdapter`, `SlidingDrawer$OnDrawerCloseListener`, `SlidingDrawer$OnDrawerOpenListener`, `SlidingDrawer$OnDrawerScrollListener`, `SlidingDrawer`, `StackView`, `Switch`, `TabHost$OnTabChangeListener`, `TabHost$TabContentFactory`, `TabHost$TabSpec`, `TabHost`, `TabWidget`, `TextClock`, `TextSwitcher`, `ThemedSpinnerAdapter`, `TimePicker$OnTimeChangedListener`, `TimePicker`, `Toast$Callback`, `ToggleButton`, `Toolbar$LayoutParams`, `Toolbar$OnMenuItemClickListener`, `TwoLineListItem`, `VideoView`, `ViewFlipper`, `ZoomButton`, `ZoomButtonsController$OnZoomListener`, `ZoomButtonsController`, `ZoomControls`

</details>

<details><summary><code>android.widget.inline</code>: 4 of 4 missing</summary>

`InlineContentView$SurfaceControlCallback`, `InlineContentView`, `InlinePresentationSpec$Builder`, `InlinePresentationSpec`

</details>

<details><summary><code>android.window</code>: 10 of 10 missing</summary>

`BackEvent`, `InputTransferToken`, `OnBackAnimationCallback`, `OnBackInvokedCallback`, `OnBackInvokedDispatcher`, `SplashScreen$OnExitAnimationListener`, `SplashScreen`, `SplashScreenView`, `SurfaceSyncGroup`, `TrustedPresentationThresholds`

</details>

