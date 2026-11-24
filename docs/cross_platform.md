# Shiraz Cross-Platform Host Architecture

Shiraz targets **100% native host OS support** across all three major desktop
platforms without virtual machines, containers, or emulators:

| Platform | Architectures | Graphics | Audio | Video Decode | OS Primitives & Isolation | Packaging | Goal Spec |
|---|---|---|---|---|---|---|---|
| **Linux** | `x86_64`, `aarch64` | SDL2 + EGL/GLES + Skia Ganesh + zero-copy GBM/DMA-BUF swapchain, `SurfaceTexture`, `AHardwareBuffer` `EGLImage` (`EGL_LINUX_DMA_BUF_EXT`) & `EGL_ANDROID_native_fence_sync` + live refresh-rate / presenter vsync `AChoreographer` pacing + Vulkan (`libvulkan.so`, `VK_KHR_android_surface` / AHB, real-ICD `PackageManager` probe) | PulseAudio / PipeWire / ALSA (OpenSL ES & AAudio via runtime `libasound`) | FFmpeg / VA-API | `epoll`, `eventfd`, `timerfd`, `memfd_create`, Unix sockets, **default-on** Landlock LSM + `PR_SET_NO_NEW_PRIVS` across CLI/desktop/daemon/AppImage (`--no-sandbox` opt-out, `--no-network`/`--no-internet` seccomp) + Flatpak bubblewrap sandbox | AppImage & Flatpak (`.flatpak`, CLI `--digitalis` & browser WASM Digitalis-capable), `.deb`, desktop `.desktop` | [framework_final_goal.md](framework_final_goal.md) |
| **macOS** | `arm64` (Apple Silicon) | SDL2 Cocoa + ANGLE Metal + Skia Ganesh + zero-copy `IOSurface` swapchain, `SurfaceTexture`, `AHardwareBuffer` `EGLImage` (`EGL_METAL_TEXTURE_ANGLE`) & `EGL_ANDROID_native_fence_sync` (`MTLSharedEvent`) + 120 Hz `CVDisplayLink` vsync pacing + pinned MoltenVK v1.4.2 (live Metal Vulkan 1.4.357 on Apple M2 Pro across host `libvulkan.so`, Darwin guest AAPCS64 thunks & Java `PackageManager` `level=1`) | CoreAudio / AudioToolbox (`AudioQueue`, `AudioConverter`, `AAudio`, `OpenSL ES`) | VideoToolbox (`VTDecompressionSession`) + AudioToolbox Opus | `kqueue`, `EVFILT_USER`/`TIMER`, `shm_open`, `LOCAL_PEERPID`, `posix_spawn` (`POSIX_SPAWN_CLOEXEC_DEFAULT`), SDK `sqlite3`, ART `mach_vm_remap` + `MAP_JIT` + ARM64 fault handler, `WarmArtPool` wired in `HostSession`/`ShirazDaemon` (`2.67×` faster launch), **default-on** Seatbelt SBPL (`--no-sandbox` opt-out) + App Sandbox entitlements | Signed `Shiraz.app` (`scripts/build_macos_app.py --sandbox`), browser `.app` sidecar zip (`go-wasm/macos_app.go`), SwiftUI Launcher | [macos_final_goal.md](macos_final_goal.md) |
| **Windows** | `x86_64` (`AMD64`), `arm64` (`ARM64`) | SDL2 Win32 (`test-presentation.exe` CI-verified) + Per-Monitor V2 DPI & ANGLE-D3D11 wiring + DXGI shared `AHardwareBuffer` pool + `EnumDisplaySettingsW`/`DwmFlush` pacing + `libvulkan.dll` (`VK_KHR_android_surface` swapchain) | WASAPI (`IAudioClient`, `OpenSL ES`, `AAudio`) | Media Foundation (`IMFTransform` decode + `MFEnumDeviceSources` probe & `IMFSourceReader` RGBA frame capture) | `WaitForMultipleObjects`, `CreateWaitableTimerExW`, `CreateFileMappingW`, Named Pipes, Win32 sync fences, Win32 clipboard, Job Object + Restricted Token sandbox, Win32 ART `NativeBridge` v3 (`libshiraz-win32-guest.dll`) + Guest ELF (`SysV ↔ Win64` thunks) & pinned `patches/art-win32-build.patch` + `bootstrap_windows.py` orchestrator | Portable `Shiraz\` + MSIX (`scripts/build_windows_app.py`) | [windows_final_goal.md](windows_final_goal.md) |

## Shared Cross-Platform Layers

1. **Python host utilities (`src/shiraz/`)**
   - `platform_paths.py`: OS-standard `data_home`, `cache_home`, `config_home`,
     `state_home`, `app_data_dir`, `app_cache_dir`, `shared_library_name`, and
     `executable_name` across Linux (XDG), macOS (`~/Library`), and Windows
     (`%LOCALAPPDATA%` / `%APPDATA%`).
   - `architecture.py`: maps Linux `x86_64`/`aarch64`, macOS `arm64`, and
     Windows `AMD64`/`ARM64` to Android ABIs (`x86_64`, `arm64-v8a`), plus
     `host_page_size()` and `elf_needs_copy_load()`.
   - `host_layout.py`: maps build host tags (`linux-x86`, `darwin-x86`,
     `windows-x86`).
   - `sandbox.py` & `sandbox_exec.py`: unified cross-platform `SandboxPolicy`,
     `prepare_sandbox_launch(...)`, and **default-on** OS sandboxing across
     `shiraz run`, `shiraz install-desktop`, `shiraz daemon`, and Linux AppImages
     (`--no-sandbox` opt-out, `--no-network` / `--no-internet`) applying Linux
     Landlock LSM + `PR_SET_NO_NEW_PRIVS` + seccomp-BPF network deny, macOS
     `/usr/bin/sandbox-exec` Seatbelt profiles / App Sandbox entitlements, and
     Windows Win32 Job Object limits (`CreateJobObjectW` / `AssignProcessToJobObject`).
   - `desktop_install.py`: per-APK native launcher installation via
     `shiraz install-desktop` / `shiraz uninstall-desktop` (Linux `.desktop` +
     icon, macOS `~/Applications/<Label>.app`, Windows Start Menu launcher).
2. **Java runtime & embedded WebView (`java/shiraz/runtime/`, `java/android/webkit/`)**
   - `HostOs.java`: reliable runtime detection for `linux()`, `darwin()`, and
     `windows()` under both ART (where `os.name` reports `Linux`) and standard
     host JVMs, plus `sharedLibraryName()` and `nullDevicePath()`.
   - `ExternalLauncher.java`: delegates URLs/mailto/tel to `xdg-open` (Linux),
     `/usr/bin/open` (macOS), or `rundll32.exe url.dll,FileProtocolHandler`
     (Windows).
   - `HostContentPicker.java` & `HostConnectivity.java`: native file picker and
     connectivity hooks across Linux, macOS, and Windows.
   - `ShirazWebDom.java`, `ShirazJsEngine.java`, and `WebView.java`: pure-Java
     HTML5 DOM parser, CSS selector matcher, interactive `contentEditable` /
     `<textarea>` / `<input>` editing bridged to `EditText`, `Selection`/`Range`
     & `document.execCommand`, `MutationObserver`, `Handler`-backed timers
     (`setTimeout`/`requestAnimationFrame`/`queueMicrotask`), `localStorage` /
     `sessionStorage`, step-budgeted JavaScript evaluator, `@JavascriptInterface`
     reflection bridge (blocking unannotated methods), `postWebMessage`,
     `findAllAsync`, and multi-entry back/forward history.
3. **Native C/C++ platform split (`include/shiraz/`, `native/`, `mk/`)**
   - Linux stays the clean baseline; `scripts/darwin_linux_guard.py` guarantees
     shared C/C++ files compile identically on Linux.
   - `native/vulkan_bridge.cpp`: `VK_KHR_android_surface` ANativeWindow
     swapchain bridge, acquire/present loop, and `AHardwareBuffer` memory
     binding.
   - `native/sandbox_linux.cpp` (`include/shiraz/sandbox.hpp`): raw Linux
     Landlock LSM syscalls + `PR_SET_NO_NEW_PRIVS` with explicit `-ENOSYS`
     fallback on non-Linux hosts.
   - macOS host modules live in `native/darwin/` (`mk/darwin.mk`), including
     ProMotion/CoreGraphics display pacing (`display_pacing.mm`), zero-copy
     `IOSurface`→`MTLTexture` readback, `Security.framework` trust anchors, and
     `AVFoundation` speech voices.
   - Windows host modules live in `native/win32/` (`mk/windows.mk`), including
     DXGI shared buffers, Win32 sync fences, `DwmFlush` choreographer,
     `CreateProcessW` spawn, `IFileOpenDialog`/WinHTTP/CryptoAPI/SAPI services,
     and Job Object + Restricted Token sandboxing.
4. **Cross-Platform CI (`.github/workflows/`)**
   - `quality.yml`: Linux lint, C++ static analysis, Go/WASM, Python tests, web checks.
   - `macos.yml`: Apple Silicon (`macos-15`) native libraries, `MAP_JIT`
     verification, `macos-platform-extras-check`, `.app` bundle builder, Python tests.
   - `cross-platform.yml`: 3-OS matrix (`ubuntu-24.04`, `macos-15`,
     `windows-latest`) running cross-platform Python, Java, sandbox, packaging,
     and native host tests on every push and PR.
