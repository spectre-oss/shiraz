# Droidloom comparison and game compatibility targets

## Objective and acceptance

Learn from Droidloom and make its reported games work in Shiraz. This work is
unfinished. A project comparison row or translator fixture is not gameplay
acceptance. Keep the original APKs unchanged; verify visible gameplay, input,
portrait/landscape behavior where supported, and persistence across restart.
Record app versions, architectures, runtime configuration and observed limits.
Do not turn another project's results into Shiraz compatibility claims.

## Reviewed sources

Reviewed 2026-11-08:

- [Droidloom](https://github.com/denialwm/droidloom/tree/ffccef6a7f6db4d2f1350e90668df2391b8f9b78),
  revision `ffccef6a7f6db4d2f1350e90668df2391b8f9b78`.
- Its [known issues](https://github.com/denialwm/droidloom/blob/ffccef6a7f6db4d2f1350e90668df2391b8f9b78/docs/KNOWN_ISSUES.md)
  qualify the broader README's app-launch reports.
- Its [translator lock](https://github.com/denialwm/droidloom/blob/ffccef6a7f6db4d2f1350e90668df2391b8f9b78/android/manifest/native-bridge-lock.json)
  pins [Teto](https://github.com/denialwm/teto/tree/917021cf3ecd077eb1581146a79d2265011755b8)
  revision `917021cf3ecd077eb1581146a79d2265011755b8`, Apache-2.0.

Droidloom runs Android userspace (including ART, Binder, Zygote and system_server)
in Linux namespaces, with separate Wayland app windows. It requires Binder and
compatible Wayland protocols. Its full framework differs substantially from
Shiraz's standalone ART and selective Java/native API bridges. Teto derives from
Digitalis/AOSP Berberis; the shared lineage does not make their integrations
interchangeable. Preserve upstream license and change notices for any adopted
source. No upstream source has been vendored by this comparison change.

## All reported game targets

| Game | Upstream evidence and scope | Shiraz status |
| --- | --- | --- |
| Arknights | README reports running; pictured on ARM64 hardware. | Untested: no local game package identified. |
| Arknights: Endfield | README reports running; pictured on ARM64 hardware. | Untested: no local game package identified. |
| Brawl Stars | ARM64 build 69.252: upstream documents breakpoint/ptrace and guest JNI-table fixes, activity launch and user confirmation; not exhaustive gameplay validation. | Untested: no local game package identified. |
| Clash of Clans | Version 18.600.5: known-issues notes document a rendered age-entry screen with local identity/readlink changes, explicitly not verified gameplay. | Untested: no local game package identified. |
| Neverness to Everness | README reports ARM64 build on x86_64; translator has a regression for its large address-space reservation. | Untested: no local game package identified. |

These are research targets, not entries in Shiraz's tested-app catalog. Actual
package files, including required splits/assets, are needed before launch tests.
Server-dependent flows also require a working network and may require a user
account; an offline startup result cannot establish those flows work.

## Implementation work to evaluate

1. **Floating-point rounding:** Teto exercises guest FPCR-to-MXCSR mapping and
   narrowing conversions. Compare its implementation to Shiraz's pinned bridge;
   reproduce failures with ARM64 numerical fixtures before selecting changes.
2. **Memory reservations:** Teto's opt-in `android-mmap-noreserve` applies to
   private anonymous guest mappings. Its tests preserve accounting for shared
   and file mappings and exercise a 76 GiB virtual reservation for Neverness to
   Everness. Avoid changing global host overcommit policy.
3. **Guest JNI table isolation:** upstream tests verify a guest hook does not
   overwrite the host FindClass callback and that table sharing follows host
   table identity. Shiraz must validate its standalone ART integration before
   adopting this behavior.
4. **Guest debugging state:** Brawl Stars exposes breakpoint metadata and ARM64
   register-set translation gaps. Upstream support is limited to verified guest
   breakpoint states; arbitrary asynchronous host stops are not equivalent.
5. **Framework and graphics:** full Android services and DMA-BUF Wayland
   composition are separate from CPU translation. Diagnose actual Shiraz API
   gaps with the supplied game, rather than assuming a translator upgrade alone
   supplies system services or GPU compatibility.

Shiraz currently pins its own Digitalis bridge. This investigation has not
replaced that bridge, implemented the items above, or verified these games.
