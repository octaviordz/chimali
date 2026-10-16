# Bug Verification: Restore credential crypto services in optimized builds

- **Slug**: credential-registration (reused from assessment/fix context)
- **Tested**: 2026-10-15, America/Mexico_City
- **Assessment**: ./assessment.md
- **Fix**: ./fix.md
- **Result**: verified — controlled optimized-app provider defect
- **Branch**: bug/056-credential-registration

## Summary

The original provider-availability reproduction fails for all three credential algorithms in
the unchanged optimized baseline APK and passes in the repaired optimized APK on the same ASUS.
Public-key conversion, signing, signature verification, and modified-message rejection all pass.
This verifies the packaging repair. A fresh browser/Bluetooth registration has not been replayed
and is not claimed as verified by these results.

## Checks Performed

| Check | Command / Action | Result | Notes |
|-------|------------------|--------|-------|
| Baseline reproduction | `gradlew.bat :app:connectedShrunkDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.CredentialCryptoProviderTest --console=plain` | Expected failure, exit 1 | 3 tests, 3 failures: missing EC, Ed25519, ML-DSA-65 KeyFactory services. Main app R8 task UP-TO-DATE; corrected test harness used unchanged baseline APK. |
| Reproduction after repair | Same device test command in combined validation run below | Pass | 3 tests, 0 failures/errors/skips on ASUS AI2302 / Android 15 / API 35. |
| Existing FIDO2 regression suite | `:feature:fido2:testAndroidHostTest` | Pass | 458 tests, 0 failures/errors/skips across 90 reports. |
| Formatting | `ktlintCheck` | Pass | Entire configured project; includes new Android test class. |
| Static analysis | `detekt` | Pass | Entire configured project. |
| Release package | `:app:bundleRelease` | Pass | Shrinking remains enabled; 41,231,263 bytes. |
| Release mapping inspection | Inspect `app/build/outputs/mapping/release/mapping.txt` | Pass | Required mapping entry points retain their original names. |
| Diff hygiene | `git diff --check` | Pass | No whitespace errors. |
| Browser registration/assertion | Fresh externally approved ceremony | Not run | No normal app install, wallet reset, biometric approval, or browser ceremony was performed during this fix. |

Combined validation command (Android Studio bundled JDK):

```powershell
.\gradlew.bat :app:connectedShrunkDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.CredentialCryptoProviderTest' :feature:fido2:testAndroidHostTest ktlintCheck detekt :app:bundleRelease --console=plain
```

## Output Excerpts

Baseline:

```text
Missing target KeyFactory.EC
Missing target KeyFactory.Ed25519
Missing target KeyFactory.ML-DSA-65
tests=3 failures=3 errors=0 skipped=0
```

After repair:

```text
CredentialCryptoProviderTest: tests=3 failures=0 errors=0 skipped=0
es256PublicKeyRoundTripAndSignature
ed25519PublicKeyRoundTripAndSignature
mlDsa65PublicKeyRoundTripAndSignature
FIDO2 Android host reports: tests=458 failures=0 errors=0 skipped=0
BUILD SUCCESSFUL in 25m 54s
681 actionable tasks: 202 executed, 479 up-to-date
```

The initial discarded harness attempt used an original provider class name that R8 legitimately
renamed. It failed with ClassNotFoundException and is excluded from root-cause proof. The corrected
harness references the provider facade only and checks its class loader; it does not reference
the mappings or SPI classes. Identical corrected tests are used for the meaningful comparison.

## Artifact Identity and Size

Baseline main source: `8b4d85ad`. The existing local release-signing edit is present in both builds.
The HDK implementation stash was not applied or changed:
`d2aace595ae409b81ff47450cc7b5a2e089ab5a7`.

| Artifact | Before (bytes) | After (bytes) | Increase |
|----------|----------------|---------------|----------|
| Release AAB | 41,015,031 | 41,231,263 | 216,232 bytes (211.16 KiB) |
| Optimized test-target APK | 62,414,123 | 62,659,883 | 245,760 bytes (240 KiB) |

The final AAB is below 50 MB and the increase is below 500 KB. No binary-budget approval is needed.

SHA-256:

| Artifact | Digest |
|----------|--------|
| Baseline release AAB | `0ba29bde821986a39c97271f7be8a2af4bf44f0c873d600d2dda1f81f9839701` |
| Repaired release AAB | `f53e493f86cb840647e5432c933dfeeea98e1bb3ad4bd4ef46e2d679529324cf` |
| Baseline optimized APK | `84cc3eda8b195033dd2cbecd6950826277e6e501de446d8416ff379dfcaad318` |
| Repaired optimized APK | `bb9eae999afc6fd64c200f6cac619e1ff9e72fec15bcebe323e42c0e1cce9733` |

## Residual Risks

- Browser transport, user approval, wallet lifecycle, and complete credential persistence are not
  exercised by this provider-level regression. Retry an approved external registration/assertion
  before declaring every historical browser failure resolved.
- The migrated source remains stashed. Its Ed25519 conversion uses the same bundled provider,
  while its P-256 conversion uses the platform-default provider. Apply this packaging repair when
  resuming that migration; its complete acceptance checks remain separate.
- The tests establish crypto correctness and provider availability, not ceremony latency or
  application startup performance.

## Recommendation

Accept the focused packaging fix: the controlled defect is reproduced and resolved with passing
regression, static, and binary-budget checks. Keep the external browser outcome separate until
a fresh approved registration and assertion have been verified by the relying party.

## Hooks

No before/after bug-test hooks are registered in `.specify/extensions.yml`. No source changes were
made during this final verification step.
