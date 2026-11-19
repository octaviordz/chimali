# Consent statistics collection admission cleanup

Date: 2026-11-18  
Finding: SEC065-FIDO-OWNER-001  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetUserConsentUseCase.kt`

## Ownership correction

`getConsentStatistics` collected mutable `UserConsentRecord` owners into a local list and erased records admitted to that list in its outer `finally`. If admission of the current record failed, that record was not in the cleanup list and its RP/credential text owners escaped the terminal cleanup.

Collection now uses `admitOwnedConsentRecord`. It transfers the current owner only after the destination list accepts it and erases it when admission rejects or throws. The outer cleanup continues to erase all records already collected.

## Verification and limits

`GetUserConsentUseCaseTest.ConsentStatisticsTests` passes five cases, including rejected admission that verifies the current RP ID owner is erased. The full FIDO host suite passes 1,349 tests across 175 reports with zero failures, errors or skips; FIDO KtLint passes.

This host test does not model allocator exhaustion or establish database/provider copies or heap-root absence.
