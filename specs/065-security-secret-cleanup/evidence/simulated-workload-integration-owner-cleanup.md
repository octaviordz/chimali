# Synthetic performance integration fixture owner cleanup

**Finding:** SEC065-CORE-043  
**Scope:** `SimulatedWorkloadIntegrationTest` and its synthetic RP verifier/fixture; no production data or device account was used.

## Source review

The host integration test generates a software EC key pair, request challenge/hash arrays, MakeCredential/GetAssertion options, a returned passkey summary, an RP-verified credential-ID byte copy, and an assertion response. Before correction, request/options/key/summary cleanup was on the success path after assertions; failures could bypass it. The fixture already had a `dispose()` path and its repeated workload test already called it from `finally`.

The test now wraps generated owners in an outer `finally`. It erases option and request arrays, RP/user owners, passkey summary mutable fields and stored public-key encoding, the RP-verified credential ID, and generated fixture bytes. It attempts to destroy the synthetic JCA private key. The registration result's mutable credential owner is erased after the summary is created, while the attestation remains available to the verifier, which erases the result in its own `finally`. Assertion cleanup now also clears the optional mutable response user owner.

## Verification

` :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedWorkloadIntegrationTest ` passed: 1 test, 0 failures/errors/skips. Full FIDO host suite: 1,286 tests across 169 XML reports, zero failures/errors/skips; `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` and host-test compilation pass. SimulatedWorkloadIntegrationTest report SHA-256: `c50d4ce462a4f4ee554b3f368c43260ab98d05ad5ed22db5799cfa76f6f12303`; SimulatedRegistrationIntegrationTest report SHA-256: `9660e728d941db0db2eb58f0103a41d7f3160abecc7d6f66c56721a78d3ca226`.

## Limits

All RP, user, credential and key inputs are fixed synthetic fixture values. Immutable `CredentialId` encodings and `String` projections remain GC-managed. JCA/provider internals are outside this source-level cleanup proof; `PrivateKey.destroy()` is attempted but provider heap erasure is not asserted. No heap, persistence, runtime/provider or release-candidate claim is made.

### Additional registration integration source — SEC065-CORE-043

A second host registration test, `SimulatedRegistrationIntegrationTest`, had the same success-only lifecycle: generated software private key, request/client-data-hash arrays, mutable RP/user option fields and the returned `MakeCredentialResult` were not guaranteed to be cleared if verification or a test assertion failed. It now clears the result in the verifier's `finally`, clears options/request/hash buffers in an outer `finally`, and attempts to destroy the generated JCA private key. This test uses the same synthetic `https://example.com`/synthetic-user fixtures. Current source compiles and host-test KtLint passes; the full host suite is being rerun against this final edit before runtime evidence is refreshed.

**SEC065-CORE-043 completion update (2026-11-16):** The final formatted `SimulatedRegistrationIntegrationTest` source is included in the full FIDO host suite, which passed 1,286 tests across 169 reports with zero failures/errors/skips. Host-test compilation and KtLint passed. Its current individual XML report hash is in the evidence index; prior pending status above is superseded.
