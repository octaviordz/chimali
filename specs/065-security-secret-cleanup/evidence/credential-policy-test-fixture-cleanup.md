# Credential policy test fixture byte cleanup

**Finding:** SEC065-CORE-037  
**Date:** 2026-11-16  
**Scope:** `CredentialProtectionPolicyTest` synthetic credential-ID inputs

The policy tests constructed mutable 16-byte arrays inline and passed them to `CredentialId.fromByteArray`, which borrows the caller buffer. The test now creates each buffer in a helper and fills it with zero in `finally` immediately after the encoding call. The resulting synthetic `CredentialId` is an immutable Base64URL string and remains GC-managed until the test completes.

The full `:core:domain:testAndroidHostTest :core:domain:ktlintCheck --no-daemon` run passes 104 tests across 24 reports, zero failures/errors; KtLint passes. `CredentialProtectionPolicyTest.xml` SHA-256: `73608AB8AD419F72C258F5E5201B5E6B9C57F9A2651BC28196E117BDF9C1C956`.

This evidence covers only the synthetic mutable fixture buffer. It does not claim explicit erasure of the encoded identifier String or external copies.
