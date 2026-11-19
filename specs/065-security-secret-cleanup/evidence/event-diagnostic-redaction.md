# Event diagnostic representation redaction

**Finding:** SEC065-CORE-035  
**Date:** 2026-11-16  
**Scope:** `VaultEvent` and `PasskeyEvent` generic `toString()` output

Synthetic marker regressions failed before correction for both event families. Vault `Created`, `Updated` and `Deleted` default data-class representations included aggregate identifiers; `Created` also formatted its identity, type, title and payload fields. Passkey `Registered` had a custom representation that still included `aggregateId`; `Authenticated` and `Deleted` used generated representations that included it.

All Vault event kinds now redact identifying and sensitive fields in generic formatting. All Passkey event kinds redact aggregate identifiers, while `Registered` continues to redact its existing account and key material. The shared `DomainEvent` KDoc now states the same restriction for implementations. Explicit properties, data-class equality and serialization fields remain unchanged.

The full `:core:domain:testAndroidHostTest :core:domain:ktlintCheck --no-daemon` run passes 103 tests across 24 reports with zero failures/errors; KtLint passes.

- `VaultEventPrivacyTest.xml` SHA-256: `5347E5E28B8675E9D76E8CA37EDEB855EBC6C89751B07292CEB0C04C3CEDB458`
- `PasskeyEventPrivacyTest.xml` SHA-256: `1F1D1254C732557B82C499A678D17E4BDAC9B7CF2F767ED3FAB79435CFCE0D52`

This is host evidence for generic formatting only. Explicit-property logging by callers, persisted/provider copies, device behavior and heap roots remain open.
