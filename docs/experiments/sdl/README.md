# SDL Android game experiments

SDL games typically combine an Android activity with native game code, graphics,
audio, controllers, and packaged assets. Shiraz must support the APK's Android
integration as well as its native ABI; a Linux desktop build of the same game
does not establish Android compatibility.

- [SuperTux](SUPERTUX.md): ARM64 Android release; animated startup and touch-driven menu, gameplay investigation.

Acceptance requires a rendered game, actual movement and menu input, resize/back
behavior, and save/restart checks. Native library loading alone is insufficient.
