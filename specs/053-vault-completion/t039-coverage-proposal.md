# T039 proposal: coverage evidence for terminal exception branches

**Status:** Approved and applied. **Date:** 2026-09-09.

## Approved decision

Amend Constitution XII.3 only for a branch whose covered outcome necessarily terminates by throwing a documented exception before JaCoCo can place a successor probe. Treat that outcome as covered when an independent test invokes the exact input, asserts the exact exception, and verifies the relevant cleanup invariants.

This is not a reduction in test coverage. It recognizes an instrumentation limitation for direct `throw` exits while retaining 100% executable-line coverage, full coverage for every nonterminal decision, and explicit exception-path tests.

## Current evidence

The scoped `vaultMemoryCoverage` report passes and records:

| Unit | Lines | Branches |
|---|---:|---:|
| `VaultCryptoServiceImpl` | 83 / 83 | 15 / 15 |
| `MutableDraftField` | 26 / 26 | 10 / 10 |
| `VaultPayloadCodec` | 357 / 357 | 207 / 208 |

The only uncredited branch is `VaultPayloadCodec.Reader.requireNonzeroDigit()` when the number ends immediately after a minus sign. `missingRequiredFieldsAndTruncatedTokensEraseAllPartialData` supplies that exact truncated JSON input, asserts `IllegalArgumentException`, and verifies every captured partial mutable allocation is erased. The branch executes; the direct throw cannot receive a later JaCoCo probe.

## Proposed Constitution wording

Constitution 1.1.0 appends to XII.3, Strict Coverage Gates:

> A terminal exception branch that a line/branch instrumenter cannot credit because it exits directly through `throw` may be accepted only when all nonterminal executable lines are fully covered, the exact branch input and exception type are independently asserted, and the test verifies all cleanup invariants affected by the exit. The exception must be documented with the instrumenter report, source location, test name and reason a normal successor probe would alter or obscure production behavior. This exception does not apply to recoverable, nonterminal, asynchronous, cancellation, or externally delegated paths.

## Scope and non-goals

- It applies only to the one documented malformed-number EOF branch in T039.
- It does not exempt encryption, mutable-draft cleanup, serializer compatibility, failure mapping, cancellation, platform controls, or runtime tests.
- It does not permit excludes, generated-code annotations, unexecuted branches, fabricated probes, or production state changes made only to improve a coverage report.
- If JaCoCo or a replacement instrumenter can credit the branch without changing behavior, the exception is removed and ordinary 100% branch coverage resumes.

## Rollout after approval

1. Amend Constitution XII.3 using the proposed wording and increment its minor version.
2. Add this proposal and the final report evidence to `quickstart.md`.
3. Mark T062 and umbrella T039 complete after re-running the scoped report and final project checks.
