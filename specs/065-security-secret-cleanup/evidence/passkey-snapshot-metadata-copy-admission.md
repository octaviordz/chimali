# Passkey snapshot metadata copy admission cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-016

## Source correction

Snapshot hydration copied the RP ID, user ID, account name, display name and credential ID into a new `PasskeyState` through sequential unguarded copies. If a later copy failed, earlier mutable arrays had no cleanup owner. `PasskeyState.copyWithOwnedMetadata` now keeps every new array staged until the copied state is constructed and erases all staged arrays on failure.

## Verification and limitations

The new parameterized regression injects failure at each of the five copy positions. It verifies all earlier staged arrays are zeroed and the source state's arrays remain unchanged. The full FIDO host suite passes 1,337 tests across 172 reports with zero failures, errors or skips; FIDO KtLint passes.

This covers local snapshot-copy staging. Snapshot repository and SQLDelight/provider copies, downstream state lifetime, allocation exhaustion and heap roots remain separate open boundaries.
