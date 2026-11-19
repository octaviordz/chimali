# Secure random platform failure cleanup

**Date:** 2026-11-16  
**Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`  
**Scope:** Android/iOS `generateSecureRandomBytes` actuals used by `CredentialId.generate`.

`SecureRandom.ios.kt` previously ignored the result from `SecRandomCopyBytes` and returned the allocated array unconditionally. Apple documents that the function returns `errSecSuccess` or another result on failure, and instructs callers to check the result before using the buffer ([Apple `SecRandomCopyBytes`](https://developer.apple.com/documentation/security/secrandomcopybytes%28_%3A_%3A_%3A%29?language=objc)).

Both platform actuals now use the common `fillSecureRandomBytes` boundary. It returns the caller-owned buffer only after the fill callback reports success. A nonzero status or thrown provider exception clears the buffer and yields a fixed cause-free `IllegalStateException`; fatal errors still propagate through `finally` after the bytes are erased. `CredentialId.generate` separately clears the successful random buffer after encoding.

The initial synthetic status regression failed before the helper handled nonzero status. The three-case `SecureRandomStatusTest` now covers partial bytes followed by failure status, a thrown provider detail, and successful buffer identity/preservation. The full `:core:domain:testAndroidHostTest` suite passes **99 tests across 20 XML reports** with zero failures, errors, or skips. `:core:domain:ktlintCheck` passes.

The common host tests exercise the shared failure boundary, not Apple Security or Android provider behavior. Kotlin/Native compilation was disabled on this Windows host, and no iOS device/runtime test was run. Those platform-specific checks and any provider-internal copies remain open.
