# Bug Assessment: Credential crypto missing in optimized builds

- **Slug**: credential-registration
- **Created**: 2026-10-15
- **Source**: User report and retrieved “Implement HDK draft migration” chat
- **Verdict**: valid
- **Severity**: high
- **Branch**: bug/056-credential-registration

## Report (verbatim or summarized)

User: “found a bug when the application tries to register a credential.” The referenced chat
records “Generated key not found in KeyStore” on baseline and “Credential cryptographic operation
failed” after migration. Registration fails after approval; a successful credential is expected.
No user-supplied URL was fetched.

## Symptom

Optimized releases cannot reconstruct credential public keys when the bundled crypto provider's
reflectively discovered implementations have been removed. Unshrunk host/device checks can pass.

## Reproduction

1. Start from `8b4d85ad` with release minification enabled and bcprov-jdk18on 1.80.
2. Build/run the app's optimized `shrunkDebug` instrumentation variant on ASUS AI2302/API 35.
3. Load the BC provider through the target APK class loader and request KeyFactory services for
   EC, Ed25519, and ML-DSA-65; attempt key conversion and signing.
4. Compare the same checks before and after the targeted consumer-rules fix.

External browser registration is a separate confirmation: it additionally requires a connected
host and user approval. This assessment does not claim that ceremony has been repeated.

## Suspected Code Paths

- `feature/fido2/proguard-rules.pro`: incorrectly assumes BC's consumer rules preserve its services.
- `Fido2CryptoService.getPublicKey`: catches GeneralSecurityException from KeyFactory conversion
  and returns null in the baseline.
- `RegisterCredentialUseCase.generateCredential`: labels that null result as a missing KeyStore key.
- Stashed migration `Fido2CryptoService.decodePublicKey`: still depends on BC for Ed25519 decoding.
- `app/build/outputs/mapping/release/usage.txt`: EC/EdEC mappings and KeyFactory SPIs removed.

## Root Cause Hypothesis

Confidence: high. R8 sees no direct references to dynamically constructed BC mapping and service
names. The merged release configuration has warning suppression but no reflection keeps. Provider
construction succeeds while services are absent. Passing a provider instance cannot restore
removed implementations. A controlled target-APK regression will confirm the runtime effect.

## Proposed Remediation

**Preferred**: preserve EC, EdEC, and MLDSA mapping classes plus the public constructors of the
credential service implementations loaded by name, in the FIDO2 library's consumer rules. Keep
optimization enabled and preserve implementation dependencies through normal reachability.

**Alternatives**: disabling minification or retaining the entire BC library has unnecessary size
and optimization costs. Changing key derivation is unrelated and risks credential incompatibility.

**Files likely to change**:

- `feature/fido2/proguard-rules.pro`
- `app/src/androidTest/kotlin/com/chimali/CredentialCryptoProviderTest.kt`
- Bug reports and supplementary design/validation records under `specs/056-credential-registration/`.

**Tests to add or update**:

- ES256, Ed25519, ML-DSA-65 target-provider public-key round trips and signatures.
- Reject modified messages with each generated credential public key.
- Use the target class loader without BC imports so test code cannot retain the missing classes.

## Risks & Considerations

- Measure release bundle growth against 50 MB total and 500 KB change thresholds.
- Existing signing edit and HDK migration stash are separate work and must be preserved.
- No credential/schema/derivation changes or secret diagnostic logging are needed.
- The runtime provider checks diagnose packaging; they do not prove every previous browser error
  has the same cause or establish performance timing.

## Open Questions

No clarification blocks the focused packaging repair. Browser confirmation remains separate.

## Workflow

Slug generated from the established branch and task context. Dedicated bug skills became available
after the initial standard Spec Kit setup. No before/after bug hooks are registered in extensions.yml.
