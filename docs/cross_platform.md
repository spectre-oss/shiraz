# Shiraz Cross-Platform Host Architecture

Shiraz targets **100% native host OS support** across all three major desktop
platforms without virtual machines, containers, or emulators:

| Platform | Architectures | Graphics | Audio | Video Decode | OS Primitives | Packaging | Goal Spec |
|---|---|---|---|---|---|---|---|
| **Linux** | `x86_64`, `aarch64` | SDL2 + EGL/GLES + Skia Ganesh + GBM/DMA-BUF + Vulkan | PulseAudio / PipeWire / ALSA | FFmpeg / VA-API | `epoll`, `eventfd`, `timerfd`, `memfd_create`, Unix sockets | AppImage, `.deb`, desktop `.desktop` | [framework_final_goal.md](framework_final_goal.md) |
| **macOS** | `arm64` (Apple Silicon) | SDL2 Cocoa + ANGLE Metal + Skia Ganesh + IOSurface + Metal probe | CoreAudio / AudioToolbox (`AudioQueue`, `AudioConverter`) | VideoToolbox (`VTDecompressionSession`) + AudioToolbox Opus | `kqueue`, `EVFILT_USER`/`TIMER`, `shm_open`, `LOCAL_PEERPID`, `posix_spawn` | Signed `Shiraz.app` (`scripts/build_macos_app.py`) | [macos_final_goal.md](macos_final_goal.md) |
| **Windows** | `x86_64` (`AMD64`), `arm64` (`ARM64`) | SDL2 Win32 + ANGLE D3D11 + DXGI shared textures + Vulkan | WASAPI (`IAudioClient`) | Media Foundation (`IMFTransform`) | `WaitForMultipleObjects`, `CreateWaitableTimerExW`, `CreateFileMappingW`, Named Pipes/`AF_UNIX` | Portable `Shiraz\` + MSIX (`scripts/build_windows_app.py`) | [windows_final_goal.md](windows_final_goal.md) |

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
2. **Java runtime host detection (`java/shiraz/runtime/`)**
   - `HostOs.java`: reliable runtime detection for `linux()`, `darwin()`, and
     `windows()` under both ART (where `os.name` reports `Linux`) and standard
     host JVMs, plus `sharedLibraryName()` and `nullDevicePath()`.
   - `ExternalLauncher.java`: delegates URLs/mailto/tel to `xdg-open` (Linux),
     `/usr/bin/open` (macOS), or `rundll32.exe url.dll,FileProtocolHandler`
     (Windows).
3. **Native C/C++ platform split (`include/shiraz/platform.hpp`, `native/{darwin,win32}/`, `mk/{darwin,windows}.mk`)**
   - Linux stays the clean baseline; `scripts/darwin_linux_guard.py` guarantees
     shared C/C++ files compile identically on Linux.
   - macOS host modules live in `native/darwin/` (`mk/darwin.mk`).
   - Windows host modules live in `native/win32/` (`mk/windows.mk`).
4. **Cross-Platform CI (`.github/workflows/`)**
   - `quality.yml`: Linux lint, C++ static analysis, Go/WASM, Python tests, web checks.
   - `macos.yml`: Apple Silicon (`macos-15`) native libraries, `MAP_JIT` verification, `.app` bundle builder, Python tests.
   - `cross-platform.yml`: 3-OS matrix (`ubuntu-24.04`, `macos-15`, `windows-latest`) running cross-platform Python, Java, packaging, and native host tests on every push and PR.
