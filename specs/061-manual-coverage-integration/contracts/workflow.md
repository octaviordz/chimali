# Contract: Coverage Review and Target Push

## Local pre-push

Input: Git remote name/URL and stdin rows `<local-ref> <local-object> <remote-ref> <remote-object>`. Resolve `coverage.targetRef` to the intended named remote and exact `refs/heads/...` destination. Preserve slashes; do not guess ambiguous URL-only mappings.

| Condition | Outcome |
|---|---|
| No configured target | Allow without parsing update records or requiring coverage, device, harness, baseline or evidence directory. |
| Configured target and valid empty stream or no matching remote/ref | Allow without coverage, device, harness, baseline or evidence directory. |
| Matching non-deletion update | Verify exact proposed commit against live target and accepted baseline; proceed only on complete PASS. |
| Several updates including a match | Assess each matching candidate; any failure aborts the entire push. |
| Configured target with malformed input or ambiguous target; matching deletion, unavailable prerequisite, failed/incomplete verification | Block with an actionable reason; never report unexecuted work as PASS. |
| Configured archive exemption | Never bypass a matching product target merely because an archive ref is configured. |

Bind the exact candidate SHA/tree using an isolated clean checkout or fail closed if unsafe. Clear inherited Git environment when invoking Git in another checkout; retain freshness and post-run identity checks. Preserve Git's two standard remote arguments.

## Manual assessment and PR

The developer runs the assessment against the explicitly selected target and links the specific archived report in the PR. It binds candidate and assessment-time baseline revisions/trees, trusted harness, policy, suites/device scope, counts, decisions, limitations and digest. The authorized reviewer manually rejects absent, untrusted, incompatible, tree-mismatched, failed or incomplete evidence; an older assessment baseline alone does not invalidate evidence. Local checks do not establish a hosted required check.

Before accepting a PR, the reviewer resolves the current PR head and `main` commit through authenticated repository access and computes the prospective merge tree in an isolated, non-mutating Git context. Conflict, unavailable identities or an uncomputable tree prevents acceptance. Compare that tree with the tested-tree identity in the sealed report and record the head, base, computed tree and result with the review decision. An advance of `main` alone does not require recalculation of the assessment's baseline or repeating its comparison; recompute only the prospective tree when head or base changes. If the proposed tree differs, verify the changed tree before acceptance. Recheck head and base immediately before merge; this check is not an atomic guarantee against a later race. After merge compare the actual tree with the accepted evidence, withholding promotion and surfacing a mismatch for remediation.

For a different or conflicted prospective tree, the supported remediation is for the developer to merge the then-current `main` into the feature branch, resolve conflicts, commit the result and run the full assessment on that new feature commit against the accepted baseline selected at that assessment. Publish and link the new report, retain the earlier report unchanged, and repeat the prospective-tree check before review. The tool must not change or push the developer's branch automatically. If `main` advances again, repeat only the tree check; a further assessment is required only when the proposed tree differs or evidence is otherwise invalid.

The review and publication authority is an authenticated repository user with the necessary access, or an agent explicitly acting under that user's authority. The trusted harness and authority configuration are selected outside candidate-controlled content. Verify the actor's authenticated identity and applicable repository access at the review or publication action; never accept an actor name, PASS flag or credential supplied by the candidate as proof of authority. Record reviewer identity, PR, run/digest, head, observed base and prospective tree in the review record. A same-owner agent may act on the owner's behalf; no separate human reviewer or bypass-exception record is imposed. When the owner authored the PR, GitHub cannot record a self-approval: the authenticated owner decision is recorded as a review comment bound to the exact run and tree, and must not be described as a GitHub approval. For other reviewers, acceptance requires a GitHub approval. Do not expose publisher credentials to candidate execution or represent a manual decision as a hosted App check.

## Archive and baseline

The initial baseline identifies the `lab/or/main` commit used to create `main`; this creation-commit identity check applies only to initial baseline acceptance. Later candidate assessments and promoted baselines use their own recorded identities. Rename the archive to `swe/coverage-evidence`, retaining historical commits, bytes and legacy receipt identities; new receipts bind the new exact ref. Read back and verify digests, protect against concurrent writes and never promote synthetic records.

After a verified merge, independently validate the review, integrated/tested tree, original assessment baseline and archived bundle. Promote the accepted index atomically with expected-old-ref concurrency checks and read-back; failure leaves the prior baseline intact. Feature 058 and governing rules reflect the assessment-time baseline policy; the workflow remains planned until implemented and verified. Preserve GitHub PR protection, `octaviordz` bypass and the hosted-runner waiver. Actual remote operations require current-state and permission verification.

### Sealed assessment and reviewer authority

The assessment record is created from the trusted harness after verification and is immutable. It binds the authenticated repository and PR (when applicable), actor and authority source, candidate commit/tree, target commit/tree observed at capture, accepted baseline commit/tree/run/index digest selected at assessment time, policy and scope fingerprint, clean harness commit and file digests, device identity/profile, required suite outcomes, raw instruction/branch counts and applicability, individual decisions, limitations, unique run ID, bundle/manifest digests, archive ref/commit and UTC timestamp. The decision is `PASS`, `FAIL`, or `INCOMPLETE`; only a complete trusted verification can produce `PASS`.

Reviewer authority must be checked at the action boundary against the authenticated repository identity and current repository access using a trusted source. A claimed username in a request, a candidate file, or an assessment bundle is descriptive data and never grants authority. An agent may act only under an authenticated user's explicit authority, and the record identifies both the acting agent and authorizing user. The harness, policy, authorization configuration, archive destination, and publisher credentials come from a reviewed trusted checkout or operator configuration outside the candidate tree. Candidate execution receives no archive write credential. Publication happens in a separate trusted process after candidate execution has ended.

Assessment, review, and publication records are append-only. A failure or missing prerequisite is retained as `FAIL` or `INCOMPLETE`; it cannot be rewritten as a passing decision. Archive promotion uses compare-and-swap against the exact previously observed accepted-index ref, validates the immutable bundle digest and integrated/tested tree, writes the new index atomically, then reads it back. A concurrent update, digest mismatch, or read-back failure leaves the previous accepted index authoritative.

Historical receipts remain bound to the archive ref recorded when they were created (`refs/heads/codex/coverage-evidence`). New publications use only `refs/heads/swe/coverage-evidence`; migration must preserve old commit and bundle bytes and cannot reinterpret legacy receipts as new-ref receipts.
