# App and game experiments

These are observations from running unchanged APKs through Shiraz, not a promise
that an entire framework or app catalog is supported. All experiments in this
series execute on a separate x86-64 Linux test VM, with software graphics.
No APKs, app data, raw logs, or private deployment details belong in this directory.

| Platform | Experiments |
| --- | --- |
| [Flutter](flutter/README.md) | Obtainium |
| [React Native](react-native/README.md) | Joplin |
| [Unity](unity/README.md) | 2048 and Lucas Doggiehood |
| [libGDX](libgdx/README.md) | Unciv |

See [tested applications](../tested.md) for the cross-platform summary.

Each report records the upstream artifact, version/hash, native ABI, runtime
baseline, observed failure, changes tried, and remaining acceptance steps.
**Rendering**, **interactive**, and **fully working** are different outcomes.
A successful process launch alone earns none of those labels. A timeout in a
bounded experiment is not automatically a crash.
