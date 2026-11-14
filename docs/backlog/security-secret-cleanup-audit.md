# Backlog: Security Secret Cleanup and Platform Boundary Audit

**Status**: Open — current-release audit unverified

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-SEC-001`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: Cryptographic & Memory Security (Constitution Principle I, Rules §5.5, TRD §5.1, BRD `NFR-SEC-010` / `030`)
- **Mapped Feature Requirements**: FR-006, FR-007, FR-008
- **Remaining Scope**: Static code inspection across production source code to audit all platform UI text boundaries and memory zeroing; dynamic runtime heap inspections; physical verification of clipboard 60s timeout on physical Android devices.
- **Reason Excluded**: Feature 057 is strictly documentation-only; historical evidence is consolidated without certifying new source audits. "Runtime compliance remains a separate assessment with its own dated evidence."
- **Current Verification Status**: `Unverified` (Documentary consolidation complete; live source audit pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: Vault ownership changelog (`docs/changelogs/2026-09-09-vault-secret-ownership-remediation.md`), Vault handoff (`specs/053-vault-completion/handoff.md`), and feature-055 security review (`specs/055-migrate-hdk-draft/security-review.md`) retain their original dated scopes and commit fingerprints. Consolidation MUST NOT expand reviewer fingerprints or provider-erasure assurance.
- **Responsible Role**: Security Lead / Security Engineer
- **Needed Completion Evidence**: Formal security audit report against the current release branch, recording commit hash, verified files, confirmed absence of string secrets, and heap dump verification of terminal cleanup. Documented handling of residual provider limitations (BouncyCastle `BCMLDSAPrivateKey` lack of destroy, JCA `BigInteger`, ICU native heap, and IME caches) without false erasure claims. Explicit audit of identifying RP/user account strings, DB projection models, and CTAP request models in memory to verify absence of plaintext retention, improper audit logging, and evaluate residual GC-collected String instances.

## Next steps

Inventory current secret and identifying-data usages against the canonical registers, with the assessed revision and files. Inspect ownership, terminal cleanup, UI lifetime, logging and persistence; repair confirmed defects and execute cleanup and clipboard-timeout checks with synthetic data. Record residual provider, IME and OS limits.

## Evidence and completion

Store canonical entries in the two proposed registers. Cross-reference assessed source usages and ownership/cleanup tests or manual evidence; retain detailed run records in the relevant feature evidence. Mark unassessed usages explicitly open.

Populated registers for a declared assessed scope; reconciled classifications; updated references; each assessed entry evidenced or marked unresolved; no blanket compliance claim or new security exemption.

Evidence identifies the assessed revision, files, procedures, observed results and limitations. Distinguish owned-buffer erasure from immutable provider, IME and OS memory limits; preserve historical review scopes.
