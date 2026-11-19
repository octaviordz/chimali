# Onboarding error sanitization owner map (2026-11-15)

## Requirement and scope

`NFR-SEC-030` / `FR-009`; `SEC065-ERR-008` from the DataStore `IOException` catch through `OnboardingUiState`, `OnboardingNavGraph`, `FeatureSelectionScreen`, and `OnboardingViewModelTest`.

## Source review

`finishOnboarding` catches `IOException` and publishes the fixed message “Unable to save onboarding preferences.” It does not retain or expose the throwable message. The UI state carries the immutable message to the feature-selection screen, which renders it without transforming it. The regression test's `FailingDataStore` throws an `IOException` containing a synthetic marker and private-path string, then verifies the marker is absent and saving state resets. The test suite also covers selection persistence, route choice, invalid selection and error clearing. The onboarding manifest, DI module, fixed route constants and intro screen were inspected and recorded with file-specific no-relevant-usage rationales.

## Verification

Ran `:feature:onboarding:testDebugUnitTest` on the Windows JVM host. All 7 `OnboardingViewModelTest` tests pass.

## Limitations

This verifies only the onboarding `IOException` path and host-side state. Other exception owners and configured diagnostic destinations remain open under T005/T006/T035; Compose runtime retention is not measured.
