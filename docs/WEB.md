# Shiraz Go web server

## Debian package

The experimental `shiraz-web` amd64 package has been installed and exercised in
clean Debian 12 (Bookworm) and Debian 13 (Trixie) containers. It bundles the
Shiraz native runtime, ART, Python, fonts, and media libraries. Install it with:

```sh
sudo apt install ./shiraz-web_*_amd64.deb
```

The package creates and starts `shiraz-web.service`. Open
<http://127.0.0.1:8080/> on the server. Use `systemctl status shiraz-web` to
check the service. The service uses a dedicated `shiraz` user and listens only
on localhost. APK uploads and saved app data are not included in the package;
app data persists under `/opt/shiraz/data` after removal. Runtime audit state
is stored in the service-owned `/opt/shiraz/data/state`.
The Live logs tab streams ART output over WebRTC and loads the complete saved
stdout/stderr files after a run ends or the page reconnects.

The upload endpoint has no authentication. Remote browser access needs an
authenticated HTTPS entry point and WebRTC network configuration appropriate
for the server; this package currently provides only the localhost service.

`packaging/build_deb.py` assembles the package from a prepared Shiraz runtime
tree and a private Python/library bundle. This is not yet a Debian-native
source build. Its input arguments are `--source-root`, `--runtime-root`,
`--private-python`, and `--output`; the runtime root must have the built ART,
Shiraz libraries, fonts, and Bionic translation configuration. The GitHub
prerelease workflow builds this package and checks installation and portal
startup on Debian 12 and 13. That package declares Debian's `ffmpeg` package
as a dependency; `apt` installs it automatically. The package bundles the
remaining private runtime libraries and includes no sample app APK.

This package serves a phone-shaped APK runner. It runs the APK with Shiraz's
headless SDL display, reads the actual app-surface capture, and shows it in the
browser over WebRTC VP8 video. Touch, drag, Back, Stop, and keyboard input
travel over a WebRTC data channel to Shiraz's live-input path. HTTP is used to
exchange the initial WebRTC offer and answer and to upload the APK.

From the Shiraz repository root, build the runtime first, then start the server:

```sh
cd go-server
go run . -root ..
```

Open <http://127.0.0.1:8080>. Select an APK and choose **Upload and run**.
The phone screen appears after Shiraz starts rendering. Click or drag within
the screen to interact. Click the phone screen to give it keyboard focus.
Key presses and releases then go to the app even when the pointer moves away. Clicking another page
control or switching windows releases held keys. Next field and Enter return
keyboard focus to the phone after sending their command. The **Stop** button
ends the current run. WebRTC video requires FFmpeg with the `libvpx` encoder. Shiraz uses its local
`.local/system-codecs/usr/bin/ffmpeg` when available, or `ffmpeg` from `PATH`;
set `SHIRAZ_FFMPEG` to override the executable path.

The server accepts one run at a time, limits uploads to 256 MiB, and ends a run
after 30 minutes. Temporary APK uploads are removed when a run ends. Shiraz's
normal per-app data under `data/apps/` persists across runs.

### Keyboard-assisted clicking

For apps with keyboard-accessible controls that do not accept virtual touch,
set `SHIRAZ_KEYBOARD_POINTER=1` in the **app runtime's** environment before
launching it. In attached mode, configure the external launcher, not the viewer.
This opt-in mode maps a stationary click to keyboard focus and normal Enter
key events. Clicking a text editor only focuses it, so typing remains explicit.
It does not modify the APK or emulate physical touch hardware.

This is an alternative navigation mode, not native touch compatibility. Dragging,
multitouch, long presses, and controls without keyboard focus are not supported
by this mode. Leave it unset for ordinary touch dispatch. Dialogs and popup
windows retain their existing input handling.

The default listener is localhost. The upload endpoint has no authentication;
do not expose it to other machines without adding authentication and transport
security. The selected APK executes code on the server through Shiraz's native
runtime, so only use APKs you trust. Shiraz supports selected Android apps,
not every APK. Runtime errors appear in the page's log.

## Attach an externally managed session

For compatibility experiments, the viewer can attach to a runtime launched by
an external supervisor:

```sh
shiraz-web -root /path/to/shiraz -attach-session /path/to/session
```

The directory contains `input.txt` (the existing live-input command stream),
`frame.ppm` (the 360 × 640 app-surface capture), and an `active` heartbeat file.
The supervisor creates the input file before launch, sets `SHIRAZ_LIVE_INPUT`
and `SHIRAZ_PRESENT_SNAPSHOT` to these paths, and updates `active` every five
seconds while its runtime child is alive. Remove `active` on exit. Before a new
run, remove it and allow the viewer to observe the inactive state before clearing
input. A heartbeat older than 15 seconds disables input and clears the stream.
Give the web service read access to the frame/heartbeat and append access to input.
Keep APKs, app data, and private runtime logs outside this shared directory.

In this mode uploads are disabled. The supervisor owns launch, isolation,
restart, and timeout; stopping the web service does not stop that runtime.
The page's Stop control still sends the normal close event. Next field (Tab),
Enter, and keyboard input work without relying on touch support. Attaching a
session does not change its compatibility profile or make unsupported APIs work.
