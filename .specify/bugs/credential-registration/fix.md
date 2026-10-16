# Bug Fix: Restore credential crypto provider services

- **Slug**: credential-registration (reused from assessment context)
- **Fixed**: 2026-10-15
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

Added targeted consumer rules for Bouncy Castle's dynamically loaded credential algorithm
mappings and public service constructors. The controlled pre-fix ASUS run confirms all three
KeyFactory services are missing in the optimized target APK.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `feature/fido2/proguard-rules.pro` | Modified | Preserve EC/EdEC/MLDSA mappings and selected service constructors; allow optimization. |
| `app/src/androidTest/kotlin/com/chimali/CredentialCryptoProviderTest.kt` | Added | Test target-provider key conversion, signing, verification, and modified-message rejection for all supported credential algorithms. |

## Tests Added or Updated

- `es256PublicKeyRoundTripAndSignature`
- `ed25519PublicKeyRoundTripAndSignature`
- `mlDsa65PublicKeyRoundTripAndSignature`

The test references only the provider facade, asserts it belongs to the target app class loader,
and discovers algorithm services by JCA name. It never references or keeps mapping/SPI classes.

## Local Verification

The unchanged baseline target APK failed 3/3 checks with `Missing target KeyFactory.EC`,
`Missing target KeyFactory.Ed25519`, and `Missing target KeyFactory.ML-DSA-65`.
The app R8 task was UP-TO-DATE in that run; only the corrected test APK was rebuilt. An earlier
harness attempt incorrectly looked up the obfuscated provider facade by its original name;
that attempt is excluded from root-cause evidence.

Post-fix device, host, static-analysis, and release packaging checks are recorded in `test.md`.

## Deviations from Assessment

The harness uses a direct reference to the already-reachable provider facade instead of looking
it up by its original class name: the latter name is legitimately obfuscated. A class-loader
assertion proves the facade is from the target app. Mapping and service implementation classes
remain unreferenced by the test; the corrected baseline still fails all three discovery checks.
This preserves the assessment's test-isolation intent while supporting R8 name rewriting.

Supplementary standard Spec Kit artifacts were prepared before the dedicated bug skills appeared.
Existing extension installation, local signing changes, and the HDK migration stash are outside
this source fix.

## Follow-ups

The migration uses the platform-default EC KeyFactory for P-256, whereas the baseline explicitly
uses BC. Both implementations explicitly use BC for Ed25519 conversion. Consequently, successful
migrated ES256 browser tests do not rule out the removed Ed25519 services; the earlier EdDSA-first
failure is consistent with this packaging defect.

Repeat an approved browser registration/assertion using the installed release when host and user
interaction are available. The automated provider reproduction establishes the packaging defect;
it does not establish end-to-end Bluetooth behavior or the result of every historical browser attempt.

## Hooks

No before/after bug-fix hooks are registered in `.specify/extensions.yml`.
