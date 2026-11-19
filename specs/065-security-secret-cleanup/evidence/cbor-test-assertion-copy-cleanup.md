# CBOR test assertion-copy cleanup

Date: 2026-11-18  
Scope: `CborCodecTest` synthetic owner assertions

## Correction

The sensitive-decoding regression inspected `SensitiveCredentialText` by calling `copyCharacters()`. Those temporary `CharArray` copies were only checked for zeroes; the test did not erase the inspection buffers themselves. The test now centralizes the inspection in `isErased`, which checks the copy and clears its full contents in `finally`.

## Verification and limitations

`:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.CborCodecTest` passes all 18 tests, and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passes. This is synthetic host-test fixture cleanup; it does not establish production decoder callers, provider/native copies or heap-root release.
