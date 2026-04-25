# Detailed Changes - 2026-04-25 - CI Pipeline Hardening and FIDO2 Test Fixes

## Overview
This update addresses critical gaps in the local CI pipeline and resolves persistent compilation and static analysis issues in the FIDO2 module. The focus was on ensuring that the CI pipeline accurately reflects the build state and that the codebase adheres to strict quality gates.

## Changes

### CI Pipeline Hardening
- **Enhanced Compilation Detection**: Updated `tools/local-ci.ps1` to replace ineffective lifecycle tasks (e.g., `compileAndroidHostTestSources`) with actual compilation tasks (`compileAndroidHostTest`, `compileAndroidDeviceTest`). This ensures that the CI pipeline correctly identifies and fails on compilation errors in KMP Android source sets.
- **Improved Pipeline Accuracy**: The pipeline now reliably catches "Unresolved reference" and other compilation-level failures that were previously missed by the "Compile All" phase.

### FIDO2 Test Stability
- **MockK Resolution Fix**: Resolved "Unresolved reference 'any'" errors in several FIDO2 test files:
    - `CredentialRepositoryImplTest.kt`
    - `GetUserConsentUseCaseTest.kt`
    - `RegisterCredentialUseCaseTest.kt`
- **Scoping Fix**: Switched to explicit MockK imports and leveraged `MockKMatcherScope` resolution within `coEvery`/`coVerify` blocks. This avoids top-level resolution conflicts between `io.mockk.any` and `kotlin.Any` in the Kotlin 2.x/MockK environment.
- **Logic Restoration**: Restored previously accidentally removed test variables (`message1`, `scrubbed1`) and long string literals in `PrivacyLogScrubberTest.kt` to ensure test coverage for negative redaction cases.

### Detekt Quality Fixes
- **MaxLineLength Compliance**: Resolved `MaxLineLength` violations in `PrivacyLogScrubberTest.kt` by splitting a 12-word mnemonic string literal across multiple lines using string concatenation.
- **Unused Code Resolution**: Resolved `UnusedPrivateProperty` in `PrivacyLogScrubberTest.kt` by adding an assertion for `scrubbed1`. This verifies that the scrubber correctly ignores messages that do not meet the redaction keyword/delimiter requirements.

## Impact
- **CI Reliability**: Developers can now trust `local-ci.ps1` to catch compilation errors before pushing code.
- **Code Quality**: All FIDO2 tests now pass both compilation and Detekt static analysis gates.
- **Test Coverage**: Restored and verified the integrity of the `PrivacyLogScrubber` test suite.

## Detailed Diff Summary
- **Files Modified**: 5
- **Modified Files**:
    - [/tools/local-ci.ps1](/tools/local-ci.ps1)
    - [/feature/fido2/src/test/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImplTest.kt](/feature/fido2/src/test/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImplTest.kt)
    - [/feature/fido2/src/test/kotlin/com/chimali/fido2/domain/usecase/GetUserConsentUseCaseTest.kt](/feature/fido2/src/test/kotlin/com/chimali/fido2/domain/usecase/GetUserConsentUseCaseTest.kt)
    - [/feature/fido2/src/test/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCaseTest.kt](/feature/fido2/src/test/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCaseTest.kt)
    - [/feature/fido2/src/test/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt](/feature/fido2/src/test/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt)
