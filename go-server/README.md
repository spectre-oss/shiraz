# Shiraz Go web server

This package serves a phone-shaped APK runner. It runs the APK with Shiraz's
headless SDL display, reads the actual app-surface capture, and shows it in the
browser. Touch, drag, Back, Stop, and text input travel over a WebSocket to
Shiraz's live-input path. Screen images are served as PNG frames.

From the Shiraz repository root, build the runtime first, then start the server:

```sh
cd go-server
go run . -root ..
```

Open <http://127.0.0.1:8080>. Select an APK and choose **Upload and run**.
The phone screen appears after Shiraz starts rendering. Click or drag within
the screen to interact. The **Stop** button ends the current run.
The server accepts one run at a time, limits uploads to 256 MiB, and ends a run
after 30 minutes. Temporary APK uploads are removed when a run ends. Shiraz's
normal per-app data under `data/apps/` persists across runs.

The default listener is localhost. The upload endpoint has no authentication;
do not expose it to other machines without adding authentication and transport
security. The selected APK executes code on the server through Shiraz's native
runtime, so only use APKs you trust. Shiraz supports selected Android apps,
not every APK. Runtime errors appear in the page's log.
