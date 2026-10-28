---
name: Bug report
about: Report a reproducible problem in the runtime, Builder, Web, or build tools
title: ''
---

<!-- Search existing issues first. Share only sanitized evidence: no APK uploads,
credentials, personal data, private app identifiers, or deployment addresses.
Describe sensitive app behavior generically; a public source link is optional. -->

## Problem

<!-- One sentence describing what fails and its impact. -->

## Environment

- Shiraz version and commit:
- Component: runtime / Builder / Web / build or packaging
- Linux distribution and version:
- Host CPU architecture; APK native ABI (if relevant):
- Graphics driver and display backend (if relevant):
- Browser and version (Builder/Web only):
- Installation/build method and optional components (e.g. Digitalis):
- Test isolation: container / VM / other

## Reproduce

<!-- Include exact sanitized commands or UI actions, preconditions, and whether
app data was fresh or reused. For build failures, include the failing target. -->

1.
2.
3.

## Expected and actual behavior

**Expected:**

**Actual:**

- Frequency: always / intermittent (how often?)
- Last known working version, if any:
- Optional public app source/release link and version, or a minimal source fixture:

## Evidence

<!-- Paste the first relevant error and stack trace, not entire private logs.
Include exit status and steps leading to a crash. For graphics/input issues,
attach a sanitized app-only screenshot and display size. A screenshot of a
fixture is not evidence that the original app works. -->

```text
Sanitized error excerpt
```

## Checks

- [ ] I searched for an existing report.
- [ ] I removed secrets, personal data, private app identifiers, and host details from the report and attachments.
- [ ] I described what I actually observed and identified anything untested.
