# Follow up on material cold FIDO2 settings lookup latency

**Status:** Open follow-up from Feature 062.

## Finding

Feature 062's comparable ASUS_AI2302/API 35 groups measured a pre-change cold median of 183.979 ms and an after-change cold median of 186.929 ms. The cold group remains material under the investigation's 20 ms rule at both sensitivity scales. The after-change raw median is 1.60% above the pre-change median. This is a device/workload diagnostic, not a project acceptance threshold or a population claim.

Profile 04 attributes most of one observed settings lookup to `EncryptedSharedPreferences.create()`. T016 caches the encrypted-preferences handle for subsequent repository reads; it does not remove the first-use cold initialization cost. The available evidence does not establish which subcomponent causes the whole cold interval or whether another change would improve user-visible registration.

## Next work

1. Review the retained Profile 03/04 component evidence and identify whether the first-use encrypted-preferences creation can be decomposed safely without changing migration, settings, credential-limit, security, or failure behavior.
2. Decide whether further cold-start profiling is worth its cost before proposing a production change.
3. If a change is justified, predeclare a comparable cold-path capture and verification plan. Keep the 20 ms diagnostic classification, calibration identity, eligibility, and uncertainty limitations explicit.
4. Preserve all failed and inconclusive attempts. Do not claim a population effect from the current ten-sample groups.

## Evidence

- [Feature 062 T017 comparison](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-13-t017-comparison.md)
- [Profile 04 split report](../../specs/062-fido2-performance-investigation/artifacts/f062-settings-lookup-profile-04-report.md)
- [Feature 062 implementation evidence](../../specs/062-fido2-performance-investigation/implementation-evidence.md)
- Historical findings and provenance from the original backlog are preserved in [Feature 062 implementation evidence](../../specs/062-fido2-performance-investigation/implementation-evidence.md), §11.1.

The separate [warm registration reliability investigation](fido2-warm-registration-security-key-failure.md) remains independent.
