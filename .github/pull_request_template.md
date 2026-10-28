## Problem and result

<!-- Explain the concrete trigger and the behavior before and after this change.
Link a related issue if one exists. Keep this proportionate to the change. -->

Related issue:

## Changes

<!-- Describe the affected layer and why this approach is appropriate.
Mention new dependencies, upstream sources/licenses, and material tradeoffs. -->

## Validation

<!-- Give exact commands and results, test environment, and any skipped checks
with reasons. For compatibility fixes, include actual app interactions verified
and whether the original APK was unchanged. Sanitized evidence only. -->

- Focused regression or manual check:
- `make check`:
- Go module tests, if applicable:
- Actual app flow, if applicable:

## Limitations

<!-- Remaining failures, unsupported behavior, or risks. Do not claim general
compatibility from one fixture or one successful app launch. -->

## Review checklist

- [ ] I reviewed the complete diff, including any AI-generated changes.
- [ ] Validation above accurately describes what ran and what did not.
- [ ] No APKs, generated artifacts, secrets, private logs, app identifiers, or deployment details are included.
- [ ] Relevant regression coverage and documentation are included, or I explained why they are unnecessary.
- [ ] Third-party attribution is preserved and VERSION is unchanged.

<!-- Optional: describe how AI tools helped and how you independently verified
 their output. Use a Conventional Commit-style PR title. -->
