# Shiraz NDK / Native C/C++ Libraries: final goal (100% API 35 NDK surface)

This document defines what "100% NDK / Native C/C++ Libraries Layer" means in
Shiraz, establishes the engineering rules and verification gates required to
reach it without silent stubs, and tracks every Android API 35 stable NDK
library and API family across both Linux (`build/`) and Apple Silicon macOS
(`build/macos/`).

Status boxes are updated only after a library or family builds cleanly with
`-std=c++17 -Wall -Wextra -Wpedantic -Werror`, exports every required `extern "C"`
symbol verified by `python3 scripts/audit_ndk_apis.py`, and passes its native
behavioral unit tests:

- `[x]` — 100% of tracked symbols in the library/family are exported and behaviorally unit-tested.
- `[~]` — Partly implemented; remaining gaps are listed.
- `[ ]` — Not yet implemented.

## Engineering rules

1. **Real working implementations:** Data models, codecs, containers, parcels,
   state machines, image decoders, audio/MIDI queues, ICU wrappers, and NNAPI
   CPU reference tensor kernels must execute real logic and be covered by
   automated native tests.
2. **Explicit hardware boundaries:** Where an NDK API crosses into hardware or
   host facilities absent on a desktop host (for example, physical camera2
   sensors when no capture device is bridged, or Vulkan when no host ICD is
   installed), object construction/metadata/queries work normally and the
   hardware boundary fails explicitly with the documented NDK status code
   (`ACAMERA_ERROR_*`, `VK_ERROR_INCOMPATIBLE_DRIVER`, `AMEDIA_ERROR_UNSUPPORTED`,
   etc.). Never fabricate silent success.
3. **Dual-host build & live symbol audit:** Every library target is defined in
   `mk/ndk.mk` for both Linux (`build/lib*.so`) and macOS (`build/macos/lib*.so`)
   and verified with `nm` via `python3 scripts/audit_ndk_apis.py`.

## Milestone roadmap (`N0` – `N9`)

| Milestone | Scope | Libraries / families | Status |
|---|---|---|---|
| **N0** | NDK audit infrastructure & standalone targets | `scripts/audit_ndk_apis.py`, `mk/ndk.mk`, standalone `libmediandk.so`, `libsync.so`, `libOpenSLES.so` (macOS) | `[x]` |
| **N1** | `libandroid.so` core app, input, config & assets | `AConfiguration_*`, `AInputEvent_*`, `AInputQueue_*`, `AKeyEvent_*`, `AMotionEvent_*`, `ANativeActivity_*`, `AAssetManager_*` / `AAsset_*` / `AAssetDir_*` | `[x]` |
| **N2** | `libandroid.so` graphics/system/storage & `libjnigraphics.so` | `ASurfaceControl_*`, `ASurfaceTransaction_*`, `AImageDecoder_*`, `APerformanceHint_*`, `AThermal_*`, `AStorageManager_*`, `AObbInfo_*`, `AndroidBitmap_compress` / `_getDataSpace` / `_getHardwareBuffer` | `[x]` |
| **N3** | `libmediandk.so` containers & codec surface | `AMediaExtractor_*` (WAV/MP4), `AMediaMuxer_*` (WAV/MP4), `AMediaDataSource_*`, `AMediaFormat_*` & `AMediaCodec_*` completion | `[x]` |
| **N4** | `libaaudio.so` low-latency audio | Full `AAudioStreamBuilder_*` & `AAudioStream_*` over CoreAudio/ALSA + callback/ring-buffer engine | `[x]` |
| **N5** | `libicu.so` & `liblog.so` | Unversioned NDK ICU4C ABI (`u_*`, `ubrk_*`, `ucnv_*`, `ucol_*`, `udat_*`, `uloc_*`, `unorm2_*`, `unum_*`, `ubidi_*`, `uscript_*`, `utext_*`, `utrans_*`, `ucal_*`) + full Android logging API | `[x]` |
| **N6** | `libamidi.so`, `libcamera2ndk.so` & `libOpenMAXAL.so` | Native MIDI port/loopback engine, Camera2 metadata/request/session API, OpenMAX AL 1.0.1 COM engine | `[x]` |
| **N7** | `libbinder_ndk.so` & `libneuralnetworks.so` | Real in-process NDK Binder (`AIBinder_*`, `AParcel_*`, `AStatus_*`, `ABinderProcess_*`) + real CPU NNAPI tensor engine (`ANeuralNetworks*`) | `[x]` |
| **N8** | `libvulkan.so` | Dynamic Vulkan loader/ICD bridge + `VK_KHR_android_surface` & `AHardwareBuffer` Android extension hooks | `[x]` |
| **N9** | Final audit, build integration & tracker update | `MISSING_ANDROID_LIBRARIES.md` §5 regeneration, `landing/src/lib/android-support.json` update, `--require-complete` verification | `[x]` |

## Complete NDK library inventory (15 native libraries)

| Status | Library | Primary APIs | Milestone |
|---|---|---|---|
| `[x]` | `libandroid.so` | `ANativeWindow`, `AHardwareBuffer`, `ASharedMemory`, `ALooper`, `AChoreographer`, `ASensor*`, `AFont`, `ATrace`, `AConfiguration`, `AInput*`, `ANativeActivity`, `AAsset*`, `ASurfaceControl*`, `AImageDecoder*`, `APerformanceHint*`, `AThermal*`, `AStorageManager*`, `AObbInfo*` | Existing + N1 + N2 |
| `[x]` | `libjnigraphics.so` | `AndroidBitmap_getInfo`, `_lockPixels`, `_unlockPixels`, `_getDataSpace`, `_compress`, `_getHardwareBuffer` | Existing + N2 |
| `[x]` | `libmediandk.so` | `AMediaFormat`, `AMediaCodec`, `AMediaCrypto`, `AMediaDrm`, `AImageReader`, `AImage`, `AMediaExtractor`, `AMediaMuxer`, `AMediaDataSource` | N0 + N3 |
| `[x]` | `libsync.so` | `sync_wait`, `sync_merge`, `sync_file_info`, `sync_file_info_free` | N0 |
| `[x]` | `libOpenSLES.so` | `slCreateEngine`, `slQueryNumSupportedEngineInterfaces`, `slQuerySupportedEngineInterfaces`, `SL_IID_*` | Existing + N0 |
| `[x]` | `libaaudio.so` | `AAudio_createStreamBuilder`, `AAudioStreamBuilder_*`, `AAudioStream_*`, `AAudio_convert*` | N4 |
| `[x]` | `liblog.so` | `__android_log_*` | N5 |
| `[x]` | `libicu.so` | Unversioned NDK ICU4C (`u_*`, `ubrk_*`, `ucnv_*`, `ucol_*`, `udat_*`, `uloc_*`, `unorm2_*`, `unum_*`, `ubidi_*`, `uscript_*`, `utext_*`, `utrans_*`, `ucal_*`) | N5 |
| `[x]` | `libamidi.so` | `AMidiDevice_*`, `AMidiInputPort_*`, `AMidiOutputPort_*` | N6 |
| `[x]` | `libcamera2ndk.so` | `ACameraManager_*`, `ACameraMetadata_*`, `ACameraDevice_*`, `ACaptureRequest_*`, `ACameraOutputTarget_*`, `ACaptureSessionOutput*`, `ACameraCaptureSession_*` | N6 |
| `[x]` | `libOpenMAXAL.so` | `xaCreateEngine`, `xaQueryNumSupportedEngineInterfaces`, `xaQuerySupportedEngineInterfaces`, `XA_IID_*` | N6 |
| `[x]` | `libbinder_ndk.so` | `AIBinder_*`, `AParcel_*`, `AStatus_*`, `ABinderProcess_*` | N7 |
| `[x]` | `libneuralnetworks.so` | `ANeuralNetworksModel_*`, `Compilation_*`, `Execution_*`, `Event_*`, `Memory_*`, `Device_*`, `Burst_*` | N7 |
| `[x]` | `libvulkan.so` | Core Vulkan 1.0–1.3 loader + `vkCreateAndroidSurfaceKHR`, `vkGetAndroidHardwareBufferPropertiesANDROID`, `vkGetMemoryAndroidHardwareBufferANDROID` | N8 |
| `[x]` | `libnativewindow.so` | `ANativeWindow_*`, `AHardwareBuffer_*` (re-exported / symlinked from `libandroid.so`) | Existing + N0 |

## Verification commands

```sh
# Build all macOS NDK libraries and run all 9 NDK behavioral test suites
make macos-ndk-build
make macos-ndk-test

# Audit live exported NDK symbols against the API 35 catalog (15/15 libs, 887/887 symbols)
python3 scripts/audit_ndk_apis.py --require-complete
```
