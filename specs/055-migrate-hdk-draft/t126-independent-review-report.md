# T126 Independent Security Review Report

## Snapshot and reviewer

- Feature: 055 — local HDK profile migration
- Base revision: `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`
- Reviewed source/test manifest: `t125-current-source-manifest.sha256`
- Manifest SHA-256: `55053ec24959c5f4e186ed9e9cc4130b728f32419d5c3491d564ee54df631021`
- Manifest entries: 217 (186 present, 31 deleted)
- Reviewer: `/root/t125_security_review`, separate independent security-review agent; not an implementation author and not the T103 reviewer.
- Review date: 2026-10-17
- Disposition applies only to this exact manifest snapshot, before T127 changes. It is not a review of later uncommitted remediation.

## Materials and method

Verified the manifest SHA-256 and recomputed every present path fingerprint against current staged and unstaged working-tree bytes; verified all 31 declared deleted paths are absent. Result: zero malformed entries, missing paths, unexpected paths, or fingerprint mismatches. Compared the source/test delta with base `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` and enumerated all manifest paths by status and path class.

Reviewed the T104-T126 task history and the current plan security gates, `contracts/local-key-derivation.md`, `contracts/credential-lifecycle.md`, `security-review.md`, `security-review-packet.md` (including T103 reviewed-scope boundaries and the T125 request), the latest implementation-evidence verification appendix, and the T125 disposition template. Inspected the R1 registration/authentication consent-state changes, mutable owner-copy APIs, Compose rendering boundaries, and retained-owner lifecycle regressions. Also assessed directly relevant derivation/provider and root-key ownership code; CTAP parsing, negotiation and authorization paths; bounded CBOR/JSON adapters; aggregate/event/snapshot validation and persistence paths; database DAO/schema and app/provider wiring; diagnostics/logging; and directly related regression tests. Deleted T125 entries were treated as absent snapshot paths: the retired legacy derivation implementations/tests, old event/snapshot implementations moved to Android sources, removed dormant credential-management handler/tests, and removed schema migration history were accounted for. Current schema and clean-install scope were inspected.

Independently reran `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`. It completed successfully in 162.65 seconds (487 actionable tasks; 27 executed, 460 up-to-date). Inspected the resulting JUnit XML and coverage report artifacts. Focused lifecycle XMLs show authentication consent ownership 4/4 and registration owner integration 22/22. Connected ASUS XMLs show app 11/11 and FIDO2 lifecycle/UI/restart 25/25, all with zero failures, errors, or skips. The configured coverage gate passed. This was not a clean rebuild; T084/T085 performance campaigns remain deferred and SC-007 remains partial.

### Scope accounting

The manifest contains changes across app/provider wiring, security and domain production code, FIDO2 ceremony/crypto/persistence/presenter/logging paths, SQLDelight schema, direct host/device regressions, shared test helpers/fixtures, and deletions. The security-sensitive production paths and directly affected regression suites listed above were inspected. Test-only files outside those direct behaviors (unrelated UI assertions, generic stress/performance samples, and fixtures/helpers not exercising the reviewed boundaries) were classified from their paths and diff roles, but were not individually re-read line by line. Build configuration and specification/documentation files are outside the manifest's app/core/FIDO2 source roots and do not expand the reviewed implementation snapshot.

Schema compatibility with an existing pre-change encrypted database is outside the approved feature scope: the plan expressly selects clean installation, excludes old database conversion, and removes the SQLDelight migration history on that basis. No backward-compatibility claim is made. That upgrade question is retained as a scope limitation, not a feature blocker or a basis for this disposition.

## Findings

| ID | Severity | Evidence | Required remediation |
|---|---|---|---|
| T126-1 | HIGH | `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt` obtains RP/user metadata lookup-index arrays in `getCredentialsByRpId` (line 167), `getPagedCredentialsByRpId` (184), `getCredentialsByRpIdAndUserId` (213–214), and `getCredentialsByUserId` (226) without erasing them. The flow variants retain captured arrays for the flow lifetime. Insert and replacement paths do erase their owned index arrays, but the query paths do not. `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/dao/SearchableMetadataDaoTest.kt` verifies token matching, not retained-reference cleanup, query failure, or flow cancellation. | Give each query operation or collection explicit ownership of its generated lookup indices and erase them on success, failure, and cancellation, including partial acquisition of RP and user indices. Add retained-reference regressions for synchronous/paged queries and flow terminal paths. |
| T126-2 | HIGH | `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationState.kt:58` logs the credential ID. Lines 21, 24 and 127 retain the raw migration `Throwable` in a process-wide `AtomicReference`; line 128 logs that throwable. This can retain or emit uncontrolled diagnostic details. `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationTest.kt` covers migration success, rollback, retry and idempotency, but does not assert diagnostic redaction or cause-free retained state. | Replace record-ID logging and raw throwable retention/logging with fixed redacted diagnostics and a cause-free failure value. Add synthetic-marker captured-log and retained-state assertions while preserving rollback and retry behavior. |

No additional concrete defect was established in the inspected R1 consent-state owner remediation. Consent text is held in independent mutable owners, erased on state transitions and teardown, and converted to immutable text at the Compose rendering boundary. The focused regressions retain references and verify terminal erasure.

## Disposition

**REJECT.** The two HIGH findings above remain open and concern application-owned lookup-token lifetime and migration diagnostic exposure. The fingerprint and verification evidence establish the snapshot identity and configured test status; they do not close those ownership and diagnostic gaps.

Production activation is **not authorized** for this snapshot. Close T126-1 and T126-2, run the specified focused regressions and configured verification, update the evidence, and obtain a fresh independent disposition against a new fingerprint before considering activation. T103 ACCEPT remains restricted to its earlier source fingerprints and scope. This report makes no deployment or activation claim.

## Reviewer attestation

I verified the exact manifest fingerprint and reviewed the scope, evidence, findings, exclusions, and clean-install limitation recorded above. This disposition applies only to the stated snapshot and does not extend to later source changes.
