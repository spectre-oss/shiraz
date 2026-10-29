# Arc experiments

[Arc](https://github.com/Anuken/Arc) is the framework used by Mindustry. Its
Android backend calls Java graphics APIs and native libraries; successful
loading alone does not establish working rendering, input or audio.

- [Mindustry](MINDUSTRY.md): GLES3 vertex-array support fixes a rendering-thread
  failure and allows the main menu to render. Campaign interaction is under test.

Require a visible game scene and input-driven gameplay before calling a game
playable. Record software-rendering performance separately from compatibility.
