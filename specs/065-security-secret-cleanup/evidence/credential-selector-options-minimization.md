# Assertion selector options minimization

**Finding:** SEC065-FIDO-023  
**Classification:** CLS-FIDO-001 and CLS-FIDO-002  
**Requirements:** FR-002, FR-003, FR-004, FR-010, NFR-SEC-041, TR-AUTH-031  
**Revision:** Dirty exploratory worktree; source changed 2026-11-16

`SelectCredentialUseCase` accepted `GetAssertionOptions` but never read it. That object contains the RP ID, allow-list, verification preference and client-data hash. Candidate lookup already scopes summaries by RP and applies allow-list and credential-protection filtering in `GetAssertionUseCase` before calling the selector.

Removed the unused options parameter. The selector now receives only the already-filtered candidate summaries and returns one by reference. `GetAssertionUseCase` retains ownership of the entire candidate list and erases every summary in its existing `finally`. Tests and mock verifications now use the candidate-only API. The marker regression verifies fixed diagnostic text and caller cleanup; the selector test no longer constructs an options object or client-data hash.

Full verification passed:

```text
:feature:fido2:testAndroidHostTest
:feature:fido2:compileAndroidDeviceTest
:feature:fido2:ktlintAndroidHostTestSourceSetCheck
:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck
```

The host suite passed 1,286 tests across 169 reports with zero failures, errors or skips. This establishes source/API behavior and host caller coverage, not immutable String lifetime, provider copies, heap absence or device runtime behavior.
