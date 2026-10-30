# Changelog: Local HDK Profile Migration

- **Date**: 2026-10-14
- **Feature**: 055 — Migrate the local HDK draft profile
- **Status**: Core implementation and documentation reconciliation recorded; all remaining performance work is deferred by the user to a later phase

## Summary

Replaced the former remote-style HDK implementation with Chimali's named local derivation profile. The change binds credentials to the complete decoded credential ID and explicit algorithm/profile metadata, derives a wallet root from the full BIP39 seed, and uses separate derivation branches for P-256, Ed25519, and ML-DSA-65.

This is a breaking clean-install change authorized for feature 055. Existing credential key aliases and public-key repair behavior were removed; no compatibility fallback, old-database reset, or migration to the new deterministic identity is provided by this feature. Required credential recovery metadata remains necessary alongside the wallet mnemonic.

## Implementation changes

- Applied constitution version 3.0.0 and synchronized the local-profile requirements while preserving stable requirement identifiers and historical records.
- Removed the retired remote KEM, hash-to-scalar, multiplicative-blinding, repair-worker, and credential-alias paths where they were no longer used.
- Added independent synthetic fixtures and current profile tests. The configured shared/host suites passed 105 suites and 557 tests on 2026-10-14.
- Kept derivation secrets and signing scalars in software-managed operation buffers. This does not provide Android Keystore non-exportability or hardware-backed signing custody.
- Updated current documentation to distinguish the local profile from the OpenID4VCI issuance draft and to state its recovery and compromise limits.

## Verification and remaining gates

The implementation evidence and exact verification scope are maintained in [implementation-evidence.md](../../specs/055-migrate-hdk-draft/implementation-evidence.md). The report records independent fixtures, host/device suites, encrypted persistence/restart/recovery, lifecycle and owned-buffer checks, coverage floors, local CI and the user-designated ASUS baseline/current comparison. Each result retains its dated workload, build and limitations; historical test totals above are not represented as a new full-suite run.

The T005 design review, T072/T075 formal implementation review and supplemental assertion-UV review are recorded. The supplemental test-stub and CI-scope conditions were remediated. Acceptance applies to the named local profile and reviewed scope; it establishes neither deployment, issuer interoperability, hardware-backed signing custody, malware resistance after root compromise nor mnemonic-only account discovery.

### Implementation status update — 2026-10-16

The corrected baseline build succeeded after disk space was freed, and the matched ASUS baseline/current capture is recorded. T003/T066/T071/T074/T082 are checked; T069/T083 documentation reconciliation is complete. Constitution 3.1.0 and current requirements distinguish all internal FIDO operation processing (<200ms) from end-to-end response (<1s).

Fresh instrumentation records all dispatched CTAP commands. Three verified registrations produced MakeCredential internal times of 247.818, 284.752 and 229.253 ms; T085 investigation/optimization and the operation sample campaign are deferred to [the later phase](../backlog/fido2-internal-performance-enhancements.md). Previous local HID handoff results do not replace this internal metric.

The user accepted the observed ASUS/Windows UX and deferred T084's end-to-end measurement investigation to [the Feature 060 assessment](../../specs/060-fido2-latency-measurement/implementation-evidence.md). Raw browser totals and prompt-adjusted residuals remain recorded; unmeasured Windows interaction prevents treating the residual as system-only latency. SC-007 remains partial. No production activation is claimed.
