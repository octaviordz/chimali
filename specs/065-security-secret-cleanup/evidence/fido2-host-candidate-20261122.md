# Frozen-candidate FIDO Android host regression — 2026-11-22

| TRD key | Record |
|---|---|
| `requirement` | FR-003/004/008/009/010; T110/T111; SC-002/004 |
| `scope` | Full configured FIDO Android host-test source set after the frozen implementation changes, including passkey event/snapshot row-payload cleanup and aggregate credential-validation regressions. |
| `revision` | Frozen product commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`. FIDO production and host-test source files matched the frozen commit during execution; only evidence/validator documentation had subsequent worktree changes. |
| `named_procedure` | `.\gradlew.bat :feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` |
| `environment` | Windows host, Gradle 9.6.0, JDK 25; Android JVM host test runtime; no physical device involved. |
| `expected_observed_result` | Build succeeded. 1,369 tests across 175 XML reports; 0 failures, 0 errors, 0 skipped. `:feature:fido2:ktlintCheck` succeeded (UP-TO-DATE). XML summary, detailed report and per-report SHA-256 manifest are retained and pinned in the evidence index. |
| `date` | 2026-11-22 |
| `limitations` | Host regression only; it does not establish Android runtime, physical-device, heap, provider/native or release-equivalent behavior. The full historical evidence-index audit still has expired/missing artifacts in older rows. |
| `status` | Passed for the frozen-candidate FIDO host regression procedure. |
| `artifact_link` | `feature/fido2/build/reports/tests/testAndroidHostTest/index.html` SHA-256 `FB878A1307D9F2A8A16DE3EFE6950DC30E07725EB3E7311630258724D300E499`; `evidence/fido2-host-candidate-20261122.sha256` SHA-256 `5E1E760A067D1763131FCC6625D7F5347E75539CC9FD5FD6B6915057009821CF`; focused candidate `PasskeyAggregateServiceImplTest.xml` SHA-256 `FE485BF346DFD4DE1E1EDE03A742A643F5812CB3B90D7C74D798EDD96B4EE254`. |
