# React Native experiments

React Native apps use Android APIs, a JavaScript engine, and native modules.
SoLoader needs accurate package metadata and native-library paths. Newer apps
may use Hermes and the new native architecture; success with one release does
not establish compatibility with every React Native version.

- [Joplin](JOPLIN.md): notes list and Add menu render; title editing and persistence work; body editing needs a JavaScript-capable WebView.

- [SEN-214 Calculator](SEN_214_CALCULATOR.md): basic and scientific calculations work after OpenType and numeric-input fixes, including permutation-dialog input; resize rendering remains under investigation.

Acceptance requires rendered content, responsive input, and an actual app action,
not merely loading the JavaScript/native libraries.
