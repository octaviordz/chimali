# Implementation Plan: Manual Coverage Review for Integration

**Branch**: `061-manual-coverage-integration` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

## Summary

Route the local coverage hook only to pushes updating an explicitly configured target. Provide manual feature assessment and PR evidence, migrate the archive, establish `main` and its baseline, and document manual review and promotion. GitHub PR rules control access; the authorized reviewer makes the coverage decision under the existing hosted rollout waiver.

## Technical Context

- **Language/platform**: PowerShell 7, Git 2.54+ config-based hooks, existing Windows/Android tooling and JSON evidence; preserve the Gradle/JaCoCo/Kotlin toolchain.
- **Dependencies**: Existing `tools/integration-ci.ps1`, `tools/coverage/` capture, verifier, publisher, promotion and fixtures; GitHub administration; Android platform tools and a capable authorized device.
- **Storage**: Stable evidence directory outside the measured checkout, with a validated `baselines/current.json` and immutable digest-bound bundles; protected `swe/coverage-evidence` archive.
- **Testing**: Isolated Git repositories and stub verifier for routing; existing policy/publication fixtures for identity, integrity, promotion and races; applicable static checks, local CI and connected coverage. A real baseline and device are required for an end-to-end demonstration.
- **Constraints**: Preserve coverage floors, required suites, compatible scope, trusted verifier and nonregression; no hosted runner activation or false hosted success. Git aborts the whole push on a hook failure. No product behavior change.
- **Scope**: One configured target per checkout; multi-ref pushes; manual developer assessment and manual PR acceptance. No new application module.

## Constitution Check

| Principle | Design disposition |
|---|---|
| I–IV, VIII | No production credential, architecture or persistence changes. Evidence uses synthetic data and excludes secrets. |
| V | Preserve automated CI floors (Tier 1 60%, Tier 2 20%, aggregate 50%), required execution and traceable evidence. Manual PR review does not certify a hosted required check. |
| VI | Preserve existing performance obligations; coverage evidence is not performance acceptance. |
| VII | Reconcile current Feature 058 and higher-authority requirements as needed, preserve dated history and hosted waiver, and reuse existing tools. |

**Post-design gate**: The governing BRD, TRD, rules and Feature 058 requirements specify strict nonregression against the accepted assessment-time baseline, unchanged-tree reuse and renewed verification for a changed tree. Feature 058 retains the hosted waiver and requires trusted evidence and authorized manual review. Constitutional CI floors and required suites remain binding. Feature 061's local entry points are implemented and validated with fixtures plus local CI. Active rulesets protect `main` and both archive refs, and the exact-main initial baseline is published and accepted on `swe/coverage-evidence`. The repository default remains `lab/or/chimali`; the retained legacy archive contains a historical digest mismatch. Archive migration completion and the controlled GitHub review, merge and promotion remain open. See implementation evidence for the current credential incident and rollout details.

## Project Structure

```text
specs/061-manual-coverage-integration/  # spec, plan, research, data model, contracts, quickstart
tools/coverage/pre-push-hook.ps1        # target routing and Git hook inputs
tools/coverage/fixtures/               # isolated routing and publication cases
tools/integration-ci.ps1               # candidate, target and baseline verification
tools/coverage/                        # policy, verifier, publisher and promotion
tools/setup-hooks.ps1                   # setup and diagnostics
.gitconfig, .github/workflows/          # hook and dormant hosted references
specs/058-enforce-coverage-targets/     # governance and operational reconciliation
README.md                               # developer entry point; create if absent
```

**Structure decision**: Extend existing verification tooling and fixtures. Preserve the existing uncommitted Git argument handling fix.

## Phase 0 — Research

See [research.md](research.md) for target routing, baseline, archive migration, manual authorization and prerequisite decisions. Runtime and remote outcomes remain unverified.

## Phase 1 — Design and Delivery Sequence

1. Reconcile the authorized manual PR policy with Feature 058 and governing documents without lowering coverage gates or claiming hosted success. Identify any required governance amendment before changing authoritative requirements.
2. With no configured target, return without parsing update rows or requiring coverage setup. Otherwise parse Git's remote and all update rows before requiring a device, harness or evidence; allow valid nonmatching pushes without coverage setup. Normalize the configured remote and full destination ref; reject ambiguous targets, malformed input and matching deletions. Preserve standard Git arguments and stdin.
3. Verify each matching proposed commit against the live target and compatible baseline using an exact clean candidate checkout. If an arbitrary pushed SHA cannot be safely isolated, stop with an actionable diagnostic; never substitute current HEAD. Failure of one matching update aborts the whole push.
4. Provide explicit manual feature assessment using the same trusted verifier. Discover one authorized device only when unambiguous and validate capabilities. Use a stable external evidence location and a reviewed, clean, pinned trusted harness; verify the baseline index and digest before comparison.
5. Implement a reviewer-controlled manual publication and approval path that binds PR, authenticated authorized repository user or their agent, tested tree, assessment-time baseline, run, policy and digest. Verify authority against repository access and trusted configuration outside candidate-controlled content; keep publisher credentials away from candidate execution. Preserve immutable artifacts and atomicity without inventing hosted App receipts or requiring a second reviewer.
6. Prepare the archive rename from `codex/coverage-evidence` to `swe/coverage-evidence`; preserve historical bytes, Git identities and legacy receipt ref, update active exact-ref checks, dormant workflows, fixtures and current docs, and verify read-back, permissions, protections and concurrency. Synthetic trial records remain history, not baselines.
7. Verify the exact `lab/or/main` source commit and create `main` at that commit. Configure and verify PR rules, merge commits and `octaviordz` bypass, then establish a valid compatible baseline before the first PR. Prepare reviewable remote changes and preserve recoverability; do not claim rollout succeeded until verified.
8. Before manual merge, resolve current PR head/base and compute the prospective merge tree in an isolated, non-mutating Git context; reject conflicts or unknown identities and compare the tree to sealed evidence before acceptance. Record identities and comparison, and recheck head/base immediately before merge. Do not recalculate or compare coverage again solely because `main` advances. If the tree differs or conflicts, the developer merges the then-current `main` into the feature branch, resolves conflicts, commits and assesses the new feature revision, publishes a new report and repeats the tree check; tools do not change or push the branch automatically. After merge, compare the actual tree with accepted evidence and promote only a matching run through atomic index update and read-back.
9. Create a root README and detailed developer/reviewer procedures. Run isolated positive and negative fixtures, then applicable CI and connected coverage and a controlled manual scenario when prerequisites exist. Record unexecuted steps honestly.

## Requirements Mapping

| Specification | Design |
|---|---|
| FR-001–008, SC-001/003/004 | Branch creation, manual review, merge-tree check and baseline promotion: [workflow contract](contracts/workflow.md). |
| FR-009–012, SC-002/006 | Target routing, explicit assessment, device selection and fixture matrix. |
| FR-013–016, SC-005 | Trusted harness, archive migration, governance and hosted waiver. |
| FR-017, SC-007 | Root README and detailed quickstart. |

## Complexity Tracking

No constitutional exception or new service is proposed. Hosted enforcement remains explicitly waived and unverified.
