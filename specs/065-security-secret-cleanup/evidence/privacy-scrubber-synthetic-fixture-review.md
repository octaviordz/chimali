# FIDO privacy scrubber synthetic fixture review

**Finding**: SEC065-LOG-054  
**Date**: 2026-11-16  
**Scope**: FIDO diagnostic scrubbing and its host-test fixtures.

`PrivacyLogScrubber` replaces heuristic mnemonic-shaped text, long key-shaped strings, and labeled sensitive values before logging. Its immutable input/output lifetime remains caller/JVM-managed and platform sink behavior is assessed separately.

`PrivacyLogScrubberTest` previously embedded the canonical public BIP-39 `abandon ... art` phrase as its 12- and 24-word fixtures, plus a private-key-shaped hex value. Those fixtures are now repeated `mockword` markers and `deadbeef` repeated eight times. They continue matching the scrubber patterns without using a known valid wallet phrase or a crypto-generated key.

Verification: the normal FIDO host suite and KtLint passed: 1,276 tests across 168 reports, zero failures/errors/skips. `PrivacyLogScrubberTest` report: 7 tests, zero failures/errors/skips; XML SHA-256 `f4d205ea1f3099d13b9e3a3ca1712432cfe916735cd840f1a07fb20bc7a3901e`. The initial filtered Gradle invocation found no matching tests; the full configured host suite ran successfully afterward.

The markers remain immutable synthetic strings in the test methods and are not persisted or logged. No claim is made about erasing JVM String objects or validating configured runtime log destinations.
