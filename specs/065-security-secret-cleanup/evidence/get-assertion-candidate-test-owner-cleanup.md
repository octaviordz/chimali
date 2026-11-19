# GetAssertion candidate test owner cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-030  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`GetAssertionCandidateOwnershipTest` previously erased candidate summaries and request RP metadata only after all assertions passed. If use-case execution or an assertion failed, its synthetic selected/rejected summary owners, `GetAssertionOptions` RP owner and client-data hash could remain populated through test teardown. Cleanup now runs in `finally`; it erases returned candidate owners, both fixture summaries, options RP text and the explicit hash array on every exit.

## Verification

Focused `GetAssertionCandidateOwnershipTest` passes 1/1. Full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips; KtLint passes. Current focused XML SHA-256: `0f5634c597375cb923405700803c2ef01d3f292c94581e691cde78ef9ee9343a`; report index SHA-256: `f70e1e1becb000b7cd1c09a55d53957b8293dc3c602c7849e43b1573bf6a3beb`.

The synthetic credential identifier is immutable test data. This closes only test fixture cleanup; production caller terminal paths, provider/heap behavior and full lifecycle evidence remain open.
