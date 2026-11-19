# User-verification availability cache uses monotonic time

**Finding:** SEC065-UV-003  
**Date:** 2026-11-15  
**Candidate:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`UserVerificationServiceImpl` cached biometric/device-credential availability for 10 seconds using `TimeProvider.epochMillis()`. Wall-clock rollback made `now - cacheTimestampMs` negative, satisfying the “less than 10 seconds” check and potentially extending the cached device-posture result until wall time caught up. The implementation now measures the cache TTL with `SystemClock.elapsedRealtime()`. Wall-clock `TimeProvider` remains in use for the user-visible enrollment timestamp.

A deterministic host regression injects monotonic time, supplies wall-clock samples that move backwards, confirms cache reuse before 10 seconds, and confirms a fresh platform query at the 10-second expiry. The targeted test reports 1 pass. The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run then passes 1,245 tests across 166 XML reports with zero failures/errors/skips.

No production caller of `invalidateAvailabilityCache()` was found. Availability metadata is immutable and contains booleans, fixed policy values, type labels and a timestamp; no PIN, biometric sample, persistence or logging is present in the reviewed service. The service is process-scoped by its Koin `@Single` binding. Runtime Android state changes, UI/caller retention and heap roots remain unverified.


