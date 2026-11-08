# Developing Shiraz

Shiraz runs supported Android apps on Linux without an Android system image or
virtual machine. Linux desktops are the current focus; Linux phones are a
longer-term goal. Compatibility is experimental.

Shiraz does not sandbox apps like Android. Use trusted APKs, or a separate,
restricted test environment such as an unprivileged LXC container without
personal files or sensitive host resources. Keep APKs and app data out of Git.

## Find the right component

| Location | Purpose |
| --- | --- |
| `native/`, `include/` | C++ runtime adapters, JNI, windows, graphics, input, and native APIs. |
| `java/android/`, `java/shiraz/` | Android framework compatibility and runtime integration. |
| `src/shiraz/` | Python APK inspection, packaging, and launch tooling. |
| `go-wasm/` | Browser builder: packages an APK with a prepared runtime into an AppImage. |
| `go-server/` | Web runner: executes apps on a Linux host and streams their screen and controls to a browser. |
| `scripts/`, `tests/` | Dependency preparation, build helpers, regression tests, and fixtures. |

Builder and Web share the core runtime. Packaging an APK successfully does not
mean its Android APIs or native libraries are supported.

## Prepare a build environment

Use x86-64 Linux for the current desktop packaging workflow. The repository's
[Nix development shell](../flake.nix) pins build tools, but native compiler and
system-library dependencies must also be installed. Follow the **Install native
build dependencies** step in the [build workflow](../.github/workflows/browser-appimage.yml)
for the reference Ubuntu package list. Graphics builds also require GLES1
development support, including `libGLESv1_CM`.

From the repository root, enter the tool environment:

```sh
nix develop
```

Then prepare fonts, SDL, standalone ART, and the runtime:

```sh
python3 scripts/bootstrap_fonts.py
make deps
make runtime-art
make build
```

The reference Ubuntu workflow selects the system JDK 17 for JNI builds; follow
its environment settings when reproducing that build. `make deps` prepares SDL,
not every dependency. Initial ART preparation downloads and builds substantial
dependencies, so allow disk space and time. Generated files belong in ignored
directories such as `build/`, `.local/`, and `context/`.

## Run or package an app

With the runtime prepared, inspect a trusted APK before running or packaging it:

```sh
make inspect APK=/absolute/path/application.apk
make run APK=/absolute/path/application.apk
make appimage APK=/absolute/path/application.apk
```

AppImage packaging may prepare additional media and runtime components. Use the
build output to locate the resulting artifact; do not commit it or its source APK.

For the other interfaces:

- **Builder:** follow the [browser builder guide](BUILDER.md) to prepare a
  reusable base AppImage and build the Go/WASM frontend. Packaging happens in the
  browser; the generated application runs on Linux.
- **Web:** follow the [web runner guide](WEB.md) for installation or
  source setup. It requires a prepared runtime and FFmpeg with VP8 encoding.
  The default service is local-only; remote access requires authentication,
  transport security, and suitable WebRTC configuration.
- **ARM64 translation:** `make runtime-digitalis` builds the optional Digitalis
  integration. It requires the pinned sources, prepared ART, compiler headers,
  and matching guest libraries. See [attribution and provenance](../LICENSES/DIGITALIS-NOTICE.txt).
  Individual experimental proxies can be built with
  `make digitalis-nativewindow-proxy DIGITALIS_PROXY_OPTIONS="--kind android"`.
  Other kinds include `opensles`, `android_runtime`, `gles2`, and `gles3`. The Android
  proxy fetches checksum-pinned AOSP headers into the ignored build tree.
  Use `--library-dir` and `--android-library-dir` for an existing host runtime;
  GLES2/3 require Shiraz's EGL adapter. Building a proxy does not establish full
  API support: consult the per-app experiment results and test real callbacks.
  `make digitalis-thread-smoke` exercises ARM64 callbacks, TLS, thread identity,
  join, detach, key destructors, and thread naming. `make guest-thread-names-smoke`
  checks live handle resolution, cleanup, and fork handling on the host side.
  The launcher and ARM64 fixture select the configured bridge by absolute path
  to avoid silently loading a different copy with the same filename.
  Configure an existing runtime with
  `DIGITALIS_THREAD_OPTIONS`; `scripts/digitalis/thread_smoke.py --help` lists
  the available paths and compiler options. Run this only in your test sandbox.

## Make and verify a change

1. Find the failing layer: framework API, JNI/native adapter, graphics, packaging,
   or browser controls. Preserve the original APK when investigating compatibility.
2. Make a focused change. Add a regression test for a bug or new behavior when it
   provides meaningful coverage; use existing fixtures where possible.
3. Run the relevant target from the [Makefile](../Makefile), then the repository checks:

   ```sh
   make check
   ```

4. For Go changes, also run `go test ./...` from the affected Go module directory.
5. For app compatibility changes, verify the actual app: rendering, input, the
   affected interaction, and whether it survives repeated use. A passing fixture
   alone does not establish app compatibility. Record remaining limitations.

Run builds, tests, and APKs in the designated test environment when one is used.
For ownership fixes, use sanitizers or allocation-failure tests where practical;
a short successful run is not proof that every runtime path is leak-free.

## Prepare a contribution

Read [AGENTS.md](../AGENTS.md) for repository rules. Keep changes focused, preserve
third-party notices, and explain the problem, resulting behavior, and validation.
Never include APKs, generated binaries, private app names or logs, credentials,
deployment addresses, or machine-specific configuration in a commit.

After review and any required approval, stage explicit source paths and inspect
the staged diff. Run `make check` and the staged-content guard before committing:

```sh
git diff --cached
git diff --cached --check
python3 scripts/commit_guard.py
make commit MSG="fix(runtime): describe the change"
```

Use Conventional Commits. Do not edit `VERSION` manually; the release workflow
updates it. Push only when authorized.

Session logs are private workspace files, outside the Git checkout. In the
`codes/shiraz` workspace layout, use `../../docs/jobs/`. They are not part of the
public development guide or release artifacts.

## Android API probe

`make api-probe-apk` builds `build/android-api-probe/api-probe-unsigned.apk` using
stock SDK classes and pinned D8 desugaring. The probe calls public Android APIs;
it does not use Shiraz framework classes. The APK is unsigned. Sign a local copy
with your Android development signing setup before installing it on Android or
Waydroid. Use the same signed APK on both platforms for a meaningful comparison.

Run the probe with `make api-probe-run`. This prepares the platform runtime before
launching the APK. On macOS it builds the private text, path and image bridges; plain
button backgrounds reduce the controller's initial graphics requirements. Focus highlights and text
editing still require the path bridge.
`make run APK=...` launches an already-prepared runtime and does not build missing
bridges. The graphics dependency bootstrap must finish before starting another
build that needs its lock.

The source catalog is `tests/api-probe/catalog.json`. Every automatic catalog
entry has an independent assertion in `ProbeDispatch.java`; missing implementations
fail. Expected behavior is included in the UI and report. The catalog covers
runtime/concurrency, IPC, resources, storage, SQLite, Canvas/bitmap/text, EGL/GLES,
widgets/input, package/system services, networking, crypto, hardware inventory,
media configuration and JNI/NDK. It is a growing sample of Android behavior, not
an exhaustive API inventory or an Android compatibility certification.

- Tap **Category** to cycle categories and **Previous/Next case** to select a test.
- **Run selected** executes one automatic assertion or opens a manual procedure.
- **Run category** and **Run all** execute automatic cases. Manual cases without
  observations are recorded as skipped, never passed by a batch.
- **Stop queue** stops after the active case; it does not interrupt an API call.
- Manual PASS/FAIL/SKIP records an operator observation. **Input sandbox** provides
  real text editing, scrolling, corner touch targets and an Activity result screen.
  Other manual procedures require a suitable fixture/endpoint/hardware; their
  presence in the catalog does not mean those fixtures are implemented here.

Each automatic case first requests a separate, non-exported service process. Private
atomic result files avoid depending on remote ResultReceiver support. A watchdog
in that process records TIMEOUT after eight seconds and terminates its own process.
If the service is incorrectly run in the controller process, it reports FAIL and
executes no assertion. If no worker report arrives after twelve seconds, the
controller pauses the batch: process launch failure, crash and a blocked runtime
cannot reliably be distinguished by missing output. These cases need runtime logs.
If the controller itself cannot start or save reports, no API result is implied.

When the platform explicitly rejects separate service processes, the controller
uses an in-process diagnostic worker instead. Reports label this `in_process`
with `isolated=false`; the UI also displays the fallback. This preserves API
assertion results while giving up crash containment. A local timeout blocks
further tests until the app process restarts, because Java cannot safely stop
the blocked thread. Native crashes can terminate the controller in this mode.


The on-screen report shows the selected case plus up to twelve recent results,
so long histories do not require rebuilding thousands of text lines on each click.
Selecting any case reveals its saved result; export retains the full history.

Results are saved in the app-private `files/api-probe-report.json` after each step.
**Save/export** also writes to the app's external-files directory and opens the
Android document picker. The report contains expected/observed behavior, duration,
status, suite version and device metadata. Relaunch retains the latest result for
each test and pending work. Cases absent from `results` have not run. PASS applies
only to the stated assertion: sensor/camera/codec enumeration does not prove event
delivery, capture or decoding; a TLS engine does not prove a TLS handshake.

The optional native payload requires an existing local NDK:

```sh
uv run --no-project python scripts/build_api_probe.py --ndk /path/to/android-ndk
```

This builds Android x86_64 and arm64-v8a libraries, including JNI, attached native
threads, direct buffers, native loopers, native windows, bitmap pixels, shared
memory, hardware buffers, dynamic symbol lookup and monotonic clocks. Without a
payload matching the device ABI, native cases are SKIPPED. Source or cross-compilation
alone is not proof that these calls work in Shiraz.

Compare exported reports with:

```sh
uv run --no-project python scripts/compare_api_probe.py android.json shiraz.json
```

The comparator distinguishes regressions, lost coverage, manual observations,
changed test definitions and tests missing from either report. Exit status is
nonzero for regressions or lost coverage. It does not infer success from skips.
Run focused host/helper validation after building the APK:

```sh
make api-probe-check
```

`make api-probe-smoke` additionally launches the real APK in headless portrait
and landscape windows and captures their startup surfaces under `build/`.

Those checks exercise report validation, atomic files, catalog/dispatch consistency
and selected pure-Java assertions on the host JVM. They do not replace running the
APK and observing its controls, crash recovery, deadlines, resizing and persistence
on Android and Shiraz, or testing real OsmAnd workflows.

## Real APK corpus and performance checks

`make compatibility-apks` fetches three pinned, unmodified F-Droid APKs into the
ignored `context/apks/` directory: OsmAnd~, Editor and Simple Calculator. The source
catalog is `config/compatibility-apks.json`. Downloads are size-bounded and checked
against SHA-256, package ID and version code. These content pins do not independently
establish signer trust or app compatibility; generated receipts say so explicitly.
No APKs, app assets or runtime reports belong in commits.

After building the framework, inspect static class availability with:

```sh
uv run --no-project python scripts/audit_apk_api_surface.py context/apks/*.apk
```

The report goes to `build/api-surface-audit.json`. Class presence is only a
conservative inventory: methods, fields, conditional paths, reflection, JNI and
actual app behavior still need testing. A missing annotation is different from a
missing startup class. The report does not produce a compatibility percentage.

`make bitmap-clear-smoke` checks bitmap erase semantics on the JVM and ART, with
separate Java-fill/native-clear component timings. `make ui-performance-smoke`
runs outline pixel references, text layout/input, cluster boundary ordering and
bidi fixtures on both runtimes. Fixture failures remain failures even when they
expose pre-existing platform gaps.

For frame diagnostics, launch with `SHIRAZ_TRACE_FRAMES=1`; the runtime logs setup,
layout, draw and post times. `SHIRAZ_PROFILE_METHODS=1` enables bounded ART sampling
into the app-private `cache/shiraz-methods.trace` (8 MiB, one-millisecond sampling).
Profiling and live snapshot capture add overhead: disable them when measuring
normal interaction. Frame traversal time is not an input-to-photon measurement.
On macOS the current ART port is interpreter-only; these checks do not imply JIT,
GPU or guest JNI-loader support.

`make probe-interaction-benchmark` measures six settled case-selection redraws in
headless software presentation at 360×640/density 160. First prepare the probe with
only the reflection result recorded, select the Runtime category/first case, and
close it. The benchmark refuses a different scene rather than deleting results.
It disables live capture and method profiling; reports and input logs go under
`build/probe-benchmark/current/`. These fixed inputs are a performance benchmark,
not a substitute for the guided real-app acceptance walkthrough. Optional baseline
framework/native paths allow comparing retained build artifacts with the same APK.

### Local APK launcher

Run `make launcher` and open <http://127.0.0.1:8080/>. The app library lists
Android API Probe and the pinned compatibility APKs. Build/download missing entries
with `make api-probe-apk compatibility-apks`, then choose Refresh. Launch starts
one APK at a time using the existing live screen, normal input controls, Stop,
and Live logs. You can still upload another APK. App data persists between runs.
The library is local; it does not claim that listed apps are compatible.
Requires Go, Task (`task`), the built Shiraz runtime, and FFmpeg with libvpx
for the live screen. `cd go-server && go run . -root ..` also starts it directly.

## Networking on macOS

Build the pinned HTTP, crypto and TLS boot modules with `make macos-network`,
then rebuild the framework with `make macos-framework`. The macOS launcher now
requires OkHttp, Bouncy Castle, wolfJSSE and the native trust bridge.
`make network-probe-smoke` executes the networking assertions on ART; it reports
each failure and writes its result under `build/network-probe-runtime/`.
Supply `--apk /path/to/api-probe.apk` to execute the assertions packaged in a
prebuilt API test APK.
The optional `--url https://f-droid.org/repo/entry.json` adds a public HTTPS
fetch, whose success is reported separately from the controlled loopback tests.

The default trust provider imports public system anchors through
Security.framework. Default application trust builds PKIX paths with bundled
Bouncy Castle; wolfSSL supplies TLS. Caller-supplied trust
stores and per-connection TLS factories remain supported. There is no trust-all
fallback. User keychain trust overrides and platform revocation policy are not
yet modeled. The TLS bridge also supports nonblocking SSLEngine handshakes and
HTTPS peer identification, tested with in-memory client/server engines. Local
stream sockets use AF_UNIX; macOS maps abstract names into a private runtime
directory while Linux uses its native abstract namespace. Short macOS checkout
paths retain `.local/sockets`; long paths use a short per-user/per-checkout
private directory under `/tmp` to stay within the Unix socket path limit. The
runtime and smoke launchers select the same directory. Descriptor passing,
Darwin reserved socket services, seqpacket on macOS, and custom TLS SNI/algorithm
constraints still fail explicitly. Passing the tested subset does not establish
support for every Android networking API. IPv6 loopback probes exercise TCP, UDP,
HTTP and verified HTTPS/TLS with compressed and expanded IP identities. IP SANs
are compared as numeric address bytes without DNS. Darwin TLS descriptors use
`SO_NOSIGPIPE`, so disconnected peers report I/O errors without terminating the app.
These tests do not establish an external IPv6 route. Nonblocking TCP and UDP channel
probes exercise selector readiness, direct buffers, connection completion,
half-close/EOF, cancellation, connected datagrams and peer identity. The selector
probe also checks pipe readiness, cross-thread wakeup and closed-state errors.
The public `Network` adapter exposes only the host's current default network.
Its DNS, socket factory and HTTP/HTTPS methods use that route; unknown network
IDs fail without falling back to it. Handles and Parcelable identity round-trip.
Socket binding validates unconnected IP sockets against this default adapter;
it does not create Android netd or independent per-interface/process routing.
Application cleartext decisions are installed before application classes load.
Explicit `usesCleartextTraffic` flags are honored; without one, targets below 28
allow cleartext and newer targets deny it. Both the public Android policy API and
libcore HTTP enforce the decision. `make network-policy-smoke` checks allow/deny
behavior and verifies that denied HTTP never reaches the server. Compiled
`networkSecurityConfig` XML supports base/domain cleartext rules, nested
inheritance, longest domain matching, and `includeSubdomains`. Trust anchors may
use system roots or APK PEM/DER certificates. Resource-valued boolean flags are resolved from the APK.
XML policies use target-SDK defaults and take precedence over the manifest
cleartext flag. Duplicate domains, malformed values, and unsupported directives
fail before replacing the installed policy.

APK anchors, SHA-256/SHA-512 public-key pins, expiration, inheritance,
backup pins, `overridePins`, and debuggable-only anchor overrides are enforced
through PKIX path building in bundled Bouncy Castle before TLS exposes
application data. Paths can use a trusted root while ignoring an irrelevant
cross-signed branch; pins apply only to the validated path. The peer
hostname is scoped around socket/engine handshake callbacks and restored on
failure; missing peer identity fails for a domain policy. Default factories
created before policy installation still consult the installed policy. Explicit
caller-supplied trust managers retain their own behavior. `make network-trust-smoke`
checks these rules with controlled TLS sockets, HTTPS and engines, including
concurrent peer scopes and server observation of accepted requests. Android
chain-extension APIs return validated chains and distinguish domain trust
configurations. No Android user-CA store is configured; `src="user"`, certificate
transparency and newer trust directives still fail explicitly. The cleartext
smoke checks compiled resources and wire-level domain denial. XML cursors
preserve domain text and mixed content. Controlled TLS tests verify clean
`close_notify` EOF. Abrupt shutdown yields EOF or an I/O exception, never
zero progress or an invented NUL byte. TLS reads validate buffer ranges;
empty reads and nonpositive skips do not start a handshake. The stream probes
exercise the socket bridge and pinned provider under TLS 1.2/1.3, including
read-timeout recovery. The pinned API-27 URL handler negotiates TLS 1.2; its
fixed-length HTTPS body probe rejects truncation.

The fixture certificate and key are generated only under `build/`, expire after
two days, and are regenerated when building the probe. Never commit those files
or the resulting APK. Host JVM checks validate the assertions; ART checks and
real-app UI acceptance are separate evidence.
