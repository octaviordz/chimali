# FIDO2 Authenticator Remediation

Date: 2026-09-10  
Feature: `054-remediate-fido2-authenticator`

## Summary

Removed the dead `Fido2Authenticator` facade that exposed unimplemented credential and pairing
operations. Existing CTAP handlers and ceremony use cases remain the authoritative live paths,
avoiding a second, nonfunctional implementation surface.

## Changes

- Deleted `Fido2Authenticator` and `Fido2AuthenticatorImpl`, including their “Not implemented”
  operations and empty credential flow.
- Added `AuthenticatorInfoProvider` for the authenticator capability contract and routed Bluetooth
  HID `getInfo` responses through it.
- Corrected credential repository behavior so `credProtect` policy value `3` is filtered and counted
  as requiring user verification instead of being silently discarded or reported as zero.
- Removed stale facade dependencies from tests and added regression coverage for capability reporting,
  credential filtering, and `needsUV` statistics.

## Verification

- `:feature:fido2:compileAndroidHostTest` — passed.
- `:feature:fido2:testAndroidHostTest` — passed.
- Production placeholder scan found no remaining `Fido2Authenticator`, “Not implemented”, or
  `filter { false }` remnants in the FIDO2 source.
- `git diff --check` — passed.
