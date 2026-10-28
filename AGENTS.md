# Shiraz repository rules

Scope: this repository only. Main source lives here. Keep operator documentation,
private project notes, credentials, and machine-specific setup outside this repository
(in the parent workspace docs directory). Do not copy reference project documentation here. The user-authorized exception is
our comparison and compatibility research in docs/others/ (not copied upstream
documentation). Public guides belong in docs/; docs/README.md is their index.
Only explicitly allowlisted public guides may be committed; private research and
operator notes stay excluded. Update .gitignore and scripts/commit_guard.py when
adding a new public guide.
Session job logs belong outside this Git repository, in the workspace-root
docs/jobs/ directory; keep logs free of secrets, APKs, and binaries.

## Session job log
Each agent session records its own work in the workspace-root docs/jobs/<n>.md
(../../docs/jobs/<n>.md when this checkout is at codes/shiraz). Do not recreate
docs/jobs/ inside the Git repository. <n> is the next
unused positive integer (1.md, then 2.md). One file belongs to one session.
The file is append-only: add a new section when the work changes, and do not
edit or delete earlier sections or earlier files. A later session starts the
next number. Do not continue a previous session's file.

Start the file with the session number, the date, the repository VERSION when
known, and one sentence stating the session's intent. Each later section is a
short heading plus what was tried, what changed, the result, evidence paths,
and what is still open. Record only what this session actually did and saw.
Leave unfinished work stated as unfinished.

## Goal
Build a GPL-3.0-only, GTK-free Android translation layer for Linux laptops and an
APK-to-Linux-desktop-app toolchain. Learn from sibling android_translation_layer
and aragh_sagi without modifying them. Use standalone ART, a minimal Android
framework/JNI subset, selective native ABI bridges, SDL2 presentation/input,
EGL/GLES and software composition. No Android system image, container, VM,
kernel Binder requirement, GNOME/KDE requirement, or ARM translation in the core.
OsmAnd is the first compatibility target; arbitrary APK support is incremental.
Capture app-only portrait screenshots of the same OsmAnd APK in Shiraz and Waydroid
once it loads, compare equivalent app states, and continue toward visual parity.
Do not present synthetic fixtures as OsmAnd screenshots or claim parity before testing.

## Application acceptance and order
- Perform an AI-guided walkthrough of the actual running APK using available MCP or
  native interaction tools. Observe the app, choose the next action from the visible
  state, tap/click, enter text, scroll, navigate back, and inspect the resulting state.
  Include portrait/landscape resizing and persistence across restart. Fixed input
  replays and fixture tests supplement this walkthrough; they do not replace it.
- Keep the runtime testable through app-scoped observation and input controls. Capture
  only the app surface, use coordinates from its current dimensions, and deliver actions
  through the normal input path. Record actual tool capabilities and limitations; never
  claim MCP interaction or a successful walkthrough when only scripted replay ran.
- OsmAnd+ must be fully functional, not merely reach a startup screen. Verify real app
  flows by pointer/touch interaction: setup, maps, search, routing/navigation, settings,
  persistence and the features exposed by the chosen APK. Record unsupported behavior;
  do not declare completion while required app functionality is missing.
- Startup must be fast. Measure cold and warm launch to the first interactive frame on
  this host, retain reproducible timing evidence, and compare the same APK with Waydroid.
  An early process start or an unresponsive screenshot does not prove fast startup.
- The app frame must resize interactively between portrait and landscape. Test relayout,
  rendering, resource/configuration updates and pointer coordinates after repeated resizes.
  Scaling or letterboxing a fixed bitmap alone does not satisfy responsive resizing.
- Finish and verify OsmAnd+ before moving to F-Droid app compatibility testing. F-Droid
  is the next real APK acceptance target after OsmAnd+, not a substitute for it.

## Confidentiality and clean commits
- Never commit keys, credentials, tokens, .env files, signing material, private notes,
  APKs, compiled binaries, builds, app data, logs, caches, or cloned third-party trees.
- context/, build/, dist/, data/, .deps/, and .local/ are local-only and ignored.
- Keep OsmAnd source in context/osmand. Never push this clone or any APK.
- Do not print secrets, inspect credential stores, or include them in agent prompts.
- Run make check and the staged-content guard before every commit. Stage explicit
  source paths; inspect staged filenames and diff. No git add -f. No pushes unless asked.
- Hooks are defense in depth, not a proof of absence of secrets. Review changes.
- Any vendored source needs provenance and its original license notices. Do not
  relabel third-party code or OsmAnd assets as solely GPLv3.

## Implementation and verification
- No GTK in runtime, presentation, or framework bridges. SDL demo success is not APK execution.
- Fail explicitly for unsupported APK/DEX features; do not claim a bundle runs until tested.
- Static references are a conservative analysis seed, not proof that reflection,
  dynamically loaded code, JNI or resources are safe to remove. Preserve APKs unchanged.
- Keep app processing bounded and reject malformed archives/DEX rather than extracting blindly.
- Test parsing, input-coordinate transforms, composition, and failure modes.
- Keep development dependencies in-tree; do not change the host desktop or reference projects.
- Use Grok for bounded source-only review/fixes when needed; never send APKs or secrets.

## Build and commits
Use the Makefile. Conventional Commits required. make hooks installs repository-local
hooks. pre-commit checks staged safety; commit-msg validates the final message.
Use make commit MSG="feat(scope): summary" for local source commits. GitHub Actions
updates VERSION from each Conventional Commit pushed to main and commits the bump
as github-actions[bot]. Breaking changes bump major, feat bumps minor, and other
types bump patch. Version must be exposed by the CLI and native app. Commit
source-only milestones locally after checks; do not push unless asked. Track
unfinished runtime work honestly.
