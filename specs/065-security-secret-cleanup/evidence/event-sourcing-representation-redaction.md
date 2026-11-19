# Event-sourcing generic representation redaction

**Finding:** SEC065-CORE-034  
**Date:** 2026-11-16  
**Scope:** `Snapshot<T>` and `TraceEntry` generic `toString()` output

`Snapshot<T>` default data-class formatting included its aggregate identifier and reconstructed state, which may carry vault/passkey data. `TraceEntry` formatting included caller-supplied description and metadata, for which no production construction call site was found in the current source search. The values remain available through their explicit properties; serialization and persistence behavior are unchanged.

Both types now provide fixed generic representations that redact their caller-supplied content. Synthetic tests assert marker omission and explicit property compatibility. The full `:core:domain:testAndroidHostTest :core:domain:ktlintCheck --no-daemon` run passes 101 tests across 22 XML reports, zero failures/errors; KtLint passes.

- `SnapshotPrivacyTest.xml` SHA-256: `C44D67C693E42CD8F6AB596733F0C1F9D568CEA086AA304E5F7EC94A56B68DD2`
- `TraceEntryPrivacyTest.xml` SHA-256: `072A67282BFF8E471888704C5E930EC34D133AC4E354E3808079A4C497A012DE`

This verifies generic representation behavior on the Windows JVM host. It does not establish that all call sites avoid explicit sensitive-property logging, or prove storage/provider/heap behavior. No iOS compilation was run in this check.
