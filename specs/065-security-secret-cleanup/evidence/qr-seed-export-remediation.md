# Seed QR export remediation

- Date: 2026-11-22
- Worktree identity: dirty exploratory worktree; no candidate commit/tree declared.
- Change: removed the full-mnemonic `String` QR painter path from `DevelopmentToolsScreen`; retained display, copy, recovery and clear actions; added explicit disabled-export wording.
- Regression: `seedQrExportIsDisabledAndClearRemainsAvailable` asserts the disabled explanation is visible and both QR control and QR image semantics are absent while copy/clear actions remain.
- Verification: `:feature:fido2:compileAndroidDeviceTest` and `:feature:fido2:ktlintCheck` passed. The device test has not been run.
- Limitation: only ASUS API 35 is attached; the prior physical test authorization specified Mi A2 API 29. This source/test compilation is not behavioral device evidence or release-candidate evidence.
- Disposition: The disabled path is behaviorally verified on an emulator for T108. The evidence remains exploratory and is not bound to a refreshed candidate.
