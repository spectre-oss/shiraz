# Shiraz Framework APIs: final goal (100% API 35 surface)

This document defines what "100% Framework APIs" means in Shiraz, establishes the
engineering rules and toolchain invariants required to reach it without fake stubs,
and tracks all **49 `android.*` framework areas (4,445 classes)** against the pinned
Android 15 / API 35 SDK reference (`platform-35_r02.zip`).

Status boxes are updated only after a package or milestone builds cleanly into
`build/shiraz-framework.jar` and passes behavioral smoke tests on **both OpenJDK
and standalone ART**:

- `[x]` — 100% of API 35 classes in the area are present and behaviorally smoke-tested on OpenJDK + ART.
- `[~]` — Partly implemented; exact `present / reference` counts and remaining gaps are listed.
- `[ ]` — Zero classes implemented yet.

## Decision and scope

- **Pinned API 35 baseline:** Every `android.*` class in `.deps/android-sdk/platform-35_r02.zip`
  (`SHA-256: 0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0`), excluding
  generated `android.R` and `android.R$*` resource ID holders (**4,445 classes across 49 areas**).
- **Real working implementations only:** Classes must be implemented via:
  1. Pure-Java data structures, parsers, evaluators, state machines, and wire encoders;
  2. Host OS bridges (`shiraz.runtime.*` and `native/*` over Linux/macOS windowing, audio,
     networking, SQLite, keystore, and desktop portals); or
  3. Clean Apache-2.0 ports from AOSP `frameworks/base` with exact commit provenance recorded
     in `LICENSES/AOSP-NOTICE.txt` (kept modular alongside Shiraz's `GPL-3.0-only` code).
- **No silent dummy stubs:** Methods must never return fabricated `null`/`0`/`true` values to
  fake unsupported capabilities. Where an Android API crosses into hardware or mobile-only
  kernel facilities absent on a Linux/macOS desktop host (e.g., cellular radio baseband, eUICC
  SIM applet channels, NFC reader RF field), value/request/exception types work normally and
  the hardware boundary fails explicitly with `UnsupportedOperationException` or the documented
  Android `SecurityException` / `PackageManager.FEATURE_*` absence check per [AGENTS.md](../AGENTS.md).
- **Dual-runtime verification gate:** Every milestone must pass `python3 scripts/build_framework.py`
  and behavioral smoke tests via `python3 scripts/java_framework_smoke.py <SmokeClass> --marker <MARKER>`
  on **both OpenJDK and standalone ART**, followed by regenerating
  [MISSING_ANDROID_LIBRARIES.md](MISSING_ANDROID_LIBRARIES.md) and
  `landing/src/lib/android-inventory.json`.

## Runtime and toolchain facts that shape the design

| Constraint | Measured baseline | Design consequence |
|---|---|---|
| Single-DEX 65,536 method ceiling | `classes.dex` reached `57,737 / 65,536` method IDs across `6,557` compiled classes at full `4,445`-class API 35 coverage. | `scripts/build_framework.py` compiles with `dx --dex --multi-dex --min-sdk-version=27` and packages all `classes*.dex` shards (`ZIP_STORED`, 4-byte local-payload aligned) into `build/shiraz-framework.jar`. |
| Android 8.1 ART `core-oj` Java surface | Bytecode scan of all `4,445` API 35 `.class` files against `core-oj` + `core-libart` found **only 2 missing `java.*` types** across the entire SDK: `java.lang.ref.Cleaner` (used by `android.system.SystemCleaner`) and `java.util.concurrent.Flow` (used by `android.service.controls.ControlsProviderService`). | `java.lang.ref.Cleaner` (daemon `ReferenceQueue` cleaner) and `java.util.concurrent.Flow` reactive-streams interfaces ship in the framework runtime bridge so all `4,445 / 4,445` API 35 classes compile and run cleanly on OpenJDK and ART. |
| No runtime `LambdaMetafactory` in `core-oj` | `javac` compiles `->` lambdas and `::` method references to `invokedynamic`, which `dx --min-sdk-version=27` lowers to `invoke-custom`. Android 8.1 ART lacks `java.lang.invoke.LambdaMetafactory` and throws `BootstrapMethodError` at runtime (APKs desugar lambdas via `d8`/`r8` at build time). | Framework sources under `java/` and smoke tests under `tests/java/` must use named or anonymous classes, never `->` lambdas or `::` method references. |
| Standalone ART `ZoneInfoDB` has no Android `/system/usr/share/zoneinfo/tzdata` | `java.util.TimeZone.getTimeZone(id)` falls back to `GMT` for IANA zones on standalone ART, while `android.icu.util.TimeZone` carries full compiled IANA tzdata (`icudt`). | Time-zone APIs (`android.util.TimeUtils`, `android.text.format.*`, `android.icu.*`) consult `android.icu.util.TimeZone` when `java.util.TimeZone` falls back to `GMT`. |
| Boot classpath vs framework jar split | ART boots with `core-oj`, `core-libart`, `okhttp`, `bouncycastle`, and `wolfssljni`, supplying `179` `android.*` classes (`162` `android.icu.*`, `9` `android.system.*`, `8` `android.util.Mutable*`). | Both `scripts/audit_framework_apis.py` and `scripts/update_compatibility_tracker.py` measure the exact runtime union (`build/framework/classes` ∪ ART boot jars), with inner-class overlays under `optional-java/icu-inner-overlay` compiled safely alongside boot ICU jars. |

### Final completion snapshot

Measured by `python3 scripts/audit_framework_apis.py`:

- **Present:** `4,445 / 4,445` `android.*` classes (**100.00%**) — `4,266` in `build/framework/classes` + `179` in ART boot jars (`classes.dex` `57,737 / 65,536` method IDs across `6,557` total compiled classes, 4-byte aligned).
- **Missing:** `0` classes across all `49` areas.
- **100% complete areas:** **49 of 49** (`android.*` API 35 SDK surface complete; behavioral parity and hardware backends remain bounded per subsystem as tracked on the [compatibility page](../landing/src/lib/android-support.json)).

## Milestone roadmap (`F0` – `F7`)

Difficulty: **L** low, **M** medium, **H** high, **X** runtime/native-heavy.

### Milestone F0: Build, multi-DEX & audit infrastructure

| Status | Item | Source / tool | Implementation & verification | Diff |
|---|---|---|---|---|
| [x] | Multi-DEX framework jar packaging | `scripts/build_framework.py` | `dx --dex --multi-dex --min-sdk-version=27` into `build/framework/dex`, preserving `build/framework/classes.dex` and archiving all `classes*.dex` shards (`ZIP_STORED`, 4-byte aligned) into `build/shiraz-framework.jar` | M |
| [x] | Live API 35 & DEX shard auditor | `scripts/audit_framework_apis.py` | Verifies pinned `platform-35_r02.zip` SHA-256, checks 4-byte DEX alignment and per-shard method ID counts, and regenerates [MISSING_ANDROID_LIBRARIES.md](MISSING_ANDROID_LIBRARIES.md) | L |
| [x] | Unified boot-jar tracker inventory | `scripts/update_compatibility_tracker.py`, `tests/test_compatibility_tracker.py` | `class_inventory(jar, classes, boot_jars=())` unions ART hostdex boot jars by default so `landing/src/lib/android-inventory.json` matches runtime class availability | L |

### Milestone F1: Core utility, annotation, XML, crypto & animation foundations

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android` | 3 / 3 (100%) | `Manifest`, `Manifest.permission` (322 API 35 permission constants), `Manifest.permission_group` (15 group constants) (`FrameworkMilestoneF1Smoke`) | L |
| [x] | `android.accounts` | 13 / 13 (100%) | Host-backed `AccountManager`, `AbstractAccountAuthenticator`, persistent session/listener/future runtime (`AccountsSmoke`, `FrameworkMilestoneF1Smoke`) | M |
| [x] | `android.animation` | 29 / 29 (100%) | Full `Animator`/`ValueAnimator`/`ObjectAnimator`/`AnimatorSet`/`TimeAnimator`, `Keyframe` + `PropertyValuesHolder.ofKeyframe`, `BidirectionalTypeConverter`, array/rect/point evaluators, `AnimatorPauseListener`, `DurationScaleChangeListener`, XML `AnimatorInflater` (`FrameworkMilestoneF1Smoke`) | M |
| [x] | `android.annotation` | 5 / 5 (100%) | `NonNull`, `Nullable`, `SuppressLint`, `TargetApi`, `FlaggedApi` with API 35 `@Retention(CLASS)` and `@Target` metadata (`FrameworkMilestoneF1Smoke`) | L |
| [x] | `android.crypto` | 2 / 2 (100%) | `HpkeSpi` and `XdhKeySpec` (`RAW` key material spec with defensive cloning) under `android.crypto.hpke` (`FrameworkMilestoneF1Smoke`) | L |
| [x] | `android.sax` | 7 / 7 (100%) | Streaming SAX `RootElement`, `Element`, listener hierarchy, `requireChild` validation, and mixed-content rejection (`FrameworkMilestoneF1Smoke`) | M |
| [x] | `android.util` | 67 / 67 (100%) | `ArrayMap`, `ArraySet`, IEEE 754 binary16 `Half`, `Rational`, `Base64InputStream`/`Base64DataException`, `proto.ProtoOutputStream`, `MonthDisplayHelper`, ICU-backed `TimeUtils`, `EventLog`/`EventLogTags`/`StatsLog`, `Printer` implementations, `TimingLogger`, `CloseGuard`, `IntProperty`, `Dumpable`/`DumpableContainer` alongside `Sparse*`, `LruCache`, `JsonReader`/`JsonWriter`, `Xml`, `DisplayMetrics`, `TypedValue`, and boot `Mutable*` (`FrameworkMilestoneF1Smoke`) | M |
| [x] | `android.system` | 14 / 14 (100%) | `Int64Ref`, `StructCmsghdr`, `StructMsghdr`, `VmSocketAddress`, and `SystemCleaner` (over daemon `PhantomReference` `Cleaner`) alongside boot `Os`/`OsConstants`/`ErrnoException`/`Struct*` (`FrameworkMilestoneF1Smoke`, `FrameworkMilestoneF6Smoke`) | L |

### Milestone F2: Core app, OS, content, storage, database & ICU runtime

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.os` | 143 / 143 (100%) | Complete API 35 surface (`StrictMode` + `strictmode.*`, `VibrationEffect`/`CombinedVibration`, `OutcomeReceiver`, `SharedMemory`, `FileUtils`, `PatternMatcher`, `DropBoxManager`, `HealthStats`, `StorageVolume`/`StorageManager`, `SdkExtensions`, `PersistableBundle`, `TestLooperManager`, `TokenWatcher`, `PowerMonitor`) (`FrameworkMilestoneF2Smoke`) | H |
| [x] | `android.content` | 178 / 178 (100%) | Full `AttributionSource`, `ContextParams`, `ClipboardManager`, `ContentProviderOperation` batch engine, `AsyncTaskLoader`/`CursorLoader`, `SyncStats`/`UriPermission`, `pm.*` (`PackageInstaller`, `LauncherApps`, `ShortcutInfo`/`ShortcutManager`, `SigningInfo`, `verify.domain.*`), `res.loader.*`, and `om.*` (`FrameworkMilestoneF5Smoke`, `FrameworkMilestoneF6Smoke`) | H |
| [x] | `android.app` | 343 / 343 (100%) | Full `Notification` + styles/channels/actions, `ActivityManager` records, `AppOpsManager`, `UiModeManager`, `WallpaperManager`, `RemoteInput`, `slice.*`, `usage.*`, `admin.*`, in-memory BM25 `appsearch.*` (`66/66`), `assist.*`, `blob.*`, `people.*`, `role.*`, `sdksandbox.*`, `grammaticalinflection.*` (`FrameworkMilestoneF5Smoke`, `FrameworkMilestoneF6Smoke`) | H |
| [x] | `android.database` | 58 / 58 (100%) | Complete cursor/SQLite surface (`AbstractWindowedCursor`, `CursorWindow`, `CursorJoiner`, `MergeCursor`, `MatrixCursor`, `DatabaseUtils`, `SQLiteQueryBuilder`, `SQLiteRawStatement`) (`FrameworkMilestoneF2Smoke`) | M |
| [x] | `android.preference` | 24 / 24 (100%) | Full `Preference` hierarchy (`PreferenceGroup`, `PreferenceScreen`, `EditTextPreference`, `ListPreference`, `MultiSelectListPreference`, `RingtonePreference`, `SwitchPreference`, `PreferenceActivity`, `PreferenceFragment`) (`FrameworkMilestoneF2Smoke`) | M |
| [x] | `android.icu` | 214 / 214 (100%) | Complete API 35 ICU surface: fluent `android.icu.number.*` (`NumberFormatter`, `Precision`, `Notation`, `Scale`, `FormattedNumber`), `text.*` (`ListFormatter`, `CaseMap`, `Edits`, `SimpleFormatter`, `UnicodeSet*` inner overlays), `lang.UCharacter*`, and `util.{MeasureUnit,NoUnit,CurrencyAmount}` (`FrameworkMilestoneF5Smoke`) | M |

### Milestone F3: Graphics, text, view, window, widget, OpenGL & WebKit UI stack

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.graphics` | 197 / 197 (100%) | `ColorSpace` adaptation/connector math, `RenderNode`, `RenderEffect`, `HardwareRenderer`, `Gainmap`, `Mesh`/`MeshSpecification`, AGSL `RuntimeShader`, `ImageDecoder`, `YuvImage` BT.601 JPEG encoder, `Interpolator`, `PathMeasure`/`PathIterator`, `fonts.*`, `text.LineBreaker`/`MeasuredText`, `drawable.*`, and `%PDF-1.4` `pdf.*` (`25/25`) (`FrameworkMilestoneF3Smoke`, `FrameworkMilestoneF7Smoke`) | H |
| [x] | `android.text` | 159 / 159 (100%) | `DynamicLayout` span reflow, `PrecomputedText`, `AlteredCharSequence`, `AutoText`, `Annotation`, `BidiFormatter`, `LoginFilter`, all 31 `text.style.*` spans, `text.method.*` movement/key/transformation engines, `Linkify` URL/email/phone regexes, RFC-2822 `Rfc822Tokenizer`, and `text.format.Time` (`FrameworkMilestoneF5Smoke`) | M |
| [x] | `android.view` | 397 / 397 (100%) | `SurfaceControl*`, `WindowInsetsController`/`WindowInsetsAnimation`, `Choreographer.FrameData`, `ScrollCapture*`, `FocusFinder`, `ScaleGestureDetector`, `accessibility.*` (`23/23`), `autofill.*`, `contentcapture.*`, `displayhash.*`, `inputmethod.*` (handwriting/stylus/inline), `inspector.*`, `textclassifier.*` (`44/44` regex entity detector), `textservice.*`, `translation.*` (`FrameworkMilestoneF5Smoke`) | H |
| [x] | `android.transition` | 26 / 26 (100%) | Complete scene/transition engine (`Scene`, `TransitionSet`, `AutoTransition`, `ChangeBounds`, `ChangeTransform`, `Fade`, `Slide`, `Explode`, `Visibility`, `ArcMotion`/`PatternPathMotion`) (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.window` | 10 / 10 (100%) | `BackEvent`, priority-ordered `OnBackInvokedDispatcher`, `SplashScreen`/`SplashScreenView`, `SurfaceSyncGroup`, `TrustedPresentationThresholds`, `InputTransferToken` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.widget` | 189 / 189 (100%) | Complete API 35 widget kit including `GridLayout`, `TableLayout`, `RelativeLayout`, `CalendarView`, `DatePicker`/`TimePicker`, `NumberPicker`, `SearchView`, `TabHost`, `ViewFlipper`/`ViewSwitcher`, `Chronometer`, `RatingBar`, `AutoCompleteTextView`, `Filter`, `RemoteViews.RemoteCollectionItems`, `ShareActionProvider`, and `widget.inline.*` (`FrameworkMilestoneF5Smoke`) | H |
| [x] | `android.inputmethodservice` | 13 / 13 (100%) | `AbstractInputMethodService`, `InputMethodService`, `Keyboard`, `KeyboardView`, `ExtractEditText` (`FrameworkMilestoneF4Smoke`) | M |
| [x] | `android.appwidget` | 5 / 5 (100%) | `AppWidgetManager`, `AppWidgetProvider` broadcast dispatcher, `AppWidgetProviderInfo`, `AppWidgetHost`, `AppWidgetHostView` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.opengl` | 36 / 36 (100%) | Complete 4x4 `Matrix`/frustum/invert math, `Visibility` frustum/triangle intersection, `GLU.gluProject`/`gluUnProject`, `ETC1`/`ETC1Util` PKM codec, `EGL14`/`EGL15`, `GLES10`–`GLES32` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.webkit` | 60 / 60 (100%) | Complete API 35 WebKit surface including `DateSorter`, entangled `WebMessagePort` channels, `TracingController`, `WebViewDatabase` HTTP auth store, `WebResourceResponse`, `WebBackForwardList`, `ServiceWorkerController` (`FrameworkMilestoneF4Smoke`) | M |

### Milestone F4: Media, hardware, speech, print, MTP, DRM & RenderScript

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.media` | 436 / 436 (100%) | WAV/ISO-BMFF `MediaExtractor`/`MediaMuxer`, TIFF/JPEG `ExifInterface` parser/writer, ITU-T DTMF `ToneGenerator`, `JetPlayer`, `FaceDetector`, `ThumbnailUtils`, `MediaSession`/`MediaController`/`MediaBrowser`, `MediaRouter2`, `Spatializer`, `audiofx.*` (`Visualizer` DFT, `Equalizer`, `LoudnessEnhancer`, `DynamicsProcessing`), `midi.*`, `metrics.*`, `projection.*`, `effect.*`, `tv.*` (`59/59` + `tv.interactive.*`) (`FrameworkMilestoneF6Smoke`) | X |
| [x] | `android.hardware` | 138 / 138 (100%) | `HardwareBuffer`, `SyncFence`, `DataSpace`, WMM-2020 `GeomagneticField` spherical harmonics, `SensorDirectChannel`/`SensorAdditionalInfo`, `usb.*`, `lights.*`, `input.HostInputManager`, `display.*` (`VirtualDisplay`), `biometrics.*` (`PromptInfo`), and full `camera2.*` (`CameraCharacteristics`, `CaptureRequest`/`CaptureResult`, `CameraMetadata`, `RggbChannelVector`, `ColorSpaceTransform`, `LensShadingMap`, `TonemapCurve`, `StreamConfigurationMap`, `MeteringRectangle`, `MandatoryStreamCombination`) (`FrameworkMilestoneF7Smoke`) | X |
| [x] | `android.speech` | 25 / 25 (100%) | Complete `SpeechRecognizer`, `RecognitionService`, `RecognizerIntent`, `RecognitionPart`, `RecognitionSupport`, and `speech.tts.*` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.print` | 22 / 22 (100%) | `PrintManager`, `PrintAttributes`, `PrintDocumentAdapter`, `PrintDocumentInfo`, `PrinterId`, `PrinterCapabilitiesInfo`, `PrintedPdfDocument` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.printservice` | 5 / 5 (100%) | `PrintService`, `PrintJob`, `PrintDocument`, `PrinterDiscoverySession`, `CustomPrinterIconCallback` (`FrameworkMilestoneF3Smoke`) | L |
| [x] | `android.drm` | 22 / 22 (100%) | `DrmManagerClient`, `DrmInfo*`, `DrmEvent*`, `DrmRights`, `DrmStore`, `DrmConvertedStatus`, `DrmUtils.ExtendedMetadataParser` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.mtp` | 7 / 7 (100%) | `MtpConstants` (`isAbstractObject`), `MtpObjectInfo` + `Builder`, `MtpDeviceInfo`, `MtpStorageInfo`, `MtpEvent`, `MtpDevice` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.gesture` | 13 / 13 (100%) | Pure-Java 2D stroke recognizer (`Gesture`, `GestureStroke`, `GesturePoint`, `GestureUtils` OBB/cosine similarity, binary `GestureStore`/`GestureLibrary`, `GestureOverlayView`) (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.renderscript` | 73 / 73 (100%) | Pure-Java CPU reference implementation of `RenderScript`, `Allocation` (1D/2D/3D + `Bitmap` sync), `Element`, `Type`, `Sampler`, `FieldPacker`, `Short*/Int*/Long*/Float*/Double*/Byte*` vectors, `Matrix2f`/`Matrix3f`/`Matrix4f`, and real CPU kernels for `ScriptIntrinsicBlur`, `ScriptIntrinsicConvolve3x3`/`5x5`, `ScriptIntrinsicColorMatrix`, `ScriptIntrinsicLUT`/`3DLUT`, `ScriptIntrinsicHistogram`, `ScriptIntrinsicResize`, `ScriptIntrinsicBlend`, and `ScriptIntrinsicYuvToRGB` (`FrameworkMilestoneF7Smoke`) | H |

### Milestone F5: Networking, security, credentials, Bluetooth, NFC & companion

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.net` | 317 / 317 (100%) | RFC-3986 `Uri`, `LinkProperties`/`LinkAddress`/`RouteInfo`/`IpPrefix`, `DnsResolver` async UDP/system resolver, `LocalSocket`, `SSLCertificateSocketFactory`, `UrlQuerySanitizer`, `CaptivePortal`, `IpSecManager`, `ProxyInfo`, `net.http.HttpEngine` (over `HttpURLConnection`) + `HttpResponseCache` + `SslCertificate`, `nsd.*`, `rtp.*`, `sip.*`, `vcn.*`, `eap.*`, `ipsec.ike.*` (`44/44`), and `wifi.*` (`p2p`, `aware`, `rtt`, `hotspot2.pps`) (`FrameworkMilestoneF6Smoke`) | H |
| [x] | `android.security` | 59 / 59 (100%) | `KeyChain`, `AppUriAuthenticationPolicy`, `ConfirmationPrompt`, `FileIntegrityManager`, `AttestedKeyPair`, `keystore.*` (`KeyProtection`, `KeyInfo`, `WrappedKeyEntry`), and ISO 18013-5 `identity.*` (`26/26`) (`FrameworkMilestoneF4Smoke`) | H |
| [x] | `android.credentials` | 19 / 19 (100%) | `CredentialManager`, `Credential`, `CredentialOption`, `GetCredentialRequest`/`Response`, `CreateCredentialRequest`/`Response`, exception hierarchy + `service.credentials.*` (`FrameworkMilestoneF3Smoke`) | M |
| [x] | `android.bluetooth` | 64 / 64 (100%) | Full Bluetooth Classic + GATT (`BluetoothClass` CoD bitmasks, IEEE-11073 int/float packing) + BLE (`ScanFilter.matches`, `ScanSettings`, `AdvertiseSettings`, `TransportDiscoveryData` wire parser) (`FrameworkMilestoneF4Smoke`) | H |
| [x] | `android.nfc` | 32 / 32 (100%) | Pure-Java NFC Forum `NdefMessage`/`NdefRecord` binary wire parser/serializer (chunked records + 36-prefix `RTD_URI`), `Tag`, `tech.*`, `cardemulation.*` (`FrameworkMilestoneF4Smoke`) | M |
| [x] | `android.companion` | 19 / 19 (100%) | `CompanionDeviceManager`, `AssociationRequest`, `AssociationInfo`, `BluetoothDeviceFilter`/`BluetoothLeDeviceFilter`/`WifiDeviceFilter`, `CompanionDeviceService`, `virtual.VirtualDeviceManager` (`FrameworkMilestoneF4Smoke`) | M |
| [x] | `android.se` | 5 / 5 (100%) | `omapi.SEService`, `Reader`, `Session`, `Channel` with explicit no-hardware SE reporting (`FrameworkMilestoneF3Smoke`) | L |

### Milestone F6: Providers, system services, accessibility, location & telephony

| Status | Area | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.provider` | 232 / 232 (100%) | Complete contract & URI builder hierarchy (`ContactsContract`, `CalendarContract` + recurrence/reminder helpers, `MediaStore`, `DocumentsContract`/`DocumentsProvider`, `Settings`, `Telephony`, `BlockedNumberContract`, `CallLog`, `VoicemailContract`, `Browser`, `AlarmClock`, `FontRequest`/`FontsContract`, `CloudMediaProvider`, `E2eeContactKeysManager`) (`FrameworkMilestoneF6Smoke`) | M |
| [x] | `android.service` | 146 / 146 (100%) | Complete service SPI surface across `autofill.*` (`45/45` including `RegexValidator`/`LuhnChecksumValidator`), `controls.*` (`18/18` with `Flow.Publisher`), `credentials.*` (`17/17`), `voice.*` (`14/14`), `notification.*` (`StatusBarNotification`, `NotificationListenerService`, `ZenPolicy`, `Condition`), `carrier.*`, `chooser.*`, `quickaccesswallet.*`, `quicksettings.*`, `wallpaper.*`, `vr.*`, `dreams.*`, `media.*`, `restrictions.*`, `textservice.*`, and `assist.classification.*` (`FrameworkMilestoneF6Smoke`) | H |
| [x] | `android.accessibilityservice` | 25 / 25 (100%) | `AccessibilityService`, `AccessibilityServiceInfo`, `GestureDescription` + stroke path validation, `MagnificationConfig`, `AccessibilityGestureEvent`, `BrailleDisplayController`, `InputMethod`, `TouchInteractionController` (`FrameworkMilestoneF4Smoke`) | M |
| [x] | `android.location` | 41 / 41 (100%) | WGS-84 Vincenty `Location` geodesics + DMS `convert`, 15-degree global EGM96 `AltitudeConverter`, full `Gnss*` models, `Address`, `Criteria`, `Geocoder`, `LocationManager` test providers (`FrameworkMilestoneF4Smoke`) | M |
| [x] | `android.telephony` | 179 / 179 (100%) | ITU-T E.164 `PhoneNumberUtils`, GSM 7-bit / UCS-2 `SmsMessage` PDU codec & multipart splitter, `ServiceState`, `SubscriptionInfo`/`SubscriptionManager`, `SignalStrength` + `CellInfo*`/`CellIdentity*`/`CellSignalStrength*` (GSM/CDMA/LTE/WCDMA/TDSCDMA/NR), `CarrierConfigManager`, `PreciseDataConnectionState`, `PhysicalChannelConfig`, `BarringInfo`, `NetworkRegistrationInfo`, `VisualVoicemail*`, `data.*`, `euicc.*` (LPA activation code parser), `ims.*`, `mbms.*`, and `gsm.*` (`FrameworkMilestoneF6Smoke`) | H |
| [x] | `android.telecom` | 45 / 45 (100%) | Complete API 35 surface (`TelecomManager` transactional `CallControl`, `Connection`/`ConnectionService`/`Conference`, `RttTextStream` pipe I/O, `InCallService`/`Call`, `CallScreeningService`, `CallRedirectionService`, all 15 `Parcelable` models) (`FrameworkMilestoneF4Smoke`) | M |

### Milestone F7: Health Connect, AdServices, DeviceLock & `core-oj` finalization

| Status | Area / item | Coverage | Implementation summary | Diff |
|---|---:|--:|---|---|
| [x] | `android.health` | 182 / 182 (100%) | Full `android.health.connect.*`: physical units (`Energy`, `Length`, `Mass`, `Power`, `Pressure`, `Temperature`, `Velocity`, `Volume`, `BloodGlucose`, `Percentage`), `AggregationType`, `TimeRangeFilter`s, `ReadRecordsRequest*`, `InsertRecordsResponse`, `ChangeLogsRequest`/`Response`, `HealthConnectException`, `HealthConnectManager`, and all 143 strongly-typed health/fitness `Record` classes (`FrameworkMilestoneF7Smoke`) | H |
| [x] | `android.adservices` | 145 / 145 (100%) | Complete API 35 surface across all 11 subpackages (`ondevicepersonalization` [53], `adselection` [29], `customaudience` [20], `common` [14 — `AdTechIdentifier`, `AdData`, `AdFilters`, `FrequencyCapFilters`, `KeyedFrequencyCap`], `measurement` [13], `topics` [7], `signals` [3], `adid` [2], `appsetid` [2], `exceptions` [1], `AdServicesState` — reporting AdServices disabled by default on desktop hosts) (`FrameworkMilestoneF7Smoke`) | M |
| [x] | `android.devicelock` | 2 / 2 (100%) | `DeviceId` and `DeviceLockManager` reporting unmanaged desktop device state via `OutcomeReceiver` (`FrameworkMilestoneF3Smoke`) | L |
| [x] | `core-oj` Java 9+ bridge (`Cleaner` & `Flow`) | 2 / 2 (100%) | `java.lang.ref.Cleaner` (daemon `PhantomReference` + `ReferenceQueue` cleaner) and `java.util.concurrent.Flow` (`Publisher`, `Subscriber`, `Subscription`, `Processor`) compiled into the runtime bridge and verified on OpenJDK + ART (`FrameworkMilestoneF6Smoke`) | M |

## Complete 49-area inventory (`4,445` API 35 classes)

Sorted alphabetically; regenerate numbers anytime with `python3 scripts/audit_framework_apis.py`.

| Status | Area | Present | Reference | Missing | Coverage | Milestone |
|---|---|--:|--:|--:|--:|---|
| [x] | `android` | 3 | 3 | 0 | 100.0% | F1 |
| [x] | `android.accessibilityservice` | 25 | 25 | 0 | 100.0% | F6 |
| [x] | `android.accounts` | 13 | 13 | 0 | 100.0% | F1 |
| [x] | `android.adservices` | 145 | 145 | 0 | 100.0% | F7 |
| [x] | `android.animation` | 29 | 29 | 0 | 100.0% | F1 |
| [x] | `android.annotation` | 5 | 5 | 0 | 100.0% | F1 |
| [x] | `android.app` | 343 | 343 | 0 | 100.0% | F2 |
| [x] | `android.appwidget` | 5 | 5 | 0 | 100.0% | F3 |
| [x] | `android.bluetooth` | 64 | 64 | 0 | 100.0% | F5 |
| [x] | `android.companion` | 19 | 19 | 0 | 100.0% | F5 |
| [x] | `android.content` | 178 | 178 | 0 | 100.0% | F2 |
| [x] | `android.credentials` | 19 | 19 | 0 | 100.0% | F5 |
| [x] | `android.crypto` | 2 | 2 | 0 | 100.0% | F1 |
| [x] | `android.database` | 58 | 58 | 0 | 100.0% | F2 |
| [x] | `android.devicelock` | 2 | 2 | 0 | 100.0% | F7 |
| [x] | `android.drm` | 22 | 22 | 0 | 100.0% | F4 |
| [x] | `android.gesture` | 13 | 13 | 0 | 100.0% | F4 |
| [x] | `android.graphics` | 197 | 197 | 0 | 100.0% | F3 |
| [x] | `android.hardware` | 138 | 138 | 0 | 100.0% | F4 |
| [x] | `android.health` | 182 | 182 | 0 | 100.0% | F7 |
| [x] | `android.icu` | 214 | 214 | 0 | 100.0% | F2 |
| [x] | `android.inputmethodservice` | 13 | 13 | 0 | 100.0% | F3 |
| [x] | `android.location` | 41 | 41 | 0 | 100.0% | F6 |
| [x] | `android.media` | 436 | 436 | 0 | 100.0% | F4 |
| [x] | `android.mtp` | 7 | 7 | 0 | 100.0% | F4 |
| [x] | `android.net` | 317 | 317 | 0 | 100.0% | F5 |
| [x] | `android.nfc` | 32 | 32 | 0 | 100.0% | F5 |
| [x] | `android.opengl` | 36 | 36 | 0 | 100.0% | F3 |
| [x] | `android.os` | 143 | 143 | 0 | 100.0% | F2 |
| [x] | `android.preference` | 24 | 24 | 0 | 100.0% | F2 |
| [x] | `android.print` | 22 | 22 | 0 | 100.0% | F4 |
| [x] | `android.printservice` | 5 | 5 | 0 | 100.0% | F4 |
| [x] | `android.provider` | 232 | 232 | 0 | 100.0% | F6 |
| [x] | `android.renderscript` | 73 | 73 | 0 | 100.0% | F4 |
| [x] | `android.sax` | 7 | 7 | 0 | 100.0% | F1 |
| [x] | `android.se` | 5 | 5 | 0 | 100.0% | F5 |
| [x] | `android.security` | 59 | 59 | 0 | 100.0% | F5 |
| [x] | `android.service` | 146 | 146 | 0 | 100.0% | F6 |
| [x] | `android.speech` | 25 | 25 | 0 | 100.0% | F4 |
| [x] | `android.system` | 14 | 14 | 0 | 100.0% | F1 / F7 |
| [x] | `android.telecom` | 45 | 45 | 0 | 100.0% | F6 |
| [x] | `android.telephony` | 179 | 179 | 0 | 100.0% | F6 |
| [x] | `android.text` | 159 | 159 | 0 | 100.0% | F3 |
| [x] | `android.transition` | 26 | 26 | 0 | 100.0% | F3 |
| [x] | `android.util` | 67 | 67 | 0 | 100.0% | F1 |
| [x] | `android.view` | 397 | 397 | 0 | 100.0% | F3 |
| [x] | `android.webkit` | 60 | 60 | 0 | 100.0% | F3 |
| [x] | `android.widget` | 189 | 189 | 0 | 100.0% | F3 |
| [x] | `android.window` | 10 | 10 | 0 | 100.0% | F3 |
| **Total** | **49 areas** | **4,445** | **4,445** | **0** | **100.0%** | **F0–F7** |

## Verification commands

```sh
# 1. Compile framework classes and build multi-DEX shiraz-framework.jar
python3 scripts/build_framework.py

# 2. Run Milestone F1–F7 behavioral regressions on OpenJDK + ART
python3 scripts/value_animator_smoke.py
python3 scripts/java_framework_smoke.py FrameworkMilestoneF1Smoke --marker FRAMEWORK_F1_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF2Smoke --marker FRAMEWORK_F2_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF3Smoke --marker FRAMEWORK_F3_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF4Smoke --marker FRAMEWORK_F4_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF5Smoke --marker FRAMEWORK_F5_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF6Smoke --marker FRAMEWORK_F6_OK --framework-res build/framework-res/framework-res.apk
python3 scripts/java_framework_smoke.py FrameworkMilestoneF7Smoke --marker FRAMEWORK_F7_100_PERCENT_OK --framework-res build/framework-res/framework-res.apk

# 3. Audit live API 35 coverage, DEX shards, and regenerate tracked inventories
python3 scripts/audit_framework_apis.py --write-missing-doc
python3 scripts/update_compatibility_tracker.py
python3 -m unittest tests/test_compatibility_tracker.py
node landing/scripts/check-compatibility.mjs
```
