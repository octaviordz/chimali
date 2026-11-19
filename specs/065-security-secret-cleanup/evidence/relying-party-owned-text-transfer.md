# RelyingParty owned-text transfer cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-001

## Source correction

`RelyingParty.fromOwnedText` now stages custody of both the RP identifier and display-name owners until its model factory returns successfully. Any construction failure erases both inputs in `finally`; successful construction transfers them to the returned model. The test seam injects a deterministic construction failure and inspects the original character buffers.

## Verification and limitations

The full `:core:domain:testAndroidHostTest` suite passes 106 tests across 24 reports with no failures, errors or skips. `:core:domain:ktlintCheck` passes. Allocation exhaustion itself was not injected; serializer/DAO/provider copies and heap-root behavior remain outside this evidence.
