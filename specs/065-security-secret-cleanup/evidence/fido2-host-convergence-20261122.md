# FIDO host regression refresh — 2026-11-22

- Worktree identity: dirty exploratory worktree; no frozen candidate declared.
- Procedure: `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- Result: 1,369 tests across 175 Android host XML reports; zero failures, errors or skips. KtLint passed.
- Scope includes current passkey event/snapshot row-payload cleanup regressions, aggregate credential validation, and all configured FIDO host suites.
- Limitation: host-only result; no physical device, heap, provider-copy or candidate-bound claim.
