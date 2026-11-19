# Legacy CustomField diagnostic redaction

**Finding:** SEC065-VAULT-009  
**Date:** 2026-11-16  
**Scope:** `feature.vault.model.CustomField` generic `toString()`

The legacy model stores a caller-provided custom-field name as an immutable `String` and a mutable `CharArray` value. Its generated data-class representation included the name. A synthetic marker regression failed before correction because the marker appeared in `toString()`.

The model now returns a fixed redacted generic representation. Explicit property access, equality/hash behavior and `clearMemory()` semantics are unchanged. The full `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck --no-daemon` run passes 97 tests across 23 reports with zero failures/errors; KtLint passes. `CustomFieldPrivacyTest.xml` SHA-256: `9BD062063C85442B16106FE243FD470E0651D16BC5A33D37F8C04C14D73582E8`.

A source search found no in-repository call sites for this legacy type; it is retained to avoid an unreviewed API removal. The immutable name String remains caller-owned and GC-managed, and external consumers are not audited.
