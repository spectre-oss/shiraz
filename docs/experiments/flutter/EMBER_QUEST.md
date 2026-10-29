# Ember Quest — Flutter / Flame

Status: **scene and menu render; gameplay input under investigation**.

## Artifact

- [Upstream release v1.0.5+1](https://github.com/omnitechphilippines/ember-quest/releases/tag/v1.0.5%2B1), `app-release-v1.0.5+1.apk`.
- Manifest version 1.0.5, code 1; minimum SDK 21, target SDK 35.
- SHA-256: `fa757b132a201370271b6ad8a1f42f8a4c319e2824bec21a695f7c6ac4ddb7f5`.
- ARM32, ARM64 and x86-64 libraries; tested x86-64, unchanged APK, on the separate Linux VM.
- Baseline `91c1cd2` plus the CLI font configuration fix below.

## Missing text and fix

Flutter selects the Impeller OpenGLES backend. Sprites, hearts, platforms and
menu shapes render, but title, button text, instructions and score were absent.
VM file tracing showed unsuccessful Android font-configuration lookups.

The CLI did not supply the native font redirector and family XML already used
by AppImage packaging. Font bootstrap now installs that XML with the verified
fonts, and CLI launch preloads the redirector into ART and supplies its font
directory. No system font paths or APK contents are modified.

A first diagnostic wrapper accidentally supplied two loader preload options;
the later option displaced the font library. Correcting that experiment restored
text. Retesting through the normal CLI with the source fix also restores the
title, Play label, instructions and score. This is a CLI setup fix, not a change
to Flutter's text renderer.

## Interaction and remaining work

Launching directly at 960 × 540 shows the complete player and ground. Clicking
Play dismisses the menu and the animated scene remains. This differs from
launching in portrait and then resizing to landscape, which leaves the player
partly below the window edge. Resize compatibility remains unresolved.

The displayed controls are WASD/arrows and Space. Holding Right for two seconds,
including after a Tab attempt, did not visibly move the player. The opt-in view
dump now includes focus state and focus eligibility. After clicking Play, the
Flutter view is focusable, including in touch mode, but is not focused; none of
the four reported views has focus. This is a lead for investigating keyboard
dispatch, not a verified input fix.

Movement, jumping, collection, loss/retry, persistence, audio and portrait
usability remain unverified. Earlier bounded runs closed normally. These results
were observed with the unchanged APK on the separate VM; rendering a scene does
not establish playable gameplay.
