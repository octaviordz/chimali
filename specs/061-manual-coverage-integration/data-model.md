# Data Model: Manual Coverage Review

## Configured target and push

- Explicit remote identity and full destination ref derived from `coverage.targetRef`; absent means no automatic local coverage hook. Invalid or ambiguous mappings are rejected.
- Each Git update contains local ref, candidate object, destination ref and prior remote object. Match exact remote/ref. A target deletion has no candidate and is blocked by the local check.

## Candidate assessment

- Candidate commit/tree, PR/repository identity when applicable, authenticated actor and authority source, target commit/tree observed at capture, trusted harness commit and file digests, policy/scope fingerprint, device profile, run ID, required test outcomes, raw applicable coverage counts, assessment-time accepted-baseline commit/tree/run/index digest, comparison, limitations, archive identity, UTC timestamp and digest-bound artifact locations.
- States: requested → capturing → PASS/FAIL/INCOMPLETE. Missing or invalid input cannot yield PASS. Each matching target update binds its own candidate.

`PASS` means the trusted complete verifier accepted the required execution, floors and applicable comparison. `FAIL` means a completed check rejected policy or behavior. `INCOMPLETE` means required evidence or execution was unavailable or invalid. A reviewer decision is separately recorded as `ACCEPT` or `REJECT` and cannot change the assessment result. Identity claims from candidate content do not establish repository authority. When an agent acts for a user, retain both authenticated actor identity and explicit authorizing-user identity.

## Accepted baseline

- Target commit/tree, run ID, immutable bundle path/digest, policy/scope and acceptance provenance. The external evidence index resolves to this bundle.
- Initial state is absent until matching independently validated or freshly captured evidence for `main`'s creation commit is accepted. The creation-commit restriction applies only to that initial baseline; later candidates and baselines retain their own commit/tree identities. Each assessment retains the accepted baseline used at assessment time. Promotion requires trusted review, matching integrated/tested tree and atomic index update; a changed index alone does not require the original assessment to be compared again.

## Archive and review record

- Archive ref/commit, unique run ID, sealed bundle digest, paths and receipt. New records use `refs/heads/swe/coverage-evidence`; historical receipts retain original `refs/heads/codex/coverage-evidence` identity and bytes.
- Review binds PR, authenticated authorized reviewer (or agent acting for that user), current head and observed base, prospective merge tree, tested tree, assessment-time baseline, report/run/digest, decision and time. Candidate-supplied identity is not authority. No separate bypass exception record is required. Manual review never asserts a hosted check.
- Lifecycle: captured → verified → archived → reviewed → integrated → eligible for promotion. A changed proposed tree requires renewed verification before acceptance; target advancement alone does not require a new baseline calculation or comparison. Concurrent index changes still require safe, atomic promotion handling.
- Publisher credentials are available only to the trusted publication process after candidate execution. Archive and authority configuration are selected outside candidate-controlled content. Index promotion compares the expected old ref, validates bundle and integrated-tree identity, updates atomically, and verifies read-back; any error preserves the previous accepted baseline.
