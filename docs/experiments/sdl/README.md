# SDL Android game experiments

SDL games typically combine an Android activity with native game code, graphics,
audio, controllers, and packaged assets. Shiraz must support the APK's Android
integration as well as its native ABI; a Linux desktop build of the same game
does not establish Android compatibility.

- [SuperTux](SUPERTUX.md): ARM64 Android release; animated startup, menus, keyboard/touch movement and jumping, coin collection, resizing, and saved preferences verified; full-level completion remains unverified.

Acceptance requires a rendered game, actual movement and menu input, resize/back
behavior, and save/restart checks. Native library loading alone is insufficient.

SuperTux menu music, a gameplay sound effect, and their separate toggles also
produce PCM through a VM file sink after thread-scheduling and OpenSL
callback-pacing fixes. Physical audio output and hardware latency remain unverified.
