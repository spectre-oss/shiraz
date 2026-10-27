# Shiraz Go web server

This package serves an APK upload page and streams Shiraz run logs over a
WebSocket. The APK runs on the Linux server's graphical display through the
existing Shiraz runtime; the browser does not receive or control that display.

From the Shiraz repository root, build the runtime first, then start the server:

```sh
cd go-server
go run . -root ..
```

Open <http://127.0.0.1:8080>. Select an APK and choose **Upload and run**.
The server accepts one run at a time, limits uploads to 256 MiB, and ends a run
after 30 minutes. Temporary APK uploads are removed when a run ends. Shiraz's
normal per-app data under `data/apps/` persists across runs.

The default listener is localhost. The upload endpoint has no authentication;
do not expose it to other machines without adding authentication and transport
security. The selected APK executes code on the server through Shiraz's native
runtime, so only use APKs you trust. Shiraz supports selected Android apps,
not every APK. Runtime errors appear in the page's log.
