# Public key decoder error cause cleanup

**Date:** 2026-11-15
**Scope:** Invalid Base64 public-key decode failure mapping.

A test-first regression confirmed the decoder's fixed `CryptoError` still retained its provider `Throwable` as `cause`. `PublicKeyDecoder` now returns the same fixed error category/message without attaching the raw cause. The seven-case decoder host suite passes, including valid algorithm decoding and the malformed-input ownership assertion. The full FIDO host suite also passes 1,260 tests across 167 reports with zero failures/errors/skips; FIDO KtLint passes.

This source/host result does not establish provider-internal copies, runtime diagnostic destination behavior, or caller lifetime for successful public-key outputs.

JUnit XML SHA-256: `c3fd0594627f81d50151022335ecba944719ad5dc7afb5a0399ea5ab450b23b7` (`feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.PublicKeyDecoderTest.xml`).
