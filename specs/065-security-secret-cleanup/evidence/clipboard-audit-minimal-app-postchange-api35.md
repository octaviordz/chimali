# Clipboard audit startup graph regression check

**Requirement:** T005/T027/T034; FR-006/007/008/012/014  
**Device:** API 35 Android ATD x86_64 emulator `emulator-5554`  
**Run:** `codex20261117_api35_postisolation01`  
**Revision:** Dirty exploratory worktree; not a frozen candidate.

After `clipboardAudit` began using `ClipboardAuditApplication`, a synthetic scheduled clipboard attempt verified that its separate copy-activity process still resolves the minimal Koin clipboard module. The target published with `MetadataConfirmed` and sensitive metadata, scheduled exactly 30,000 ms after publication, and returned `ClearRequestSubmitted` 114 ms after the recorded deadline. The independent observer, running in a separate process, classified the synthetic entry as absent 1,892 ms after terminal status. Positive and mismatch controls returned `marker-match` and `replaced`; device was focused and unlocked. The observer did not read clipboard text. `cleanupSucceeded=true`; both disposable packages were uninstalled afterward.

Sanitized JSON SHA-256: `04dc7980e1312bca11fb0c3ffb33c8fd7e3f9d97a869a9ebe725178624f140c6`. Target APK SHA-256: `7b17e547cd04eb4edcd1b83cb64dc1ab9cf0aa7d978b7b9c3987cc61eedb0b87`. Test APK SHA-256: `a76b7a65efa74e89b37ff1b28d1b2b0f30adc370ecfa1dbb858667bcbf664911`.

This is a harness regression check on one API 35 emulator. It does not satisfy the API 35+ **physical** cohort, nor does it certify other OEMs, lock/sleep behavior, process-death, preview/TalkBack behavior or the frozen product candidate.
