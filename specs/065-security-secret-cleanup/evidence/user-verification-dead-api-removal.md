# Orphaned platform user-verification API removal

**Finding:** SEC065-UV-002  
**Date:** 2026-11-15  
**Candidate:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

Repository-wide source search found no production caller, dependency-injection binding, or service path for `PlatformUserVerification`, `AndroidPlatformUserVerification`, or `IosPlatformUserVerification`. The active FIDO path is `feature:fido2`'s separate `UserVerificationServiceImpl`. The common interface and Android/iOS implementations were therefore dead duplicate API surface; their direct adapter test was also removed.

A host regression test was added first and failed because `Class.forName` loaded `PlatformUserVerification`. After removal, it verifies that both the common interface and Android implementation are absent. The iOS placeholder source was deleted after source-reference search; iOS native compilation is disabled on this Windows host.

After removal, `:core:security:testAndroidHostTest :core:security:ktlintCheck` succeeds. The current host report set has 69 tests across 13 reports, with zero failures, errors, or skips. This is source/host evidence; it does not test active Android biometric behavior or release heap retention. The active FIDO service's cache and platform behavior remain open under T005/T006/T015/T027.
