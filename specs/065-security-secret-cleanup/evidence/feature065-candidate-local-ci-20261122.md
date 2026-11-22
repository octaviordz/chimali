# Frozen-candidate local CI — 2026-11-22

| TRD key | Record |
|---|---|
| `requirement` | FR-008/009/013; T041; SC-004 |
| `scope` | Repository local CI build, static analysis, lint and test gates against the frozen product source. |
| `revision` | Frozen candidate commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`. Product/build sources remained at the freeze tree; follow-up changes were confined to evidence and the evidence-validator script. |
| `named_procedure` | `pwsh -NoProfile -File tools/local-ci.ps1 -NoFix -NoConfigurationCache` |
| `environment` | Windows host; Gradle 9.6.0/JDK 25. No physical test device or coverage mode was used. |
| `expected_observed_result` | Exit 0. KtLint, Detekt, release lint, comprehensive compilation, unit tests and KMP Android host tests all reported PASS. Total 16.24 seconds; detailed captured transcript retained. Full FIDO host testing was separately executed in `fido2-host-candidate-20261122.md`. |
| `date` | 2026-11-22 |
| `limitations` | `-RunCoverage` was not selected; no connected-device coverage workload or hosted integration run occurred. Local CI itself states hosted integration remains Unverified. UP-TO-DATE Gradle tasks reuse outputs for inputs matching the frozen source; the FIDO host suite was separately rerun in this session. |
| `status` | Passed for the local CI procedure. |
| `artifact_link` | `evidence/feature065-candidate-local-ci-20261122.log` SHA-256 `CD56181DCF9EA4F5893DDA91664E3C901AA86F6A5C65FAC9B32358EF4226B16F`. |
