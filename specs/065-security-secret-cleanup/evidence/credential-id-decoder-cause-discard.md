# SEC065-ERR-021 — Credential ID decoder cause discard

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`CredentialId.decodeAndValidate` converted malformed Base64URL input to a fixed `IllegalArgumentException` message but attached the platform decoder exception as its cause. Retaining that cause was unnecessary and could expose decoder details or preserve an input-related throwable graph at downstream formatting boundaries.

The new `CredentialIdTest.malformed encoded credential identifier does not retain decoder failure` regression used a synthetic invalid-input marker and required the fixed public message, a null cause, and marker-free diagnostic representation. It failed before correction because the cause was non-null. The decoder now discards the caught `IllegalArgumentException` and emits only the fixed validation message; malformed input continues to fail closed.

Verification: `:core:domain:allTests :core:domain:ktlintCheck` passes. The Android host report set contains 93 tests across 16 reports with zero failures, errors or skips. The red-before result is the preceding `:core:domain:allTests` run: 92 tests completed and the new cause-absence assertion failed.

Limits: common-domain host/source evidence only. This does not prove downstream exception formatting, native/provider behavior, or immutable encoded credential-ID lifetime. The explicit encoded property and storage/protocol behavior are unchanged. Full source-owner, destination and heap review remain open under T005/T006/T012/T015/T029/T033/T035.
