# Missing Android libraries

Generated 2026-11-21 from the working tree at `372bbc8` (VERSION 0.350.1, with uncommitted framework changes) against the pinned Android 15 / API 35 SDK jar (`platform-35_r02.zip`).

"Missing" means the class name is absent from both `build/framework/classes` and the ART boot classpath jars the runtime loads (core-oj, core-libart, okhttp, bouncycastle, wolfssljni from the local art_standalone build). The boot jars supply 179 of the present classes, mostly `android.icu` and `android.system`. A class being present says nothing by itself about full CTS behavior, so the "partly present" packages below are not complete packages. `android.R` resource classes are excluded. `java.*` and `javax.*` come from the ART core libraries, not this tree, and are not measured here.

**4,445 of 4,445 `android.*` class names present (100.0%); 0 missing.**

Sections: [framework areas](#1-framework-areas) · [packages with nothing](#2-packages-with-no-classes-at-all) · [packages partly present](#3-packages-partly-present) · [other namespaces and optional jars](#4-other-namespaces-and-optional-sdk-jars) · [NDK libraries](#5-ndk-native-libraries) · [every missing class](#6-every-missing-class)

## 1. Framework areas

Sorted by number of missing classes.

| Area | Present | Reference | Missing |
|---|--:|--:|--:|
| `android` | 3 | 3 | 0 |
| `android.accessibilityservice` | 25 | 25 | 0 |
| `android.accounts` | 13 | 13 | 0 |
| `android.adservices` | 145 | 145 | 0 |
| `android.animation` | 29 | 29 | 0 |
| `android.annotation` | 5 | 5 | 0 |
| `android.app` | 343 | 343 | 0 |
| `android.appwidget` | 5 | 5 | 0 |
| `android.bluetooth` | 64 | 64 | 0 |
| `android.companion` | 19 | 19 | 0 |
| `android.content` | 178 | 178 | 0 |
| `android.credentials` | 19 | 19 | 0 |
| `android.crypto` | 2 | 2 | 0 |
| `android.database` | 58 | 58 | 0 |
| `android.devicelock` | 2 | 2 | 0 |
| `android.drm` | 22 | 22 | 0 |
| `android.gesture` | 13 | 13 | 0 |
| `android.graphics` | 197 | 197 | 0 |
| `android.hardware` | 138 | 138 | 0 |
| `android.health` | 182 | 182 | 0 |
| `android.icu` | 214 | 214 | 0 |
| `android.inputmethodservice` | 13 | 13 | 0 |
| `android.location` | 41 | 41 | 0 |
| `android.media` | 436 | 436 | 0 |
| `android.mtp` | 7 | 7 | 0 |
| `android.net` | 317 | 317 | 0 |
| `android.nfc` | 32 | 32 | 0 |
| `android.opengl` | 36 | 36 | 0 |
| `android.os` | 143 | 143 | 0 |
| `android.preference` | 24 | 24 | 0 |
| `android.print` | 22 | 22 | 0 |
| `android.printservice` | 5 | 5 | 0 |
| `android.provider` | 232 | 232 | 0 |
| `android.renderscript` | 73 | 73 | 0 |
| `android.sax` | 7 | 7 | 0 |
| `android.se` | 5 | 5 | 0 |
| `android.security` | 59 | 59 | 0 |
| `android.service` | 146 | 146 | 0 |
| `android.speech` | 25 | 25 | 0 |
| `android.system` | 14 | 14 | 0 |
| `android.telecom` | 45 | 45 | 0 |
| `android.telephony` | 179 | 179 | 0 |
| `android.text` | 159 | 159 | 0 |
| `android.transition` | 26 | 26 | 0 |
| `android.util` | 67 | 67 | 0 |
| `android.view` | 397 | 397 | 0 |
| `android.webkit` | 60 | 60 | 0 |
| `android.widget` | 189 | 189 | 0 |
| `android.window` | 10 | 10 | 0 |

## 2. Packages with no classes at all

0 of 186 packages, 0 classes.

| Package | Classes |
|---|--:|

## 3. Packages partly present

0 packages, 0 classes missing.

| Package | Present | Reference | Missing |
|---|--:|--:|--:|

Every class name present in 186 packages: `android`, `android.accessibilityservice`, `android.accounts`, `android.adservices`, `android.adservices.adid`, `android.adservices.adselection`, `android.adservices.appsetid`, `android.adservices.common`, `android.adservices.customaudience`, `android.adservices.exceptions`, `android.adservices.measurement`, `android.adservices.ondevicepersonalization`, `android.adservices.signals`, `android.adservices.topics`, `android.animation`, `android.annotation`, `android.app`, `android.app.admin`, `android.app.appsearch`, `android.app.appsearch.exceptions`, `android.app.appsearch.observer`, `android.app.appsearch.util`, `android.app.assist`, `android.app.backup`, `android.app.blob`, `android.app.job`, `android.app.people`, `android.app.role`, `android.app.sdksandbox`, `android.app.sdksandbox.sdkprovider`, `android.app.slice`, `android.app.usage`, `android.appwidget`, `android.bluetooth`, `android.bluetooth.le`, `android.companion`, `android.companion.virtual`, `android.content`, `android.content.om`, `android.content.pm`, `android.content.pm.verify.domain`, `android.content.res`, `android.content.res.loader`, `android.credentials`, `android.crypto.hpke`, `android.database`, `android.database.sqlite`, `android.devicelock`, `android.drm`, `android.gesture`, `android.graphics`, `android.graphics.drawable`, `android.graphics.drawable.shapes`, `android.graphics.fonts`, `android.graphics.pdf`, `android.graphics.pdf.content`, `android.graphics.pdf.models`, `android.graphics.pdf.models.selection`, `android.graphics.text`, `android.hardware`, `android.hardware.biometrics`, `android.hardware.camera2`, `android.hardware.camera2.params`, `android.hardware.display`, `android.hardware.fingerprint`, `android.hardware.input`, `android.hardware.lights`, `android.hardware.usb`, `android.health.connect`, `android.health.connect.changelog`, `android.health.connect.datatypes`, `android.health.connect.datatypes.units`, `android.icu.lang`, `android.icu.math`, `android.icu.number`, `android.icu.text`, `android.icu.util`, `android.inputmethodservice`, `android.location`, `android.location.altitude`, `android.location.provider`, `android.media`, `android.media.audiofx`, `android.media.browse`, `android.media.effect`, `android.media.metrics`, `android.media.midi`, `android.media.projection`, `android.media.session`, `android.media.tv`, `android.media.tv.interactive`, `android.mtp`, `android.net`, `android.net.eap`, `android.net.http`, `android.net.ipsec.ike`, `android.net.ipsec.ike.exceptions`, `android.net.nsd`, `android.net.rtp`, `android.net.sip`, `android.net.ssl`, `android.net.vcn`, `android.net.wifi`, `android.net.wifi.aware`, `android.net.wifi.hotspot2`, `android.net.wifi.hotspot2.omadm`, `android.net.wifi.hotspot2.pps`, `android.net.wifi.p2p`, `android.net.wifi.p2p.nsd`, `android.net.wifi.rtt`, `android.nfc`, `android.nfc.cardemulation`, `android.nfc.tech`, `android.opengl`, `android.os`, `android.os.ext`, `android.os.health`, `android.os.storage`, `android.os.strictmode`, `android.preference`, `android.print`, `android.print.pdf`, `android.printservice`, `android.provider`, `android.renderscript`, `android.sax`, `android.se.omapi`, `android.security`, `android.security.identity`, `android.security.keystore`, `android.service.assist.classification`, `android.service.autofill`, `android.service.carrier`, `android.service.chooser`, `android.service.controls`, `android.service.controls.actions`, `android.service.controls.templates`, `android.service.credentials`, `android.service.dreams`, `android.service.media`, `android.service.notification`, `android.service.persistentdata`, `android.service.quickaccesswallet`, `android.service.quicksettings`, `android.service.restrictions`, `android.service.textservice`, `android.service.voice`, `android.service.vr`, `android.service.wallpaper`, `android.speech`, `android.speech.tts`, `android.system`, `android.telecom`, `android.telephony`, `android.telephony.cdma`, `android.telephony.data`, `android.telephony.emergency`, `android.telephony.euicc`, `android.telephony.gsm`, `android.telephony.ims`, `android.telephony.ims.feature`, `android.telephony.ims.stub`, `android.telephony.mbms`, `android.text`, `android.text.format`, `android.text.method`, `android.text.style`, `android.text.util`, `android.transition`, `android.util`, `android.util.proto`, `android.view`, `android.view.accessibility`, `android.view.animation`, `android.view.autofill`, `android.view.contentcapture`, `android.view.displayhash`, `android.view.inputmethod`, `android.view.inspector`, `android.view.textclassifier`, `android.view.textservice`, `android.view.translation`, `android.webkit`, `android.widget`, `android.widget.inline`, `android.window`.

## 4. Other namespaces and optional SDK jars

Non-`android.*` packages shipped in the SDK `android.jar`, matched against the same framework directory and boot jars.

| Package | Present | Reference |
|---|--:|--:|
| `androidx.annotation` | 0 | 2 |
| `dalvik.annotation` | 2 | 2 |
| `dalvik.annotation.optimization` | 2 | 2 |
| `dalvik.bytecode` | 2 | 2 |
| `dalvik.system` | 6 | 10 |
| `java.awt.font` | 3 | 3 |
| `java.beans` | 5 | 5 |
| `java.io` | 81 | 82 |
| `java.lang` | 103 | 108 |
| `java.lang.annotation` | 12 | 12 |
| `java.lang.invoke` | 13 | 16 |
| `java.lang.ref` | 7 | 7 |
| `java.lang.reflect` | 24 | 25 |
| `java.lang.runtime` | 0 | 1 |
| `java.math` | 4 | 4 |
| `java.net` | 64 | 64 |
| `java.nio` | 14 | 14 |
| `java.nio.channels` | 56 | 56 |
| `java.nio.channels.spi` | 6 | 6 |
| `java.nio.charset` | 12 | 12 |
| `java.nio.charset.spi` | 1 | 1 |
| `java.nio.file` | 47 | 47 |
| `java.nio.file.attribute` | 25 | 25 |
| `java.nio.file.spi` | 2 | 2 |
| `java.security` | 86 | 92 |
| `java.security.acl` | 8 | 8 |
| `java.security.cert` | 53 | 54 |
| `java.security.interfaces` | 13 | 19 |
| `java.security.spec` | 27 | 35 |
| `java.sql` | 48 | 48 |
| `java.text` | 30 | 30 |
| `java.time` | 18 | 19 |
| `java.time.chrono` | 21 | 21 |
| `java.time.format` | 8 | 8 |
| `java.time.temporal` | 16 | 16 |
| `java.time.zone` | 5 | 5 |
| `java.util` | 128 | 134 |
| `java.util.concurrent` | 71 | 72 |
| `java.util.concurrent.atomic` | 16 | 16 |
| `java.util.concurrent.locks` | 14 | 14 |
| `java.util.function` | 43 | 43 |
| `java.util.jar` | 11 | 11 |
| `java.util.logging` | 17 | 17 |
| `java.util.prefs` | 9 | 9 |
| `java.util.random` | 0 | 7 |
| `java.util.regex` | 4 | 4 |
| `java.util.stream` | 13 | 16 |
| `java.util.zip` | 20 | 21 |
| `javax.annotation.processing` | 0 | 1 |
| `javax.crypto` | 24 | 24 |
| `javax.crypto.interfaces` | 4 | 4 |
| `javax.crypto.spec` | 16 | 17 |
| `javax.microedition.khronos.egl` | 7 | 7 |
| `javax.microedition.khronos.opengles` | 2 | 6 |
| `javax.net` | 2 | 2 |
| `javax.net.ssl` | 43 | 43 |
| `javax.security.auth` | 6 | 6 |
| `javax.security.auth.callback` | 4 | 4 |
| `javax.security.auth.login` | 1 | 1 |
| `javax.security.auth.x500` | 1 | 2 |
| `javax.security.cert` | 7 | 7 |
| `javax.sql` | 15 | 15 |
| `javax.xml` | 1 | 1 |
| `javax.xml.datatype` | 6 | 6 |
| `javax.xml.namespace` | 2 | 2 |
| `javax.xml.parsers` | 6 | 6 |
| `javax.xml.transform` | 12 | 12 |
| `javax.xml.transform.dom` | 3 | 3 |
| `javax.xml.transform.sax` | 5 | 5 |
| `javax.xml.transform.stream` | 2 | 2 |
| `javax.xml.validation` | 6 | 6 |
| `javax.xml.xpath` | 11 | 11 |
| `org.apache.http.conn` | 0 | 1 |
| `org.apache.http.conn.scheme` | 0 | 3 |
| `org.apache.http.conn.ssl` | 0 | 6 |
| `org.apache.http.params` | 0 | 3 |
| `org.json` | 5 | 5 |
| `org.w3c.dom` | 28 | 28 |
| `org.w3c.dom.ls` | 8 | 8 |
| `org.xml.sax` | 17 | 17 |
| `org.xml.sax.ext` | 8 | 8 |
| `org.xml.sax.helpers` | 10 | 10 |
| `org.xmlpull.v1` | 4 | 4 |
| `org.xmlpull.v1.sax2` | 1 | 1 |

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
