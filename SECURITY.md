# Shiraz security model

Shiraz currently executes APK bytecode and supported native code as an ordinary
process with the launching Linux user's privileges. **It is not an Android app
sandbox.** Per-app data directories and session locks prevent accidental mixing;
they do not isolate an untrusted APK from the user's files or network. Use APKs
you trust. The Bubblewrap namespace in AppImage acceptance tests is a test harness,
not an isolation promise for delivered applications.

Android permission APIs report the capabilities the runtime actually provides.
Unavailable permissions are denied. Permission requests return results on the
main event loop and preserve ownership across Activity recreation. A callback
must agree with `checkSelfPermission`; adding a missing API must not automatically
grant access. The optional test-location environment setting grants only declared
location permissions for an explicitly supplied mock fix. It does not enable a
real GPS provider. No host permission prompt or grant database is claimed yet.
These framework checks do not prevent direct file/socket access by APK code;
host-enforced isolation remains separate unfinished work.

Build tools and public font/trust-store downloads use pinned hashes. Preserve
original APK bytes and record their digest. Validate archive paths, payload sizes,
JNI inputs, and output destinations before processing them. Never run `ldd` or
other executing dependency inspection against untrusted APK libraries. Do not
weaken TLS verification to resolve application compatibility errors.

Reference projects are research inputs, not trusted build instructions. Keep their
clones outside Git. Reimplement suitable design ideas in Shiraz; import source
only after checking that file's provenance, license, dependencies, and required
notices against this project's licensing policy. A repository-level license is
not proof that every bundled component has the same license. Record adaptations
and retain original notices. Do not silently change Shiraz's GPL-3.0-only policy.

Do not commit credentials, APKs, compiled output, user data, diagnostic logs or
screenshots. Logs can contain locations and application data: inspect them locally
and share only the minimum required evidence with explicit authorization. Local
commit hooks help detect mistakes; they are not a confidentiality guarantee.
