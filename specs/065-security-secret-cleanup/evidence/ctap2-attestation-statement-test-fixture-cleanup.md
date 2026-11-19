# CTAP2 attestation statement test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-029  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`Ctap2AttestationStatementTest` retained synthetic signature and certificate-chain arrays in its response-map fixtures. It now tracks and clears mutable response maps, zeros every synthetic byte array and releases the tracked arrays/maps/chains in `@AfterTest`, including after failed assertions.

## Verification

Focused class passes 8 tests with zero failures/errors/skips; `:feature:fido2:ktlintCheck` passes. The subsequent full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips. Current test XML SHA-256: `89858938af561d100973a39a0a5ce89d5697da3c7285b8a8f73cd7ca46fc1f26`; report index SHA-256: `f70e1e1becb000b7cd1c09a55d53957b8293dc3c602c7849e43b1573bf6a3beb`.

This is test-fixture cleanup only. Production signing/provider copies, certificate model lifetimes and heap roots remain unverified.
