# Coverage Tooling Test Fixtures

## Purpose & Scope
This directory contains deterministic test fixtures for validating the coverage enforcement tooling, including:
- Policy schema validation (`schema.json`)
- Threshold, ratio, and rounding evaluations (`floors.json`)
- Module discovery, compilation inventory, and canonical zero-counter proof (`inventory.json`)
- Exporter-generated XML and proof bindings (`exporter/`)
- Verification policy and CLI argument rejections (`policy.json`)
- Freshness, run integrity, and dirty tree checks (`freshness.json`)
- Test suite outcomes and JUnit report parsing (`suites.json`)
- Data collection, variant probes, and multi-procedure runs (`collection.json`)
- JaCoCo execution compatibility receipts, attribution bindings, and real host/device probe checks (`execution-compatibility.json`, `execution-attribution.json`, `probe-compatibility.json`)
- Integration orchestration and target resolution (`integration.json`)
- Target-to-candidate comparison, regression, and compatibility (`comparison.json`)
- Baseline module report path/digest binding (`comparison-module-reports.json`)
- Publication receipts, App reporter verification, and Candidate success binding (`publication.json`)
- Publisher lifecycle, promotion, and failure receipts (`publisher-lifecycle.json`)
- Behavior test risk-ranking and batch remediation (`selection.json`)
- Accepted comparison baseline lifecycle and index management (`baselines.json`, `archive-lifecycle.json`)

## Fixture Ownership & Governance
- Fixtures are owned and maintained by the coverage verification harness (`tools/coverage/`).
- They are exercised purely in PowerShell 7 (`pwsh`) via `tools/coverage/test-policy.ps1` using built-in .NET JSON/XML parsing and arbitrary-precision integer arithmetic (`[System.Numerics.BigInteger]`).
- No external test frameworks (e.g., Pester) or extra Gradle build modules are used.
- The default policy fixture suite stays offline and dependency-light. Runtime fixtures are opt-in with `-IncludeRuntimeFixtures`; the JaCoCo probe fixture requires the compiled security classes, retained host execution data, and the pinned JaCoCo cache. It invokes the real Gradle report task and restores/removes its temporary execution files.
- Toolchain versions are pinned to:
  - Gradle 9.6.0
  - Android Gradle Plugin (AGP) 9.4.1
  - Kotlin 2.3.21 / JVM Target 17
  - JaCoCo 0.8.15

## Synthetic Data Restrictions
- **Strictly Synthetic Data**: Under Principle I (Cryptographic & Memory Security), all test fixtures MUST contain synthetic, fabricated data only.
- No real credentials, private keys, BIP39 mnemonic seeds, or production secrets may ever be placed in fixture files.
- Fixtures representing device outputs, Git commit SHAs, or run UUIDs use deterministic, non-production test vectors.
- Fixture files must remain self-contained, offline-executable, and immutable during test runs.

## Sealed-Bundle Verifier Scenarios
`run-verifier-counter-fixture.ps1` exercises the production `verify.ps1` entry point by mutating a copy of a complete sealed source bundle. It requires a current-schema bundle with the exact workload and harness evidence required by the repository policy; historical bundles are unsuitable because the production verifier binds acceptance to the current harness. The `comparison-candidate-below-floor` scenario builds a digest-consistent accepted target whose `:core:security` instruction coverage is below 60%, then verifies a candidate against its indexed target with changed Tier 1 paths. `execution-device-file-mismatch` tampers with a device execution-input hash in a module's sealed compatibility receipt and confirms the verifier rejects that receipt against canonical manifest attribution. These are verifier behavior tests, not baseline evidence.

## Changed-Path Receipt Scenarios
Changed-path receipts remain named `changed-paths.txt` for bundle compatibility, but their contents are UTF-8 JSON with `format: chimali.changed-paths`, `schema_version: 1`, exact `target_commit` and `source_commit` bindings, and an array of repository-relative `paths`. Git collection uses NUL-delimited output with rename detection disabled, so old and new paths both remain visible and whitespace/Unicode are never interpreted as Git's display quoting. Capture, Gradle selection, integration validation, and the production verifier all consume the same receipt; the bundle manifest artifact digest seals its bytes. Unsupported versions, mismatched commits, duplicates, traversal, absolute paths, and malformed entries fail closed. `run-changed-paths-fixture.ps1` exercises the actual collector and owner resolvers against a temporary Git history containing Unicode, spaces, deletions, cross-owner moves, and unaffected paths.

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1 `
  -FixturePattern changed-paths.json
```

Run it after producing a current-schema bundle:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/fixtures/run-verifier-counter-fixture.ps1 `
  -RepositoryRoot (Get-Location).Path `
  -SourceBundle '<current sealed bundle path>' `
  -Case comparison-candidate-below-floor
```

The production-entry lifecycle suite adds missing-workload evidence, unowned JUnit report, and incompatible accepted-baseline fingerprint mutations. `test-policy.ps1` runs these only when supplied with a complete current-schema bundle:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1 `
  -FixturePattern production-verifier-entry.json `
  -CurrentSchemaBundle '<current sealed bundle path>'
```

## Publisher Promotion Scenarios
`run-publisher-promotion-fixture.ps1` runs a copy of the production publisher and shared coverage helpers in an isolated Git repository with a local bare remote. The real publisher performs candidate/tree identity checks, commit ancestry checks, immutable bundle archival, accepted-index update, remote read-back, and receipt creation. Only independent coverage verification and repository API responses are replaced with explicit JSON doubles in the valid-promotion modes. The forged-assessment case runs the real trusted verifier against a malformed bundle and asserts that its failure decision is retained in a non-success receipt bound to the run, manifest, and bundle. Success, unsupported-event, and stale-base modes assert both receipt behavior and whether the remote accepted index changed; they never contact GitHub or use credentials.

## Reporter Success Protocol
`run-report-success-protocol-fixture.ps1` exercises the production reporter, trusted verifier, and publisher against an isolated local bare Git archive and a deterministic GitHub API double. It requires a current-schema Candidate bundle with trusted reporter identity binding, a compatible comparison bundle and accepted index, and sealed changed-path evidence. The fixture asserts that verification, archive publication and byte-for-byte read-back, receipt digest binding, and the final success check all refer to the same run and captured head. It uses no network service credentials. Run it after producing those inputs:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/fixtures/run-report-success-protocol-fixture.ps1 `
  -RepositoryRoot (Get-Location).Path `
  -CandidateBundle '<current Candidate bundle path>' `
  -ComparisonBundle '<accepted comparison bundle path>' `
  -AcceptedBaselineIndexPath '<accepted baseline index path>' `
  -ChangedPathsFile '<Candidate bundle changed-paths.txt path>'
```
