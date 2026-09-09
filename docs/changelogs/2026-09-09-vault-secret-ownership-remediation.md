# 2026-09-09 — Vault Secret Ownership and Lifecycle Remediation

**Feature**: `053-vault-completion` / T039

## Summary

Completed the Vault secret-lifetime remediation for password, credit-card, and secure-note
editing. Sensitive application data now has explicit mutable ownership from editor draft through
serialization, encryption, persistence, detail display, and terminal cleanup.

The work preserves the existing encrypted-payload format and adds migration-safe mutable title
handling. It also documents the boundary between application-owned buffers, which Chimali clears,
and framework or operating-system text copies, which the application cannot erase.

## Changed

- Replaced editor-held secret `String` state with typed mutable drafts. Replacement, discard,
  successful save, and disposal erase retired owned arrays. Failed saves retain the retry draft;
  failed submission copies and temporary buffers are erased.
- Added explicit submission ownership so a retry draft remains independent while a submitted copy
  is erased on success, rejection, failure, cancellation, and never-started work.
- Replaced full-secret JSON DTO serialization with the bounded mutable `VaultPayloadCodec`.
  Codec scratch storage, partial parsed fields, and UTF-8 buffers are erased on every exit.
- Hardened `VaultCryptoServiceImpl` so key acquisition occurs within cleanup ownership and keys,
  plaintext buffers, and undeliverable decoded payloads are erased for errors and cancellation.
- Migrated Vault titles across item, event, aggregate, ViewModel, repository, and database
  projection owners to `CharArray`; migration 5 stores the projection title as a UTF-8 BLOB.
  Event and snapshot JSON retain the prior v1 compatibility shape at their transient serializer
  boundary.
- Added input-boundary controls for keyboard learning, surrounding text, and autofill. Clipboard
  copy operations now own and erase their mutable transfer buffer. The platform-boundary audit
  records residual accessibility, IME, rendering, undo/history, and clipboard-consumer exposure.
- Kept passkey UI and private-key handling unchanged; passkey material remains outside the Vault
  draft and payload path.

## Verification

- Verified frozen legacy payload and metadata fixtures, including Unicode, escapes, optional
  fields, custom fields, encrypted payloads, and edit/reopen behavior.
- Added deterministic cleanup tests for key-provider failure before encryption, crypto and codec
  failures, partial allocation, cancellation, replacement, discard, success, disposal, and late
  callbacks.
- Ran API 35 managed-device checks: all three input-boundary cases pass and all 15 Vault ownership
  navigation cases pass.
- Final checks pass: SQLDelight migration verification; core domain, data, and Vault tests;
  Ktlint; Detekt; app compilation; Vault Android-test compilation; scoped coverage; and clipboard
  timeout host tests.

## Pending acceptance evidence

- T044 now includes an isolated production-graph test for both relative Koin module orders and
  is recorded as compiling successfully; its connected-device execution and full production-storage/
  passkey acceptance remain pending. Prior API 35 Vault navigation passes do not establish this result.
- T046's copy intent, mutable transfer ownership, clipboard service, and 60-second host tests
  pass. A device-level end-to-end clipboard timeout assertion remains pending. Check device availability
  when resuming; the verification history records successful managed API 35 runs for other suites.
- These pending runtime checks do not change the completed application-owned cleanup or codec
  implementation, but the corresponding task markers remain open until their required evidence
  is captured.

## Governance

The Constitution is now **1.1.0**. Its coverage rule retains full measurable statement and branch
coverage, and adds a narrow evidence rule for an independently asserted direct `throw` exit that
JaCoCo cannot probe. The sole accepted T039 case is the malformed-number EOF branch in the payload
codec; its test asserts the exception and verifies cleanup of every partial mutable allocation.

See [the T039 scope proposal](../../specs/053-vault-completion/t039-scope-proposal.md),
[the coverage proposal](../../specs/053-vault-completion/t039-coverage-proposal.md), and
[the final verification guide](../../specs/053-vault-completion/quickstart.md).

Current continuation steps and acceptance boundaries are recorded in the
[feature handoff](../../specs/053-vault-completion/handoff.md). Verification above summarizes prior
recorded runs; the changelog/handoff documentation update did not rerun application tests.
