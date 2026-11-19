# Sensitive diagnostic field and multiword redaction

**Date:** 2026-11-17  
**Finding:** SEC065-LOG-057  
**Requirements:** FR-008/009; NFR-SEC-041; TR-AUTH-031  
**Revision:** Dirty exploratory worktree; not a frozen product candidate

## Finding and correction

`PrivacyLogScrubber` left a natural-language value such as `password is <value>` unchanged, and its key/value pattern stopped at the first whitespace, leaving the rest of a multiword password visible. The existing synthetic test explicitly preserved the first unsafe behavior.

The scrubber now recognizes `:` and `=` fields plus common `is`/`was` prose forms, consumes the full value through the next explicit delimiter or recognized sensitive field, and preserves later delimited fields. The updated fixtures use synthetic values only. This remains a heuristic scrubber; it does not authorize sensitive values in diagnostics or replace source and destination review.

## Verification

Focused command:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*PrivacyLogScrubberTest' --tests '*LocalCrashReportingLogWriterTest'
```

Result: **24 focused tests passed, zero failures/errors** (8 scrubber cases, 12 writer contract cases, and 4 writer rotation/burst cases). This includes natural-language secret values, multiword values, preservation of a later semicolon-delimited field, all configured key labels, and the existing local writer behavior. `:feature:fido2:ktlintCheck` also passed. The complete `:feature:fido2:testAndroidHostTest` suite then passed **1,310 tests across 172 reports**, with zero failures/errors/skips.

The initial focused run exposed that the first matcher revision consumed later structured fields; the matcher was refined to stop at the next recognized sensitive field or explicit delimiter before the passing rerun. No app was installed and no real secret or log payload was used.

## Evidence hashes

- `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubber.kt` SHA-256 `19a7c8f8959b17eb575c5495f68a4dce9a88b67b5c15c4c7e062392908b71c1f`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt` SHA-256 `28550fd366fb6fa91bc59b937404ac8b21ca0e004e1af67c0253b49f90a4b734`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.logging.PrivacyLogScrubberTest.xml` (complete-suite rerun) SHA-256 `42522018947f757a5f190ca07be3c3738c6f1c8c455ba4a4d55ef2fb7fb25e77`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.logging.LocalCrashReportingLogWriterTest$T012WriteContract.xml` SHA-256 `6a735e589e348d7bea7bfd8a466b33de44c7a96efb73b5c0ae3ef361827ddec1`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.logging.LocalCrashReportingLogWriterTest$T013LogBurstAndRotation.xml` SHA-256 `6d81521a720f4b327e09e780c18708e7cbd3188e4ea9621af4e8cdfad6f60216`
- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html` SHA-256 `ec0ae0aad65245ba6f5a91456ff12509f1008332dac64b7e899de80513adc097`

Configured Android destination coverage, iOS behavior, arbitrary unlabelled sensitive text, immutable JVM string retention, complete source reachability and frozen-candidate verification remain open.

## Follow-up: delimiter-like characters inside sensitive values (2026-11-18)

A new regression exposed that a labeled value containing a comma or semicolon could leak its suffix because the prior matcher treated those characters as field delimiters. Since sensitive values may contain punctuation, the scrubber now replaces the sensitive label and the entire remaining message, including embedded newlines. This intentionally drops trailing diagnostic fields on that line rather than trying to infer a safe delimiter. Separate lines continue to be scanned independently. The fix was exercised with a synthetic `password=two words, and more; elapsedMs=12; pin=another-secret` value; no suffix was retained.

Focused verification rerun:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*PrivacyLogScrubberTest' --tests '*LocalCrashReportingLogWriterTest' :feature:fido2:ktlintCheck --no-daemon
```

Result: **24 tests passed, zero failures/errors/skips** (8 scrubber cases, 12 writer-contract cases, 4 rotation/burst cases); FIDO KtLint passed. The first rerun caught an outdated test expectation and then a Kotlin test-helper compile error; both were corrected before the successful run. At the time of this focused rerun, the full FIDO suite had not yet been rerun; the later full-suite result is recorded below.

Current hashes:

- `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubber.kt`: `b5105885eca0fcde0109fe8cfdc44f118837a25a888ca2f6720a5ef6059f7e48`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/util/logging/PrivacyLogScrubberTest.kt`: `108c6cf53a5b89eedc805bbf9c91e4c27f2872dd0073478a3c3a53238977c548`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.logging.PrivacyLogScrubberTest.xml`: `72317713654b59fa85338d84d9c92506060dbb18cbf41c253300f83ab23f5f54`
- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`: `cde7a2220bebf875de982db16cf6b91fa84b7117f800cd8756a667b4a91a4efc`

This closes the delimiter-suffix regression for the shared app writers; arbitrary unlabeled sensitive values, other diagnostic sources/destinations, iOS behavior, immutable String retention and frozen-candidate coverage remain open.
## Full FIDO host-suite follow-up (2026-11-18)

After the focused change and test correction, including a multiline-secret regression, the full `:feature:fido2:testAndroidHostTest` suite and `:feature:fido2:ktlintCheck` passed. The host suite completed **1,332 tests across 172 reports**, with zero failures, errors or skips. The full run regenerated the current FIDO test-report set; the current scrubber report SHA-256 is `72317713654b59fa85338d84d9c92506060dbb18cbf41c253300f83ab23f5f54`, and the full report index SHA-256 is `cde7a2220bebf875de982db16cf6b91fa84b7117f800cd8756a667b4a91a4efc`.

This validates the shared scrubber change against the full FIDO host suite. It does not close the full source/destination diagnostic matrix or other listed platform and frozen-candidate limitations.