# Shiraz on macOS: final goal (native path)

This document defines what "Shiraz runs natively on macOS" means and lists every
host API that must be implemented natively to get there. It is the acceptance
target for the `feat/macos` work. Status boxes are updated as work lands; a box
is only ticked after the item is verified on a Mac, not when code compiles.
`[~]` means partly verified; the "Verified on macOS" section names the part.

## Decision and scope

- **Native only.** Shiraz runs as an ordinary macOS process. No virtual machine,
  Hypervisor.framework guest, Virtualization.framework micro-VM, container, or
  remote host. The Linux core rules in [AGENTS.md](../AGENTS.md) apply unchanged.
- **Metal is the GPU API.** App GLES runs on ANGLE's Metal backend, Shiraz's own
  canvas and composition run on Skia Ganesh-Metal, and buffers are IOSurfaces.
- **Apple Silicon (arm64) first.** `arm64-v8a` APK native code is the host ISA
  and runs without instruction translation. Intel Macs, `armeabi-v7a` (Apple
  Silicon has no AArch32) and `x86`/`x86_64` APK libraries are out of scope and
  must fail with an explicit error.
- **Baseline OS:** macOS 15 or later. Some items need newer releases and are
  marked (for example KosmicKrisp Vulkan needs macOS 26).
- **Same acceptance as Linux.** OsmAnd+ is the first target and must be fully
  functional through a real interactive walkthrough, with measured cold and warm
  startup, interactive portrait/landscape resizing, and persistence across
  restart. F-Droid apps follow only after OsmAnd+.

## Host facts that shape the design

Measured on an Apple M2 Pro running macOS 27.2:

| Fact | Consequence |
|---|---|
| Page size is 16 KiB. | ART's `kPageSize` is a compile-time 16384 on darwin-arm64 (`runtime.cc` checks it against `sysconf(_SC_PAGE_SIZE)` at startup). 4 KiB-aligned APK `.so` segments must be copy-loaded, not mapped from the file. |
| Nothing can be mapped below 4 GB (arm64 binaries need a hard 4 GB `__PAGEZERO`). | The Java heap lives in a fixed 4 GiB window at `0x10000000000`; a 32-bit reference is the low half of the address and decodes as `base | ref` (0 stays null). |
| The kernel rewrites `tpidr_el0` on context switch (a written value reverts to the kernel's CPU id). | Bionic-ABI code that reads `tpidr_el0` for TLS (stack guard, ELF TLS, GL slots) cannot run unmodified; the loader must rewrite those instructions. |
| RWX memory requires `MAP_JIT` plus per-thread `pthread_jit_write_protect_np`; plain RWX `mmap`/`mprotect` fails. | ART JIT and the ELF loader share one executable-memory allocator. |
| Apple arm64 ABI differs from AAPCS64: variadic arguments on the stack, `char*` `va_list`, 64-bit `long double`, signed `char`, caller-extended narrow arguments, `x18` reserved. | Every bionic-to-libSystem crossing goes through a thunk; variadic and `va_list` functions need hand-written argument marshalling. |
| `UNUserNotificationCenter` aborts in an unbundled process. | Notifications, location and camera permissions require a real `.app` bundle with `CFBundleIdentifier` and usage strings. |

## Target architecture

```
Shiraz.app (signed, hardened runtime, allow-jit)
└─ Contents/MacOS/Shiraz: embedding launcher, JNI_CreateJavaVM on thread 0
   ├─ ART (Mach-O, darwin-arm64) + unchanged boot DEX + shiraz-framework.jar
   ├─ Shiraz host libraries (Mach-O): platform layer over libSystem/AppKit/Metal
   ├─ Guest ELF loader (ART NativeBridge hook)
   │    APK lib/arm64-v8a/*.so → copy-load, rewrite tpidr_el0, bind imports
   │    bionic libc/libm/libdl personality → libSystem thunks
   │    libEGL/libGLESv*/libandroid/libOpenSLES/libvulkan → Shiraz host libraries
   └─ Presentation: SDL Cocoa window → CAMetalLayer
        Skia Ganesh-Metal canvas ← IOSurface textures from app GLES (ANGLE-Metal)
        NSView displayLink → Choreographer
```

## APIs to implement natively

Difficulty: **L** low, **M** medium, **H** high, **X** research-level.

### 1. Runtime hosting (ART on darwin-arm64)

| Status | Item | Repo / source | macOS implementation | Diff |
|---|---|---|---|---|
| [~] | ART builds as Mach-O for darwin-arm64 | `context/art_standalone`, `patches/art-darwin-*.patch`, `scripts/bootstrap_darwin.py` | Apple clang build of ART, libartbase, libdexfile, libnativehelper, libcore natives; Mach-O macros in arm64 `.S` files (underscore prefix, `.private_extern`, no `.type`/`.size`); static `.set` alias tables for asm-to-C symbols. Release libart only; mterp is assembled but never runs | X |
| [x] | Page size | `art/libartbase/base/globals.h`, `runtime.cc` | `kPageSize` is `constexpr 16384` on darwin-arm64; monitor-pool chunks stay 4 KiB; stack-overflow gaps 16 KiB | X |
| [~] | Heap above 4 GB | `mem_map.cc`, `mirror/object_reference.h`, `lock_word.h` | `MemMap::Init` reserves `[0x10000000000, +4 GiB)` with `mach_vm_allocate(VM_FLAGS_FIXED)`; `low_4gb` maps come from that window; references decode as `base | ref`; forwarding addresses are window-relative. Boot images (`ART_BASE_ADDRESS`) and compiled code are not adapted yet | X |
| [ ] | Executable memory | `jit_code_cache.cc` | Single `MAP_JIT` region, `pthread_jit_write_protect_np` around writes, `sys_icache_invalidate`; no `memfd_create` dual view, no RWX `mprotect` | H |
| [ ] | Fault handling | `fault_handler_arm64.cc`, sigchain | Darwin `ucontext_t` (`uc_mcontext->__ss.__pc/__sp/__x[]`), SIGBUS vs SIGSEGV, Mach exception ports not stealing faults | H |
| [ ] | Memory remap | ART `MemMap` | `mach_vm_remap` instead of `mremap` | M |
| [ ] | Interpreter-only first, JIT next, AOT last | `oat_file.cc`, `scripts/package_aot.py` | `-Xint` → JIT; OAT files loaded by ART's own ELF loader into `MAP_JIT` memory | H |
| [ ] | Embedding launcher | `packaging/app_run.cpp`, `src/shiraz/runtime.py` | `.app` main executable, `_NSGetExecutablePath`, `JNI_CreateJavaVM` on the primordial thread (Cocoa requirement), `@rpath` dylib lookup; no `ld.so --preload`, no `DYLD_INSERT_LIBRARIES` | M |
| [ ] | TLS trust store | `patches/art-host-trust-store.patch` | Security.framework (`SecTrustCopyAnchorCertificates` / keychain roots) | M |
| [x] | Library name suffix | libcore `jvm_md.h`, `Runtime.java` | Keep `JNI_LIB_SUFFIX` as `.so` so `System.loadLibrary` finds APK `libfoo.so` | L |

### 2. Guest ELF loader and bionic ABI personality

The Darwin counterpart of ATL `bionic_translation`. ELF parsing, relocation and
symbol lookup are reusable; the host-facing layer is new.

| Status | Item | macOS implementation | Diff |
|---|---|---|---|
| [ ] | ELF loading on 16 KiB pages | Copy `PT_LOAD` segments into anonymous memory (bionic 16 KiB compat-mode layout rules), apply relocations, then flip to RX (or `MAP_JIT`); honour `GNU_RELRO`; reject non-arm64 ELF | H |
| [ ] | TLS register | Rewrite every `mrs/msr xN, tpidr_el0` in loaded executable segments into a branch to a stub returning a per-thread bionic TLS block (host thread-local); detect and refuse code that generates such instructions at run time | X |
| [ ] | Raw syscalls | Scan for and trap `svc #0` (Linux numbering in `x8`); route known syscalls to libSystem, fail others explicitly | H |
| [ ] | Variadic and `va_list` thunks | Assembly thunks for `printf`/`scanf` families, `open`, `openat`, `fcntl`, `ioctl`, `syscall`, `prctl`, `execl*`; AAPCS64 `va_list` struct → Darwin `char*` | H |
| [ ] | Narrow-argument extension | Sign/zero-extend `char`, `short`, `bool` arguments in thunks (AAPCS64 leaves upper bits undefined) | M |
| [ ] | `long double` | binary128 `*l` math and conversions via compiler-rt `__*tf3` plus quad-precision libm | M |
| [ ] | errno | Darwin → Linux errno numbers, written to a bionic-ABI `__errno()` location | M |
| [ ] | Signals | Linux ↔ Darwin signal numbers, `sigaction` flag translation, `siginfo_t`/`ucontext_t` translation for guest handlers, no real-time signals | H |
| [ ] | Flags and constants | `O_*`, `MAP_*`, `PROT_*`, `AT_*`, `F_*`, `SOCK_*`, `MSG_*`, `SO_*`, `SCHED_*`, `CLOCK_*` (Linux MONOTONIC → `CLOCK_UPTIME_RAW`, BOOTTIME → Darwin MONOTONIC) | M |
| [ ] | Struct layouts | `stat`/`stat64`, `dirent`, `statfs`, `sockaddr` (`sa_len`, `AF_INET6` 10 ↔ 30), `addrinfo`, `msghdr`/`cmsghdr`, `timespec`, `rlimit`, `utsname`, `termios`, `pollfd`, `epoll_event` | H |
| [ ] | pthread objects | Out-of-line host objects for `pthread_mutex_t`/`cond_t`/`rwlock_t`/`once_t`/`key_t` (size and value differences); thread naming applied from the target thread; `pthread_setschedprio` emulation | H |
| [ ] | Linux-only calls used by apps | `epoll_*` → kqueue, `eventfd` → pipe/`EVFILT_USER`, `timerfd` → `EVFILT_TIMER`, `memfd_create` → unlinked `shm_open`, `futex` → `os_sync_wait_on_address`, `prctl` subset, `getauxval`, `__system_property_get` | H |
| [ ] | malloc family | `malloc_usable_size` → `malloc_size`, `memalign` → `posix_memalign`, `mallopt`/`mallinfo` stubs | L |
| [ ] | Dynamic linker API | `dlopen`/`dlsym`/`dladdr`/`dl_iterate_phdr`/`android_dlopen_ext` over the loader's own module registry; ELF `.so` never reach dyld | M |
| [ ] | Debugger support | GDB JIT interface (`__jit_debug_register_code`) so LLDB can see guest modules | M |
| [ ] | `/proc` and `/sys` views | Synthetic `/proc/self/maps`, `/proc/self/exe`, `/proc/cpuinfo`, `/proc/meminfo` answers for guest `open`/`readlink` | M |
| [ ] | NDK library map | Guest `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libz.so`, `libandroid.so`, `libEGL.so`, `libGLESv1_CM.so`, `libGLESv2.so`, `libGLESv3.so`, `libOpenSLES.so`, `libaaudio.so`, `libjnigraphics.so`, `libmediandk.so`, `libvulkan.so` resolve to Shiraz host libraries | M |
| [ ] | ART NativeBridge hook | Reuse `native/digitalis_bridge.cpp` shape; ARM64→ARM64 needs no register marshalling | M |

### 3. Graphics: presentation, canvas, EGL/GLES, buffers

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [~] | Window and layer | `native/presentation.cpp`, `patches/sdl-cocoa-gles-layer-scale.patch` | SDL Cocoa window. With ANGLE built, a Cocoa window uses SDL's `opengles2` renderer (Cocoa EGL on ANGLE Metal); without it, the `metal` renderer. The SDL patch sets the GL view layer's `contentsScale` from the window's backing scale; without it a 1x display kept a stale 2.0, so ANGLE's drawable was 720x1280 under a 360x640 SDL output and the frame filled one quarter of the window. Under `__APPLE__` the presenter logs `SHIRAZ_EGL_SURFACE` (SDL output vs EGL surface size), which the windowed smokes require equal. Metal-layer handle and live resize still open | M |
| [ ] | GPU capability probe | `native/presentation.cpp` | `MTLCreateSystemDefaultDevice().name` instead of `glGetString`; Metal-device gate instead of GL-context gate | L |
| [ ] | Live resize | `native/presentation.cpp` | Render from an `SDL_AddEventWatch` callback during Cocoa live resize, or `presentsWithTransaction`; relayout on every size change | M |
| [x] | GPU canvas | `native/gpu.cpp`, `include/shiraz/gpu.hpp`, `scripts/bootstrap_skia.py` | M4a: unchanged `native/gpu.cpp`, Skia Ganesh GL on SDL's ANGLE Metal context (`SHIRAZ_CANVAS backend=skia-ganesh`, no fallback, checked by the opengles2 window smokes). Ganesh-Metal is not used (see GLES and GPU canvas (M4a)) | M |
| [x] | Symbol exports | `native/gpu.exports`, `native/darwin/*.exports`, `native/darwin/gpu.cxx-symbols` | ld64 `-exported_symbols_list`; each list is generated and checked by `scripts/darwin_jni_exports.py` against the sources before linking and against `nm -gU` after | L |
| [x] | EGL display | `native/egl_shim.cpp`, `native/gles_version.cpp`, `native/from_surface.cpp` | ANGLE Metal; reqGlEsVersion 3.0. The shim loads only an absolute `SHIRAZ_EGL_LIBRARY` on macOS (dyld would search the cwd for a leaf name) and stays loadable without ANGLE (weak libGLESv2, `make macos-egl-shim-no-angle-test`). `from_surface.cpp` uses its own keyed ANGLE display (`EGL_PLATFORM_ANGLE_DISPLAY_KEY_ANGLE`) that SDL's `eglTerminate` at teardown cannot end, and app contexts are unshared on macOS (two ANGLE contexts of one share group on two threads deadlocked in `glReadPixels`) | M |
| [ ] | EGL window surfaces (zero copy) | `native/egl_shim.cpp` | IOSurface pbuffers (`EGL_ANGLE_iosurface_client_buffer`) sampled by Metal; remove the `glReadPixels` readback and the two following CPU copies | H |
| [~] | GLES 1/2/3 | `native/gles1_jni.cpp`, `native/gles2_jni.cpp` | ANGLE `libGLESv1_CM`/`libGLESv2` dylibs, unchanged JNI sources. The gles fixture renders an ES 2/3 view (shader triangle, VAO) beside an ES 1 view (GL10, GLUtils texture), or two ES 2 views (`--gles-lower es2`), headless and windowed; frames still reach the window by `glReadPixels` readback. Still open: detect and report ANGLE-Metal GLES1 gaps (logic op, paletted textures, smooth lines, two-sided lighting, matrix palette) | L |
| [~] | AHardwareBuffer | `native/hardware_buffer.cpp`, `include/shiraz/hardware_buffer.h` | `IOSurfaceCreate`, `IOSurfaceLock`/`GetBaseAddress`/`GetBytesPerRow`; cross-process via `IOSurfaceCreateMachPort` / `IOSurfaceLookupFromMachPort`; Metal import with `newTextureWithDescriptor:iosurface:plane:` | H |
| [ ] | EGLImage from native buffer | `native/egl_shim.cpp` | `EGL_NATIVE_BUFFER_ANDROID` → IOSurface / `EGL_METAL_TEXTURE_ANGLE` | M |
| [~] | Sync fences | `native/sync_fence.cpp` | Shiraz-owned pollable fd (pipe) signalled from `MTLSharedEvent` listener; `EGL_ANGLE_metal_shared_event_sync`; emulate `EGL_ANDROID_native_fence_sync` and `sync_merge` | H |
| [ ] | SurfaceTexture | `native/surface_texture.cpp` | Fails explicitly on macOS (`SHIRAZ_UNSUPPORTED surface-texture`, `IllegalStateException`): ANGLE chromium/8127 Metal exposes neither `GL_OES_EGL_image_external` nor `EGL_KHR_gl_texture_2D_image` (probe in build/macos/m4/fix/surface-texture). Needs an IOSurface path (`EGL_ANGLE_iosurface_client_buffer`) in M4b | M |
| [~] | ImageReader | `native/image_reader.cpp` | Pooled IOSurface buffers; `eventfd` → pipe | M |
| [x] | Shared window frames | `native/shared_frame.cpp`, `native/native_window.cpp` | Unlinked `shm_open` (no seals) or IOSurface pixels; robust mutex → seqlock or `os_sync_wait_on_address` (shared) with owner-PID recovery | M |
| [ ] | Frame pacing / Choreographer vsync | `native/choreographer.cpp` | `NSView`/`NSWindow` `displayLink` (CADisplayLink); ProMotion rates; `CVDisplayLink` not used | M |
| [ ] | Headless verification | `patches/sdl-offscreen-resize.patch`, smokes | Offscreen `MTLTexture` render and readback for automated tests | M |
| [ ] | Vulkan (after GLES) | new | KosmicKrisp (macOS 26+) or MoltenVK; Shiraz layer mapping `VK_KHR_android_surface` → `VK_EXT_metal_surface` and AHardwareBuffer import → IOSurface | H |

### 4. Input and windowing

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [ ] | Pointer mapping | `include/shiraz/pointer.hpp`, `display_geometry.hpp` | Unchanged math; Retina density (2x → 320 dpi) and fractional scaled modes verified | L |
| [ ] | Touch events | `native/presentation.cpp` | Accept only `SDL_TOUCH_DEVICE_DIRECT`; trackpad (indirect) fingers must not become Android touches | L |
| [ ] | Trackpad gestures | `native/presentation.cpp` | Pinch/magnify and two-finger scroll → Android scale gestures and scroll events | M |
| [~] | Keyboard | `include/shiraz/sdl_keyboard.hpp`, `include/shiraz/keyboard.hpp` | In-tree `KEY_*` table (record provenance and license); Command-key shortcut policy | L |
| [ ] | IME | `native/presentation.cpp` | `NSTextInputClient` via SDL text input, including preedit (`SDL_TEXTEDITING`) | M |
| [ ] | Clipboard | framework | `NSPasteboard` via SDL clipboard | L |
| [ ] | Main-thread rule | `native/presentation.cpp`, `native/jni_surface.cpp`, `ActivityRunner.java` | All Cocoa/SDL video work on the primordial thread; Android main Looper integrated with the Cocoa run loop | M |
| [ ] | App Nap | launcher | `NSProcessInfo beginActivityWithOptions:` while navigating or playing audio | L |

### 5. OS primitives

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [x] | ALooper | `native/looper.cpp` | kqueue: `EVFILT_READ`/`EVFILT_WRITE` per fd, `EVFILT_USER` wake, `EVFILT_TIMER`; `EV_EOF` → hangup | M |
| [x] | Choreographer timer fallback | `native/choreographer.cpp` | Private kqueue holding a one-shot relative `EVFILT_TIMER` (`NOTE_NSECONDS`), registered on the looper through `ALooper_addFd`; relative so deadlines stay on `CLOCK_MONOTONIC` | L |
| [x] | ASharedMemory | `native/shared_memory.cpp`, `MemoryFile.java` | `shm_open(O_CREAT|O_EXCL)` + immediate `shm_unlink` + single `ftruncate`; size side table; removing `PROT_WRITE` fails with `ENOTSUP` because Darwin cannot refuse new writable mappings | M |
| [ ] | Clocks | `native/host_process.cpp` | Linux MONOTONIC → `CLOCK_UPTIME_RAW`; BOOTTIME → Darwin `CLOCK_MONOTONIC` | L |
| [ ] | Process information | `native/host_process.cpp` | `pthread_threadid_np`, `proc_pidinfo(PROC_PIDTBSDINFO)`, `_NSGetArgv`/`_NSGetArgc`, `host_statistics64`, `malloc_zone_statistics`; no reversible `PR_SET_DUMPABLE` | L |
| [x] | IPC channel | `native/ipc_channel.cpp`, `native/ipc_jni.cpp` | `socketpair` + `FD_CLOEXEC`, `SO_NOSIGPIPE`, peer credentials via `LOCAL_PEERPID`/`LOCAL_PEERCRED` instead of `SCM_CREDENTIALS` | L |
| [ ] | Process spawn | `native/ipc_spawn.cpp`, `ServiceProcess.java` | `POSIX_SPAWN_CLOEXEC_DEFAULT` + `adddup2`, `_NSGetEnviron()`, explicit child command (no `/proc/self/cmdline`) | L |
| [ ] | libc interposition | `native/android_process_compat.c`, `native/android_font_files.c` | Loader override table for guest code; `__DATA,__interpose` only where host code must be redirected | M |
| [ ] | fd helpers, sqlite | `native/fds.cpp`, `native/sqlite.cpp` | Portable; system `libsqlite3` or vendored amalgamation | L |

### 6. Media

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [ ] | Audio output | `native/audio_output.cpp`, `native/speech_output.cpp` | SDL audio (CoreAudio backend) or AudioUnit `kAudioUnitSubType_DefaultOutput` | M |
| [ ] | OpenSL ES | `native/opensles.cpp` | AudioQueue buffer callbacks ↔ `slAndroidSimpleBufferQueueCallback` | M |
| [ ] | AAudio | new | AudioUnit render callback | M |
| [ ] | Media playback | `native/playback.cpp` | Output through the audio backend; AAC via AudioToolbox `AudioConverter` (or keep the existing decoders) | M |
| [ ] | Video decode (MediaCodec, NDK media) | `native/video_codec.cpp`, `native/media_ndk.cpp` | VideoToolbox `VTDecompressionSession`: H.264, HEVC, VP9 (after `VTRegisterSupplementalVideoDecoderIfAvailable`), AV1 only when `VTIsHardwareDecodeSupported` (M3+); libvpx for VP8; IOSurface/Metal-compatible output via `kCVPixelBufferIOSurfacePropertiesKey` | M |
| [ ] | Opus | `native/opus_codec.cpp` | In-tree libopus or AudioToolbox Opus | L |
| [ ] | Camera | `native/capture.cpp`, `Camera.java` | `AVCaptureSession` + `AVCaptureVideoDataOutput`; `NSCameraUsageDescription` | M |
| [ ] | Microphone | `native/capture.cpp` | `AVAudioEngine` input tap or SDL capture; `NSMicrophoneUsageDescription` | M |
| [ ] | Text to speech | `native/speech.cpp` | `AVSpeechSynthesizer writeUtterance:toBufferCallback:`; espeak-ng fallback | L |
| [x] | DRM | `native/media_crypto.cpp` | Unsupported, explicit error | L |

### 7. Platform services

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [ ] | Notifications | `native/notifications.cpp` | `UNUserNotificationCenter` (bundle required); urgency → `interruptionLevel`; no timeout | M |
| [ ] | Location | `LocationManager.java` | `CLLocationManager`; Info.plist `NSLocationUsageDescription`; Android fine/coarse → accuracy | M |
| [x] | System fonts | `native/system_fonts.cpp` | CoreText enumeration (`CTFontManagerCopyAvailableFontURLs`, `.ttc` face index); bundled Android fonts first | M |
| [x] | Text shaping | `native/text.cpp` | In-tree FreeType, HarfBuzz, fontconfig and ICU (system ICU has no public headers); in-tree `fonts.conf` prefers the bundled Noto fonts | M |
| [ ] | Connectivity | `HostConnectivity.java` | `getifaddrs`, `NWPathMonitor`; no `/proc/net`, `/sys/class/net`, `/etc/resolv.conf` assumptions | L |
| [ ] | Open URL / external apps | `ExternalLauncher.java` | `/usr/bin/open` or `NSWorkspace openURL:` | L |
| [ ] | Document picker | `HostContentPicker.java` | `NSOpenPanel` / `NSSavePanel` | L |
| [ ] | App storage | runtime, launcher | `~/Library/Application Support/Shiraz/<package>`, `~/Library/Caches/Shiraz/<package>` | L |
| [x] | Sensors | `native/sensors.cpp` | Stubbed (no public accelerometer/gyroscope on macOS), reported as absent | L |
| [ ] | Keystore | framework | Software keystore first; Security.framework keychain optional | M |

### 8. Build, packaging, signing, CI

| Status | Item | Repo files | macOS implementation | Diff |
|---|---|---|---|---|
| [~] | Makefile platform split | `Makefile` | `include/darwin` JNI headers; `-dynamiclib -install_name @rpath/...`; `-Wl,-rpath,@loader_path` instead of `$ORIGIN`; ld64 default `-undefined error` instead of `-z,defs`; no `--wrap` (test seams instead); `-exported_symbols_list` | M |
| [~] | In-tree dependencies | `scripts/bootstrap_sdl.py`, `bootstrap_skia.py`, `bootstrap_angle.py` | Pinned SDL with Cocoa/Metal/EGL; ANGLE chromium/8127 Metal dylibs and Skia Ganesh-GL archives (`skia_use_metal=false`) built in-tree by `make macos-gpu-deps` from clean, pinned checkouts, reused only by build record or a `ninja -n` "no work to do"; no Homebrew runtime dependency. Metal's shader caches live outside the checkout, per user (see GLES and GPU canvas (M4a)) | M |
| [ ] | Host ABI detection | `src/shiraz/architecture.py` | `arm64` → `arm64-v8a`; flag `p_align < 16384` libraries for copy-load | L |
| [ ] | `.app` bundle builder | new `scripts/build_macos_app.py` | `Contents/MacOS`, `Contents/Frameworks`, `Info.plist` (`CFBundleIdentifier`, usage strings), `.icns` from the APK icon, `otool -L` closure, `install_name_tool` | M |
| [ ] | Code signing | builder | Ad-hoc `codesign -s -` locally; Developer ID + `notarytool` optional; hardened runtime with `com.apple.security.cs.allow-jit` (one `MAP_JIT` region) | M |
| [ ] | Browser Builder | `go-wasm/` | Do not append to a signed Mach-O; emit an unchanged signed `Shiraz.app` plus APK sidecar, or prove an in-browser ad-hoc signer | H |
| [ ] | Desktop integration | `packaging/desktop_install.h` | Per-APK wrapper `.app` in `~/Applications` | L |
| [ ] | CI | `.github/workflows/` | macOS arm64 runner job: deps, build, headless smokes; verify `MAP_JIT` works on runner VMs | M |
| [ ] | Version exposure | CLI, native app | `--version` on the macOS binaries and in `Info.plist` | L |

### 9. Licensing and provenance

Every vendored or bundled component keeps its original notices: ANGLE
(BSD-3-Clause plus its third-party dependencies), Skia (BSD-3-Clause), SDL
(zlib), KosmicKrisp (MIT) or MoltenVK (Apache-2.0), AOSP sources (Apache-2.0),
the Linux `KEY_*` table (GPL-2.0 WITH Linux-syscall-note), and rcodesign
(MPL-2.0) if used as a tool. Apple SDK headers are never vendored.
The macOS ANGLE and Skia builds are listed, with their pins and licenses, in
`LICENSES/MACOS-GPU-NOTICE.txt`; the install writes the license files and a
PROVENANCE file to `.local/share/licenses/{angle,skia}`.

## Verified on macOS

Host: Apple M2 Pro, macOS 27.2. Evidence: `make macos-check` (exit 0), which runs
every macOS test, the Metal smoke, `scripts/darwin_linux_guard.py` (Linux text
unchanged), the version check and the commit guard.

- Presentation (partial): `shiraz-preview` opens a Cocoa window and logs
  `SHIRAZ_PRESENTATION renderer=metal accelerated=1 vsync=1`. The Metal layer
  handle is not fetched yet, the GPU name probe still prints `device=unknown`,
  and live resize and the trackpad filter are open.
- `libandroid.so` links all fifteen Linux libandroid sources as a Mach-O dylib
  against CoreFoundation, CoreText and IOSurface only.
- Passing tests: native-window, shared-frame, choreographer, sensors (reported
  absent), shared-memory, image-reader, hardware-buffer (CPU path), system-fonts,
  media-crypto, media-ndk, ipc-channel, sync-fence, keyboard, pointer, compositor,
  archive-path, presentation and live-input (headless dummy/software).
- Partial rows:
  - AHardwareBuffer: IOSurface allocation, CPU lock and unlock pass. Cross-process
    export and the socket handle calls return `-ENOTSUP` with
    `SHIRAZ_UNSUPPORTED hardware-buffer-cross-process` until the Mach-port path lands.
  - Sync fences: pipe-backed fences pass `tests/darwin/sync_fence.cpp` (merge,
    wait, timeouts, writer close, merge of merges, descriptor leaks). The
    `MTLSharedEvent` and EGL producers wait for M4.
  - ImageReader: delivery, lifetime and fences pass on IOSurface buffers; there is
    no GPU producer yet.
  - Keyboard: the in-tree `KEY_*` table is in; the Command-key policy is not.
  - Makefile: `mk/darwin.mk` builds libandroid, the preview, the tests and the
    SDL-only dylibs (`make macos-build`, gated by `make macos-check`; no ART
    needed). `make runtime-art` (alias `macos-art`) builds the darwin-x86 ART host
    tree; `make macos-runtime-libs` then links `libshiraz-resources.so` against its
    libandroidfw, and `make macos-runtime-smoke` runs the fixture APK. The GPU
    canvas is a stub that reports no GPU until M4.
- Compiled only, not yet run on macOS: `native/host_process.cpp`,
  `native/ipc_spawn.cpp`, `native/ipc_jni.cpp` and `native/fds.cpp`. Their
  designed behaviour, still to be tested:
  - `Process.shirazSetDumpable(false)` lowers the soft `RLIMIT_CORE` to 0 and
    prints `SHIRAZ_UNSUPPORTED PR_SET_DUMPABLE` once. Same-user debugger attach is
    not blocked, because Darwin has no reversible control and the call runs on
    every launch.
  - Process spawn uses `POSIX_SPAWN_CLOEXEC_DEFAULT` and `_NSGetEnviron()`.
- Recorded macOS differences:
  - IPC peer pid comes from `LOCAL_PEERPID` at receive time. A packet whose sender
    already exited is rejected with `-EPROTO`; the gap is reported once.
  - kqueue descriptors are not inherited across `fork()`; Shiraz spawns children
    with `posix_spawn`.
  - CoreText lists 736 font faces here; Apple's hidden dot-prefixed UI fonts are
    not listed.
- Open review findings:
  - `getStartUptimeMillis` counts sleep, and `Process.getStartElapsedRealtime()`
    adds the sleep difference again. The flaw is shared with Linux but shows more
    on laptops that sleep often.
  - `RGBX_8888` maps to IOSurface `'RGBA'`, which loses "alpha ignored" for the
    M4 Metal/ANGLE import; BGRA may also be needed there.
  - The fence and image-reader workers spin if `poll()` fails with an error other
    than `EINTR`.
  - Pipes, socket pairs and received descriptors get `FD_CLOEXEC` after creation,
    not atomically. Shiraz's own spawn is safe; other fork/exec paths are not.

### ART on darwin-arm64 (M3 first exit)

`python3 scripts/bootstrap_runtime.py art` applies `patches/art-darwin-*.patch`
(Darwin hosts only, after the Linux patch chain), builds ICU4C 78.3, lz4 and
liblzma into `.local`, runs a cold ART make in about 2 min 20 s, stages the ICU
dylibs next to the ART natives and desugars the boot jars. `dalvikvm -Xint`
from `context/art_standalone/out/host/darwin-x86` then runs:

- a hello-world DEX: exit 0 in 20 of 20 runs under ASLR, about 80 ms wall each;
- a 22-check stress DEX: threads and monitor contention, wait/notify, interrupts,
  executors, 16 MiB of large objects across explicit GCs, NPE, bounds and
  stack-overflow errors, reflection, `java.lang.reflect.Proxy`, ICU text
  (Turkish casing, German number format, collation), UTF-8 file IO, 100k-entry
  maps, identity hashes across GC. All pass, with and without `-Xcheck:jni`, and
  under the CMS (default), SS, GSS and MS collectors;
- a JNI dylib loaded with `System.loadLibrary`, whose natives take 24 mixed-width
  arguments (narrow types and floats on the stack): results match the Java
  reference, so the Apple arm64 stack-argument packing in the generic JNI
  trampoline works.

`vmmap` of a running VM shows the reservation at `0x10000000000` (64 KiB guard)
and the main space at `0x10012c00000`.

Two adversarial review rounds and the fixes they prompted found 24 defects in
the port. Each now has a fix and a regression run that failed before it:

- Heap: Darwin `MADV_DONTNEED` does not zero pages. RosAlloc, `RosAllocSpace`
  clear/create and `BumpPointerSpace::Clear` now zero explicitly with
  `MADV_ZERO`, and only over writable pages. Before the fix, homogeneous-space
  compaction for OOM ended in `SIGBUS`, and so did zygote collector transitions,
  whose backup space is `PROT_NONE`. Also fixed: empty-range bitmap walks
  with a non-zero bitmap base, which crashed GSS with the map large-object
  space, and a 32-bit direct-buffer address in `VarHandle`.
- Time and sync: `NanoTime`/`MilliTime`, absolute `InitTimeSpec` and the futex
  shim all use `CLOCK_UPTIME_RAW` (`mach_absolute_time`), so a wall-clock step
  cannot fire a thread-suspension timeout. The futex shim also saturates
  timeout arithmetic, slices `FUTEX_CLOCK_REALTIME` waits to 1 s and treats a
  transient `EFAULT` as a spurious wake-up.
- Signals: XNU discards a blocked signal whose disposition is `SIG_IGN`. A VM
  started as a background job, where the shell ignores `SIGQUIT`, hung at exit
  waiting for its signal catcher. `SIGQUIT` and `SIGUSR1` are now reset to the
  default action before they are blocked.
- Loader: `dlopen` of a native library never passes a bare leaf name to dyld,
  which searches the current directory first. A `libjavacore.so` planted in the
  working directory ran before the fix.
- libcore: `SO_DOMAIN`/`SO_PROTOCOL` (emulated through `proc_pidfdinfo`) and
  `IP_MULTICAST_ALL` (0 is a no-op, other values fail with `ENOPROTOOPT`) are
  private sentinels on Darwin. Before the fix, every `ServerSocket.accept` and
  every `DatagramSocket` constructor failed. Further libcore fixes:
  - NIO socket options are translated from the Linux numbers to Darwin's.
  - `SO_LINGER` is `SO_LINGER_SEC`, so lingers last seconds as on Linux, not
    10 ms ticks.
  - `IP_TOS` on a dual-stack IPv6 socket that can carry IPv4 traffic throws
    `ENOPROTOOPT` instead of reporting a success that never reaches the wire.
  - The `IP_MULTICAST_ALL` emulation validates its descriptor.
  - Accepted sockets no longer inherit `O_NONBLOCK`.
  - `group_req` sockaddrs carry `ss_len`.
  - `prctl` never dereferences its argument.
  - A missing xattr reports `ENODATA`.
  - NIO file times keep nanoseconds.

The regression suite passes from the bootstrap output (logs kept outside the
repository). It covers hello, stress, JNI with and without `-Xcheck:jni`, every
collector, both large-object spaces, sockets, NIO options, xattrs, the OOM
compaction case, zygote collector transitions, a VM with `SIGQUIT` ignored, a
20 s park/wait/stack-walk run and the planted library.

Not done yet:
- Boot image: image reservations outside the window are refused.
- JIT, dex2oat/AOT and mterp.
- The debug libart, so the window `DCHECK`s have never run.
- Signal interposition: libsigchain cannot interpose `sigaction` under
  two-level namespaces, so the fault manager stays off and implicit null and
  stack checks are disabled.
- Networking gaps:
  - IPv4 socket options on IPv6 sockets: XNU rejects `IPPROTO_IP` options on
    `AF_INET6` sockets. This breaks `MulticastSocket` TTL, loopback and
    interface, `DatagramSocket.setTrafficClass` and IPv4 multicast on dual-stack
    sockets. NIO IPv4 multicast works.
  - IPv6 multicast send fails with `EHOSTUNREACH`.
  - Linger values above 327 s fail with `EDOM`; Linux accepts up to 65535 s.
- The `SIGQUIT` thread dump hangs unless `-XX:DumpNativeStackOnSigQuit:false`
  is given (native stack unwinding of other threads is not ported).
- Working: `Selector` uses `poll`.
- Failing with `IOException: Function not implemented`: asynchronous channels
  (`epoll`) and `WatchService` (`inotify`).

### Shiraz framework in a Metal window (M3 second exit)

`PYTHONPATH=src python3 -m shiraz run build/macos-apk/minimal.apk` boots the
pure-Java fixture (`tests/macos-apk/`) through the Shiraz framework on darwin
ART and presents its first frame in an SDL2 Cocoa window with the Metal
renderer. The frame comes from the real path (ActivityRunner, the Activity,
`View.draw` into the software canvas, SDL presentation). The launcher writes no
pixels.

Build order on a fresh Apple Silicon checkout:

1. `make macos-deps`, then `make macos-check` (SDL only, no ART).
2. `make runtime-art` (alias `macos-art`).
3. `make fonts macos-graphics-deps` (python3 3.12+; `macos-runtime-libs` now
   links the text, path and image libraries, see "Text, paths and images").
4. `make macos-runtime-libs macos-framework macos-framework-resources macos-apk-fixture`.
5. `make macos-runtime-smoke` (headless) and `make macos-runtime-window-smoke` (Metal).

The smoke fails unless all of the following hold:
- the run exits 0;
- the content and `SHIRAZ_RUNTIME_CLEANUP_COMPLETE` markers appear;
- the SDL backend and renderer are the expected ones;
- the display geometry matches the requested size and density;
- the snapshot size matches the reported pixels;
- the centre pixel is `0x123456`.

What it needs and what it shows:

- **Native libraries:**
  - New macOS dylibs: libshiraz-host, -bitmap, -sqlite (SDK sqlite 3.54) and
    -resources (linked against the darwin libandroidfw).
  - libandroidfw now builds in the ART tree with a hidden in-tree libpng 1.6.59.
  - `ActivityManager` reports no GLES version (0) until M4.
- **Main thread:** `ActivityRunner.main` runs on the primordial thread. The logs
  show `pthread_main_np=1` for display preparation and presentation. The
  surface bridge refuses SDL video calls from any other thread.
- **Unavailable on macOS, failing explicitly:**
  - `http`/`https` `URL.openConnection` throws `IOException`, because okhttp and
    wolfJSSE are not ported. `art-darwin-url-handlers.patch` makes libcore fall
    back to `shiraz.net` handlers when the okhttp handler classes are absent.
    Apps can still set `java.protocol.handler.pkgs`.
  - `SSLContext` has no provider.
  - `MemoryFile` throws `IOException`.
- **Optional libraries not built yet:** -audio, -video, -gles1/-gles2 and the
  others (text, path and image are built since the next section). They are
  reported at launch with `SHIRAZ_HOST_UNAVAILABLE` and fail on first use with
  `UnsatisfiedLinkError`.
- **Runs, on a warm page cache:**
  - 10 of 10 headless runs exit 0, median 0.33 s.
  - 10 of 10 windowed Metal runs exit 0, median 0.79 s.
  - No VM process is left behind.
- **Window capture:** a window-only capture stores Display P3 `1b3353`, which is
  sRGB `0x123456`.
- **APK path:** the launcher now resolves the APK path before building the
  classpath. A relative path failed on every host, because ART runs with its
  working directory in the app data directory.
- **Downloads:** Google SDK downloads try the redirector.gvt1.com mirror after
  any dl.google.com failure (404, truncation, wrong bytes). Every candidate is
  checked against its pinned sha256 or content check before it is renamed into
  place.

Not done yet:
- Audio and the rest of the optional libraries (text, paths and images are done;
  see below).
- TLS and HTTP.
- An arm64 device profile: the shipped profile is the x86_64 emulator identity,
  so macOS rejects it.
- Resize and input on macOS.
- Cold-start timing.
- Linux `make check` on these changes.
- Cold ART builds are flaky on this host. In five from-pin
  `bootstrap_runtime.py art` runs (`checkout -- . && clean -fdx -e out`) there
  were three transient failures, and each one passed on rerun:
  - one dx failure for core-oj-hostdex (`trouble writing output: excess write
    of 8`);
  - two clang failures in different libart files (`'android-base/logging.h'
    file not found`), although the header exists and `import_includes` lists
    `libbase/include`.

  Six concurrent dx runs on the same input produced identical output. The
  cause is unknown.

### Text, paths and images

`libshiraz-text`, `libshiraz-path` and `libshiraz-image` build on macOS from the
unchanged `native/text.cpp`, `path.cpp`, `ripple.cpp` and `image.cpp`. An APK
draws Latin, Persian and Hebrew text, vector paths and decoded PNG, JPEG and
WebP images through the Activity/View path, headless and in a Metal window.

Build steps (python3 3.12+ is required for the tarball filter):

1. `make fonts macos-graphics-deps` (step 3 above) builds pinned FreeType 2.14.3, HarfBuzz
   15.1.0, fontconfig 2.18.3, pixman 0.46.4, cairo 1.18.6, libwebp 1.6.0 and
   libjpeg-turbo 3.2.1 as static arm64 archives in `.local` (Meson and Ninja in an
   in-tree venv). zlib and expat come from the SDK; libpng and ICU come from
   `make macos-art-deps`. Pins and licenses are in
   `LICENSES/MACOS-GRAPHICS-NOTICE.txt`.
2. `make macos-graphics-deps-check` links and runs one small C test per library.
3. `make macos-graphics-libs` (also a prerequisite of `macos-runtime-libs`) links
   the three dylibs. Each exports only the `Java_*` symbols that the Java
   classes declare (`native/darwin/{text,path,image}.exports`, checked by
   `scripts/darwin_jni_exports.py` before and after linking).
4. `make macos-apk-graphics-fixture`, then `make macos-runtime-graphics-smoke`
   and `make macos-runtime-graphics-window-smoke`.

fontconfig stays inside the checkout:
- `scripts/bootstrap_darwin_graphics.py` writes `.local/etc/fonts/fonts.conf`.
  It lists the bundled font directory and, as read-only fallback,
  `/System/Library/Fonts` and `/Library/Fonts`. Its cache is
  `.local/var/cache/fontconfig`.
- Strong aliases make sans-serif prefer Noto Sans, Noto Naskh Arabic and Noto
  Sans Hebrew (the bundled `android-fonts.xml` order). Without them the host's
  Supplemental Arial won for Arabic and Hebrew.
- The launcher strips inherited `FONTCONFIG_*`, sets `FONTCONFIG_FILE` and
  `FONTCONFIG_PATH` to the in-tree config, and refuses to start if that config
  is unreadable, lists per-user or relative paths, or points into another
  checkout.
- `~/.cache/fontconfig`, `~/.fontconfig`, `~/.config/fontconfig` and
  `~/Library/Caches/fontconfig` stay absent after the runs.

The graphics smoke (`tests/macos-apk-graphics/`) checks:
- exact pixels for the background, circle, round-rect stroke, PNG and WebP
  quadrants and the PNG/WebP round trips, and the JPEG within 8 per channel;
- the measured widths of the three strings against the bundled-font values,
  within 1.5 %;
- per-region ink bounding boxes and column/row ink profiles, which reject
  notdef boxes and reversed glyph order.

The windowed smoke checks the CPU-composited snapshot and that `renderer=metal`
was selected. It does not read back what Metal presented.

Runs on a warm page cache: 10 of 10 headless runs exit 0 (median 0.37 s) and
10 of 10 windowed Metal runs exit 0 (median 0.83 s).

The Android API probe (`make api-probe-apk`), which crashed at
`TextView.onMeasure` with `UnsatisfiedLinkError` for `libshiraz-text.so`,
now draws its controller UI headless and in a Metal window.

Not done yet:
- Upstream test suites of the pinned libraries are not run.
- The 720x1280 at density 320 case has not run on a real render.
- Bidi and shaping are checked by ink profiles and by eye, not by glyph IDs.

### GLES and GPU canvas (M4a)

OpenGL ES and the GPU canvas run on ANGLE's Metal backend. `native/gpu.cpp`,
`egl_shim.cpp`, `gles1_jni.cpp`, `gles2_jni.cpp` and `presentation.cpp`
compile from the Linux sources; macOS-only code is under `#if
defined(__APPLE__)` or in `native/darwin/`, and `scripts/darwin_linux_guard.py`
checks that the Linux text is unchanged.

Build order (after the text/path/image steps above):

1. `make macos-gpu-deps`: `scripts/bootstrap_angle.py` fetches ANGLE
   chromium/8127 and its DEPS-pinned checkouts with plain git, builds gn from
   source and ninja-builds `libEGL`, `libGLESv2` and `libGLESv1_CM` with Xcode
   clang into `.local/lib/angle`; then `scripts/bootstrap_skia.py` builds the
   Skia archives in `.deps/skia/out/shiraz-darwin`. Needs python3 3.11+.
   Checkouts must be clean at their pins (Chromium `build/` must equal its pin
   plus `patches/angle-build-apple-clang.patch`). A tree is reused only when
   its build record matches (pins, gn args, patch, clang, SDK, python3) or,
   with no record, when `ninja -n` prints "no work to do".
2. `make macos-gpu-deps-check` runs `tests/darwin/gles/angle_probe.c` on the
   installed dylibs.
3. `make macos-build`, `make macos-runtime-libs`, `make macos-apk-gles-fixture`.
4. Smokes: `make macos-smoke-gles2` (preview on opengles2),
   `macos-runtime-gles-smoke`, `macos-runtime-gles-window-smoke`,
   `macos-runtime-gles-es2-pair-smoke`, `macos-runtime-gles-es2-pair-window-smoke`,
   `macos-runtime-gles2-window-smoke`, `macos-runtime-graphics-gles2-window-smoke`,
   and the JVM GLES smokes (`make macos-gles-jvm-smokes`,
   `make macos-gpu-canvas-smoke`). `make macos-egl-shim-test` includes the
   no-ANGLE shim test.

Verified on the reference Mac (M2 Pro, 1x Odyssey G93SC main display):
- the gles fixture's ES 2 and ES 1 frames match the app's own `glReadPixels`
  results and the frame checks, headless and in a window; the text region
  has the "Shiraz GLES" ink shape;
- every windowed opengles2 run reports `accelerated=1`, an ANGLE Metal
  device, `SHIRAZ_CANVAS backend=skia-ganesh` without fallback, and
  `SHIRAZ_EGL_SURFACE ... match=1`. With an SDL built without
  `patches/sdl-cocoa-gles-layer-scale.patch` the same check fails
  (`output=360x640 ... egl=720x1280 match=0`; build/macos/m4/fix/f1);
- two ES 2 GL threads at once (`--gles-lower es2`): 10 of 10 headless and 10
  of 10 windowed runs pass after the contexts were unshared; before, 3 of 6
  headless runs hung in `glReadPixels` (build/macos/m4/fix/f2). A GL thread
  that throws or does not stop within 2 s now fails the app
  (`SHIRAZ_RUNTIME_FATAL`) instead of leaving a black view;
- `from_surface.cpp` contexts stay usable after SDL's renderer, window and
  video subsystem are torn down (build/macos/m4/fix/pa5).

Design choice: Ganesh-GL on ANGLE-Metal, not Ganesh-Metal. The canvas then
shares SDL's GL context and the Linux `native/gpu.cpp`, so the same Skia
code, flags and readback paths are tested on both hosts, and app GLES
frames, the canvas and the presenter all go through one ANGLE device. A
Ganesh-Metal canvas would need a second, macOS-only GPU backend
(`GrDirectContexts::MakeMetal`, a `CAMetalLayer` drawable) and a separate
composition path with the GLES frames, for no gain while app GLES frames
still come back by readback.

Metal caches: ANGLE compiles Metal shaders at run time, and Metal stores
compiled functions and libraries per user under `getconf
DARWIN_USER_CACHE_DIR`, outside the checkout: `com.apple.metal/` for
unbundled processes (preview, dalvikvm) and
`net.java.openjdk.java/com.apple.metal/` for the JVM smokes. They survive
`make clean` and rebuilds, so a cold-launch time must state whether this
cache was warm.

Not done yet:
- IOSurface zero-copy for app GLES frames (M4b); frames still go through
  `glReadPixels` and two CPU copies.
- `EGL_ANDROID_native_fence_sync` / `MTLSharedEvent` fences.
- SurfaceTexture and EGLImage from native buffers (no
  `GL_OES_EGL_image_external` on ANGLE Metal; fail explicitly).
- GLES1 gap reporting for ANGLE-Metal.
- Live resize of the GL window and the Metal-layer handle.
- A GL window that moves between displays of different backing scale was
  checked only with a standalone SDL/ANGLE probe (build/macos/m4/hidpi), not
  with a running app.

### Guest ELF and JNI foundations (M5, incomplete)

After building the Darwin ART runtime, the native-library fixture gates are:

```sh
make runtime-art
make macos-guest-check
make macos-guest-jni-adhoc-check
```

The first gate checks memory layout, bounded ELF parsing and malformed inputs,
four AArch64 RELA relocation forms, constructors/BSS, scalar argument marshalling,
generated closures and guest `va_list` decoding. It also runs dependency-group and
initial libc-personality fixtures, including version matching, breadth-first lookup,
`DT_SYMBOLIC`, global precedence, cycles, weak-null imports, failure rollback and
per-thread errno translation. A link-only API permits binding inspection without
executing constructors. Executable fixtures run ad-hoc
and hardened with only `allow-jit`; parser rejection tests use ASan/UBSan.
File-I/O fixtures cover real files, guarded guest stat/flock layouts, locks,
nanosecond timestamps, directory iteration, symlinks, pipe errno translation,
32-bit ioctl requests, checked-open/read failure, microsecond timestamps and
working-directory changes. Filesystem-space queries serialize the guest statvfs
layout with full-width host counters. Layouts and constants are derived
by compiling the pinned bionic headers. The host adapter also runs under ASan/UBSan.

The memory API gate checks anonymous and file-backed mappings, shared writeback,
private copies, offsets, partial unmaps, real protection faults, concurrent calls
and bounded reservations. Mapping/protection operations are restricted to memory
owned by this provider; fixed mappings cannot replace host or loader allocations.
Guest addresses and offsets use the host's 16 KiB page granularity. Executable
guest mappings, unknown flags and `MAP_FIXED_NOREPLACE` remain explicitly refused;
`mremap` is not yet implemented.

The pthread gate checks guest entry callbacks, attributes with guarded bionic
buffers, join/detach, thread identity, per-thread keys and repeated guest key
destructors. Returned pthread errors are translated separately from errno.
Thread/destructor code pointers require validation against resident ELF images.
An immutable executable-image snapshot lets constructor-created workers validate
callbacks without waiting on the constructor's loader lock.

The synchronization gate adds static/dynamic mutexes, recursion and error-checking
types, condition signal/broadcast, realtime/monotonic deadlines and contended
one-time initialization. It checks buffer guards, busy destruction and mutex
reacquisition after timeout. Host objects stay outside guest buffers; process-shared
attributes are explicitly unsupported. Guest monotonic time uses Darwin's awake
uptime clock. Default private rwlocks additionally check concurrent readers,
writer exclusion, guarded layouts and busy destruction. Non-default rwlock
attributes remain unsupported.

Unnamed semaphore adapters keep the full bionic count range in lock-free atomics
and use an immediately unlinked private POSIX semaphore only for coalesced
wakeups. Posting takes no registry mutex. Tests cover 40,000 posts, overflow,
four-byte guest alignment, reuse, posting from an actual host signal handler and
interrupted waits. Runtime entry points initialize guest errno before guest code
can use the error path from a signal handler. This does not establish Android
signal-handler or semaphore timed-wait support; those remain unfinished.

The compiler-emulated TLS gate builds unchanged, hash-pinned LLVM compiler-rt
under ignored `.deps`, retaining its Apache-2.0 WITH LLVM-exception license.
Real guest C++ and peer-DSO fixtures verify initialized/zero TLS, over-alignment,
thread isolation and LIFO thread-exit destructors, including registration during
destruction. Native per-thread storage supplies a nonzero process stack guard.
This gate does not rewrite TPIDR instructions or implement ELF `PT_TLS`; both
remain unfinished. The fixtures use freestanding cross-compilation, with no
claim that protected OsmAnd code has executed.

The separate TPIDR trampoline gate patches explicitly declared first-party code
sites into bounded branch islands. It checks full-width GPRs, all 32 vector
registers, NZCV/FPCR/FPSR, SP/LR, PAC instruction sequences and concurrent real
per-thread pointer reads. A rewritten two-read stack-guard pattern returns
normally and aborts after deliberate corruption. Reserved x18, malformed sites
and unreachable islands are rejected. Host thread-pointer registers are never
written. Automatic ELF code-site selection and guest unwind integration remain
unpassed; the gate reports both limits and does not enable native APK launch.

The ART gate loads a real freestanding AArch64 ELF with a virtual libc dependency through NativeBridge with
CheckJNI enabled. It checks `JNI_OnLoad`, symbol lookup and `RegisterNatives`,
packed narrow stack arguments and floating-point overflow arguments, Java
upcalls through ellipsis/V/A forms, objects, fields, arrays, reflected method IDs,
exceptions, registration replacement and calls from four Java threads. A native
file roundtrip and page-mapping/protection call check the providers through ART
and the shadow JNI table. A separate
Mach-O JNI library loads after the bridge is active, checking host-library routing.
The adapter inventories all 229 callable JNI table slots from ART's own header;
this inventory does not mean every slot has dedicated runtime coverage.
Four guest-created threads additionally exercise regular/daemon attachment,
Java calls, detach/GetEnv and reattachment through the shadow VM. A constructor
also creates and joins a guest worker with a guest key destructor. Thread fixtures
exercise the mutex/condition/once adapters around attachment and Java calls. They
do not establish instruction-based bionic TLS. Four thread-exit callbacks also
reattach, call Java and detach during cleanup. Shadow `JNIEnv` ownership uses a
native pthread key to support these late callbacks.

`make macos-guest-jni-check` additionally runs hardened ART with only `allow-jit`.
This gate remains unpassed: the ad-hoc ART dylibs fail library validation without
matching Team IDs. The harness verifies the runtime signing flag and exact
entitlements and retains per-mode results; it does not disable library validation.

These are synthetic compatibility fixtures. Native APK native-code execution remains unverified:
the remaining bionic services, guest TLS and instruction handling, FastNative/CriticalNative
handling and real OsmAnd native-library execution remain unfinished. No OsmAnd
or F-Droid execution is established by these gates.

Automatic thread-pointer rewriting now uses pinned AArch64 decoding, bounded
control-flow traversal, validated unwind entries and JNI registration tables.
All seven unchanged OsmAnd libraries pass this static site analysis. Protected
ELF fixtures exercise constructors, private callbacks and canary failure in
ad-hoc, hardened allow-jit-only and sanitizer modes; ART JNI additionally runs
with compiler-generated stack protection. This establishes instruction handling
for those tests, not complete CPU compatibility or OsmAnd execution.

Explicit native services now include opaque stdio handles and scalar libm
adapters. The ten printf-family adapters support scalar variadics and va_list; positional formats, %n and binary128 remain unsupported. Original
ELF unwind frames are validated and registered with Darwin libunwind; a native
backtrace traverses two guest frames across repeated image lifetimes. Guest
C++ exceptions, LSDA/personality interoperability and generated island unwinding
remain unverified.

Guest-only libdl, explicit zlib stream/callback adapters and bounded liblog
ellipsis/va_list formatting now pass ad-hoc, hardened allow-jit-only and sanitizer
tests. Dynamic images remain resident on logical close; RTLD_NEXT and new loads
during constructors remain unsupported. Abandoned zlib streams retain their
allocations and budget charges; pre-unmap cleanup is unfinished. Integrated ART
JNI still passes ad-hoc. The unchanged OsmAnd link-only gate reports unresolved
libc services and missing libjnigraphics; no real OsmAnd constructors or
JNI_OnLoad functions have executed.

## Acceptance on macOS

All of the following, on Apple Silicon, with retained evidence:

1. OsmAnd+ (arm64-v8a APK) is fully functional through an AI-guided walkthrough
   using app-scoped macOS interaction tools: setup, maps, search,
   routing/navigation, settings and persistence across restart.
2. Cold and warm startup to the first interactive frame are measured with a
   reproducible harness.
3. Interactive portrait/landscape resizing works repeatedly, with correct
   relayout, rendering, configuration updates and pointer coordinates.
4. App GLES frames reach the screen without CPU readback.
5. Unsupported features fail explicitly and are recorded.
6. `make check` passes on macOS and on Linux.

## Milestones

| Milestone | Exit criteria |
|---|---|
| M1 Host layer foundation | Makefile builds host targets on macOS; preview demo presents through Metal with live resize and correct pointer mapping; native unit tests pass on macOS |
| M2 Platform primitives | libandroid looper, choreographer, shared memory, shared frames, image reader, fences and hardware buffers implemented on kqueue/shm/IOSurface with tests |
| M3 ART on darwin-arm64 | `dalvikvm -Xint` runs a hello-world DEX with the heap above 4 GB; then the Shiraz framework boots a pure-Java fixture APK in a Metal window |
| M4 GPU path | M4a: Skia Ganesh-GL canvas and EGL/GLES on ANGLE-Metal, GLES fixture APKs render headless and windowed with `glReadPixels` readback. M4b: IOSurface zero-copy for app GLES frames, native fence sync, SurfaceTexture |
| M5 Native libraries | Guest ELF loader runs arm64-v8a JNI libraries, including TLS rewriting and variadic thunks; NDK fixtures pass |
| M6 OsmAnd+ | Full acceptance above |
| M7 Distribution | Signed `.app` per APK, Builder support, CI |


## Current native-service and startup evidence

DSO callbacks now finalize before image unmapping, with per-DSO LIFO, recursive
registration and concurrent-callback checks. Scalar libc adapters translate
Android unsigned UTF-32 buffers explicitly instead of aliasing Darwin signed
wchar_t. The property namespace follows the existing nominal API-27 framework
profile. All ten printf-family functions have bounded formatting and translated
errors. The JNI bitmap provider unwraps the checked shadow environment before
calling Shiraz's software bitmap implementation; the ART fixture verifies RGBA,
nested locks and writeback. These fixtures do not establish real OsmAnd JNI.

All seven unchanged OsmAnd native libraries now link and initialize through
ART's NativeBridge. The real-library gate observes 115 constructors, exercises
legacy JNI opening and closing the APK's exact basemap, runs OsmAndCore PointI
intersection/orientation and deletion, and checks 83 AndroidXPath iterator
create/destroy pairs against the API-27 Path view. The retained AndroidXPath
entrypoints are tested directly because this APK stripped its public wrappers.
Native library and basemap hashes are checked against the unchanged APK.
Per-library parse/map/rewrite/bind/init timings, independently counted constructor
metadata and three unique JNI_OnLoad provider/version results are recorded by the
real-library harness. Rewritten TLS accessor backtraces now retain the interrupted
guest PC and its caller for framed and frameless functions, with normal, hardened
and sanitizer checks. A purpose-built guest C++ fixture now verifies nested RAII cleanup,
rethrow and typed catches against the unchanged APK libc++ and a hash-pinned LLVM
guest libunwind archive, in normal and hardened allow-jit-only modes. This covers
guest exception/LSDA paths; exception propagation across host ABI frames remains
unsupported.

Explicit networking, event, signal, account, cleanup, memory-remap and filesystem
adapters have normal, hardened fixture and sanitizer coverage. Signal-mask changes
are restricted to guest-owned dispositions and must be restored before shutdown;
native ART masks remain protected. A portable ELF/DEX corpus now exercises TLS,
guards, actual NEON arithmetic, signals/masks and formatting on Darwin. The
identical real-Android reference run remains unperformed, so this is not yet
differential proof. Unsupported
operations still fail explicitly; APFS statfs, inotify and advanced signal modes
remain unavailable. Typed EGL/GLES providers cover the 93 observed direct GL
imports and pass real ANGLE pbuffer/readback checks. Strict hardened ART remains
unverified: the existing ad-hoc native libraries fail library validation. The
harness now prepares isolated native copies with checked dependency resolution;
`make macos-guest-jni-hardened-prepare` needs no signing identity or execution.
Running the strict gate requires a public `SHIRAZ_CODESIGN_IDENTITY` whose signed
copies all expose the same actual TeamIdentifier. Only allow-jit is permitted on
the main executable. Successful ad-hoc ART execution does not complete M5.

The missing macOS NativeSound library is now built by macos-runtime-libs. It
uses pinned Ogg Vorbis decoding and AudioToolbox/AudioQueue with real output;
all five OsmAnd sound assets decode, and output/control/release and unavailable
device tests pass. Linux audio source is unchanged after Apple preprocessing.
Darwin SensorManager service creation now reports no supported bridged sensors
instead of probing Linux /sys; subscriptions remain unavailable.

With `SHIRAZ_GUEST_LOADER=1`, the actual OsmAnd APK now renders its native map
at 720 by 1280 and completes a bounded eight-frame run with status 0. Read-only
Android font aliases include the ART ANDROID_ROOT/fonts namespace used by the
legacy Skia implementation. The app-provided snapshot shows Iran and the Caspian
Sea with OsmAnd map controls; it is an actual app frame, not a fixture. Run:

```sh
SHIRAZ_GUEST_LOADER=1 make run APK=context/apks/net.osmand.plus_541003.apk
```

The loader remains explicitly opt-in while M5 gates are unfinished. HTTPS,
interactive walkthrough, routing, persistence, responsive resizing and full
OsmAnd acceptance remain unverified. A bounded rendering success does not
establish those flows or F-Droid compatibility.

Additional service fixtures cover fortified memory/format operations, scanf,
locale and multibyte translation, time/system queries, bounded scalar helpers,
and nonlocal jumps that reject crossing native ABI frames. Binary128 conversions
and log2l use integer arithmetic with explicit rounding; their normal, hardened
allow-jit-only and sanitizer fixtures pass. Process services have the same three
fixture gates, but unsupported syscalls, including futex, still report ENOSYS.
These service fixtures do not establish unchanged-library or app execution.
