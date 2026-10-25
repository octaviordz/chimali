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
- Integration orchestration and target resolution (`integration.json`)
- Target-to-candidate comparison, regression, and compatibility (`comparison.json`)
- Publication receipts and App reporter verification (`publication.json`)
- Behavior test risk-ranking and batch remediation (`selection.json`)
- Accepted comparison baseline lifecycle and index management (`baselines.json`, `archive-lifecycle.json`)

## Fixture Ownership & Governance
- Fixtures are owned and maintained by the coverage verification harness (`tools/coverage/`).
- They are exercised purely in PowerShell 7 (`pwsh`) via `tools/coverage/test-policy.ps1` using built-in .NET JSON/XML parsing and arbitrary-precision integer arithmetic (`[System.Numerics.BigInteger]`).
- No external test frameworks (e.g., Pester) or extra Gradle build modules are used.
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
