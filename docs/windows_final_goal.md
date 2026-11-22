# Shiraz on Windows: final goal (native path)

This document defines what "Shiraz runs natively on Windows" means and lists
every host API that must be implemented natively to achieve 100% parity with
Linux and macOS. Status boxes are updated as work lands; `[x]` means implemented
and verified on Windows (or via cross-platform host unit tests where noted), and
`[~]` means native implementation landed and awaiting live Windows hardware/CI
verification.

## Decision and scope

- **Native only.** Shiraz runs as an ordinary Win32 user-mode process. No WSL2,
  Hyper-V guest, Android Emulator, container, or remote host.
- **Direct3D 11 / ANGLE-D3D11 is the GPU API.** App GLES runs on ANGLE's D3D11
  backend (`EGL_PLATFORM_ANGLE_TYPE_D3D11_ANGLE`), Shiraz's own canvas and
  composition run on Skia Ganesh (GL over ANGLE-D3D11 or native Ganesh-D3D11),
  and shared surfaces are `ID3D11Texture2D` / DXGI shared NT handles
  (`DXGI_SHARED_RESOURCE_READ | DXGI_SHARED_RESOURCE_WRITE`).
- **x86_64 (`AMD64`) and ARM64 (`ARM64`).** `x86_64` APK native code runs on
  x86_64 Windows without ISA translation; `arm64-v8a` APK native code runs on
  Windows on ARM64 without ISA translation. Cross-ISA APKs fail explicitly.
- **Baseline OS:** Windows 10 21H2 / Windows 11 (x64 & ARM64).
- **Zero Linux/macOS regressions.** All Win32 implementations live under
  `native/win32/`, `include/shiraz/win32/`, and `mk/windows.mk`, or strictly
  inside `#if defined(_WIN32)` guards verified not to alter Linux or macOS paths.

## Host facts that shape the design

| Fact | Consequence |
|---|---|
| Virtual memory page size is 4 KiB (`dwPageSize`), allocation granularity is 64 KiB (`dwAllocationGranularity`). | 4 KiB APK ELF `PT_LOAD` segments match hardware page size; `VirtualAlloc` reservations align to 64 KiB. |
| Executable memory uses `VirtualAlloc(..., PAGE_READWRITE)` → `VirtualProtect(..., PAGE_EXECUTE_READ)` + `FlushInstructionCache`. | Clean W^X JIT and ELF loader without `MAP_JIT` or `memfd_create`. |
| Windows x64 ABI (`RCX/RDX/R8/R9`, 32-byte shadow space, callee-saved `RDI/RSI/XMM6-XMM15`) differs from Linux SysV x86_64 ABI (`RDI/RSI/RDX/RCX/R8/R9`). | Every bionic-to-Win32 crossing on x86_64 passes through an ABI thunk; ARM64 Windows (`AAPCS64` with `x18` reserved for TEB) requires `x18` preservation and TLS (`tpidr_el0`) rewriting. |
| Windows uses `HANDLE`s rather than POSIX file descriptors for events, pipes, shared memory, and timers. | `ALooper`, `ASharedMemory`, `ASyncFence`, and `IPC` use a Shiraz fd-to-`HANDLE` table (`WaitForMultipleObjects` + `WSAPoll` bridge). |
| `AF_UNIX` sockets are supported natively since Windows 10 1803 (`afunix.h`), while peer PID lookup uses `GetNamedPipeClientProcessId` on named pipes. | IPC supports both `AF_UNIX` and named-pipe transports with strict ACLs (`DACL` restricted to current user SID). |
| `NUL` is the null device, `%LOCALAPPDATA%\Shiraz` holds persistent data, and DLLs use `.dll` instead of `.so`. | Cross-platform Java (`HostOs.java`) and Python (`platform_paths.py`) layers normalize paths, library names, and subprocess openers. |

## Target architecture

```
Shiraz\ (portable directory or MSIX package)
└─ Shiraz.exe: embedding launcher, JNI_CreateJavaVM
   ├─ ART / JVM host runtime + boot DEX + shiraz-framework.jar
   ├─ Shiraz host DLLs (Win32): platform layer over Kernel32/User32/DXGI/WASAPI/MF/DWrite
   ├─ Guest ELF loader (ART NativeBridge hook)
   │    APK lib/{x86_64,arm64-v8a}/*.so → VirtualAlloc load, ABI thunks, bind imports
   │    bionic libc/libm/libdl personality → UCRT/Win32 thunks
   │    libEGL/libGLESv*/libandroid/libOpenSLES/libvulkan → Shiraz host DLLs
   └─ Presentation: SDL2 Win32 HWND → ANGLE D3D11 / Direct3D 11
        Skia Ganesh canvas ← DXGI shared ID3D11Texture2D from app GLES
        DwmFlush / IDXGIOutput::WaitForVBlank → Choreographer
```

## APIs to implement natively

Difficulty: **L** low, **M** medium, **H** high, **X** research-level.

### 1. Runtime hosting & ABI foundation

| Status | Item | Repo / source | Windows implementation | Diff |
|---|---|---|---|---|
| [x] | Cross-platform OS & path abstraction | `src/shiraz/platform_paths.py`, `java/shiraz/runtime/HostOs.java` | `Windows`/`MSYS`/`MinGW` normalization, `%LOCALAPPDATA%\Shiraz`, `%APPDATA%\Shiraz`, `.dll`/`.exe` naming, `NUL` null device | L |
| [x] | Host ABI & page-size detection | `src/shiraz/architecture.py`, `src/shiraz/host_layout.py` | `AMD64`/`x64` → `x86_64`, `ARM64` → `arm64-v8a`, 4 KiB page policy, `windows-x86` host tag | L |
| [x] | Portable C++ platform macros | `include/shiraz/platform.hpp` | `SHIRAZ_OS_WINDOWS`, `SHIRAZ_EXPORT` (`__declspec(dllexport)`), calling conventions | L |
| [ ] | ART Win32/MinGW-clang build | `context/art_standalone`, `scripts/bootstrap_windows.py` | Build ART (`libart.dll`, `libdexfile.dll`, `libcore`) for Windows x64/ARM64; `VirtualAlloc` + `VectoredExceptionHandler` | X |
| [ ] | Guest ELF loader & SysV↔Win64 ABI thunks | `native/win32/guest/` | Load ELF `PT_LOAD` segments via `VirtualAlloc`, map `RDI..R9` ↔ `RCX..R9` on x64, preserve `x18`/TLS on ARM64 | X |

### 2. Graphics: presentation, canvas, EGL/GLES, buffers

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | GPU capability probe & power guard | `include/shiraz/win32/d3d11_probe.hpp`, `native/win32/d3d11_probe.cpp` | DXGI `IDXGIFactory1::EnumAdapters1` (`SHIRAZ_GPU device=... vendor=...`), `SetThreadExecutionState(ES_CONTINUOUS \| ES_DISPLAY_REQUIRED \| ES_SYSTEM_REQUIRED)` | L |
| [ ] | Window & ANGLE-D3D11 layer | `native/presentation.cpp`, `mk/windows.mk` | SDL2 Win32 `HWND` with ANGLE D3D11 `opengles2` renderer and Per-Monitor V2 DPI scaling | M |
| [ ] | Live resize | `native/presentation.cpp` | `SDL_AddEventWatch` repaint during Win32 `WM_ENTERSIZEMOVE` / `WM_SIZING` modal drag loop | M |
| [ ] | AHardwareBuffer / DXGI shared surface | `native/hardware_buffer.cpp`, `native/win32/` | `ID3D11Texture2D` + `IDXGIResource1::CreateSharedHandle`, CPU staging lock/unlock | H |
| [ ] | Sync fences | `native/sync_fence.cpp`, `native/win32/` | `ID3D11Fence` / Win32 `HANDLE` event signaled on GPU fence completion | H |
| [ ] | Frame pacing / Choreographer vsync | `native/choreographer.cpp` | `DwmFlush()` / `IDXGIOutput::WaitForVBlank()` with `CreateWaitableTimerExW` high-res fallback | M |
| [ ] | Vulkan | `native/ndk_vulkan.cpp` | Native Windows `vulkan-1.dll` loader (`VK_KHR_android_surface` → `VK_KHR_win32_surface`) | M |

### 3. Input and windowing

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | Touch & Precision Touchpad helpers | `include/shiraz/win32/d3d11_probe.hpp` | Separate direct touchscreens from Precision Touchpad pinch/scroll deltas; Per-Monitor DPI scaling (`win32_dpi_density`) | L |
| [ ] | Keyboard & IME | `include/shiraz/sdl_keyboard.hpp`, `native/presentation.cpp` | SDL2 Win32 scancode → Android keycode mapping; IMM32/TSF preedit via `SDL_TEXTEDITING` | M |
| [ ] | Clipboard | framework | Win32 `OpenClipboard`/`GetClipboardData` via SDL2 clipboard | L |

### 4. OS primitives

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | ALooper | `native/win32/looper.cpp` | `CreateEventW` wake, `CreateWaitableTimerExW` (`CREATE_WAITABLE_TIMER_HIGH_RESOLUTION`), `WaitForMultipleObjects` + `WSAPoll` fd bridge | M |
| [~] | ASharedMemory | `native/win32/shared_memory.cpp` | `CreateFileMappingW(INVALID_HANDLE_VALUE, ...)` + `MapViewOfFile` + handle table + read-only `DuplicateHandle` enforcement | M |
| [~] | Process & clock info | `native/win32/host_process.cpp` | `QueryPerformanceCounter` (monotonic), `GetTickCount64` (boottime), `GetCurrentProcessId`/`GetCurrentThreadId`, `GlobalMemoryStatusEx` | L |
| [~] | IPC channel | `native/win32/ipc_channel.cpp` | Named-pipe / `AF_UNIX` (`afunix.h`) transport with `GetNamedPipeClientProcessId` peer credential validation | M |
| [ ] | Process spawn | `native/ipc_spawn.cpp`, `ServiceProcess.java` | `CreateProcessW` + `PROC_THREAD_ATTRIBUTE_HANDLE_LIST` + Win32 Job Object (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`) | M |

### 5. Media

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | Audio output (WASAPI) | `native/win32/audio_output.cpp` | WASAPI `IMMDeviceEnumerator` → `IAudioClient` (shared mode PCM16/float32) → `IAudioRenderClient` with automatic software/null fallback when no audio endpoint exists | M |
| [~] | Video decode (Media Foundation) | `native/win32/video_codec.cpp` | `MFStartup` + `IMFTransform` H.264/HEVC decoder outputting NV12/BGRA frames | H |
| [ ] | OpenSL ES & AAudio | `native/opensles.cpp`, `native/ndk_aaudio.cpp` | WASAPI event-driven callback bridge | M |
| [ ] | Camera & Microphone | `native/capture.cpp` | Media Foundation `IMFCaptureEngine` + WASAPI loopback/capture client | M |
| [ ] | Text to speech | `native/speech.cpp` | SAPI5 `ISpVoice` / Windows OneCore TTS | L |

### 6. Platform services

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | Notifications | `native/win32/notifications.cpp` | `Shell_NotifyIconW` (`NIF_INFO`) & WinRT toast bridge with active notification registry, urgency flags, sound suppression, and headless session safety | M |
| [~] | Location | `native/win32/location.cpp` | Windows Location API (`ILocation` / `ILatLongReport` COM interface) with safe headless/permission-denied fallback | M |
| [~] | System fonts | `native/win32/system_fonts.cpp` | DirectWrite `IDWriteFactory::GetSystemFontCollection` + `IDWriteLocalFontFileLoader::GetFilePathFromKey` enumerating `%WINDIR%\Fonts` | M |
| [x] | Open URL / external apps | `java/shiraz/runtime/ExternalLauncher.java`, `HostOs.java` | `rundll32.exe url.dll,FileProtocolHandler <url>` on Windows (`HostOs.windows()`), `NUL` redirect, drops `LD_*`/`DYLD_*` | L |
| [ ] | Connectivity | `HostConnectivity.java` | `GetAdaptersAddresses` + `INetworkListManager` | L |
| [ ] | Document picker | `HostContentPicker.java` | Common Item Dialog (`IFileOpenDialog` / `IFileSaveDialog`) | L |
| [x] | App storage | `src/shiraz/platform_paths.py`, `tests/test_platform_paths.py` | `%LOCALAPPDATA%\Shiraz\<package>`, `%LOCALAPPDATA%\Shiraz\Cache\<package>`, `%APPDATA%\Shiraz` | L |
| [ ] | Keystore | `HostKeyStoreProvider.java` | Software keystore + Windows CNG / DPAPI (`CryptProtectData`) optional backend | M |

### 7. Build, packaging, CI

| Status | Item | Repo files | Windows implementation | Diff |
|---|---|---|---|---|
| [~] | Makefile Windows rules | `mk/windows.mk`, `Makefile` | MinGW-w64 / Clang-UCRT64 build rules (`-shared`, `.dll`, `.exe`, `-lole32 -ldxgi -ldwrite -lmfplat -lshell32 -lws2_32`) + `make windows-check` | M |
| [x] | Portable Windows & MSIX bundler | `scripts/build_windows_app.py`, `tests/test_build_windows_app.py` | Portable `Shiraz/` directory layout, BMP/PNG-to-`.ico` generator, DLL dependency closure, `shiraz-manifest.json`, and MSIX `AppxManifest.xml` generation | M |
| [x] | Cross-platform CI matrix | `.github/workflows/cross-platform.yml` | GitHub Actions matrix running native builds & tests on `ubuntu-24.04`, `macos-15`, and `windows-latest` | M |
