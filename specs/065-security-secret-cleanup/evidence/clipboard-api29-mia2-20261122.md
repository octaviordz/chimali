# Owner-approved API 29 Mi A2 clipboard refresh

**Date:** 2026-11-22  
**Device:** Xiaomi Mi A2 (`2ac6876`), Android 10 / API 29, MIUI 11  
**Scope:** Owner explicitly waived the planned API 28 physical cohort and directed use of this available device. This API 29 run is the approved substitute; API 28 is waived, not passed.  
**Build identity:** dirty exploratory worktree at HEAD `49a44aa36e0e86cabd771ffe6c83b079a1a9c4a5`; no frozen candidate. Target APK SHA-256 `B19B50FCCB74671131729F3E5758AD82B856ED7744C9157445E10A345CF0D8ED`; instrumentation APK SHA-256 `814539C631A452C451A81834323D33F9A6E45EA911EBF33C82360B187CAEBD69`.

The current worktree clipboard audit target and test packages were installed on the Mi A2, and the eight-case synthetic procedure ran with separate target and observer processes. The observer returned classifications/metadata only; clipboard text was not read. Positive (`marker-match`) and mismatch (`replaced`) capability controls passed in every completed case. All report artifacts are sanitized JSON in this directory, with expected SHA-256 values listed below. After the run, both `com.chimali.clipboardaudit` and `com.chimali.clipboardaudit.test` were uninstalled; follow-up package-path checks confirmed both absent.

| Case | Run ID | Scoped behavior result | Removal / independent observation |
|---|---|---|---|
| scheduled-attempt | `mia2waiver20261122a` | Passed: `ClearRequestSubmitted` after the 30-second deadline | Passed: observer reported `absent` |
| process-death | `mia2waiver20261122b` | Passed: target process stopped before scheduled execution | Passed: observer matched the synthetic marker; no cleanup after process death is claimed |
| app-switch | `mia2waiver20261122c` | Passed: `SkippedOwnershipUnavailable` while target was backgrounded | Passed: observer matched the synthetic marker; retained entry is explicit |
| clipboard-replaced | `mia2waiver20261122d` | Passed: `SkippedOwnershipUnavailable` rather than clearing a different entry | Passed: independent observer confirmed `replaced` |
| manual-clear | `mia2waiver20261122e` | Passed: `ManualClearSubmitted` | Passed: observer reported `absent` |
| repeat-copy | `mia2waiver20261122f` | Passed: first generation was superseded | Passed: scheduled cleanup was independently observed as `absent` |
| copy-rejected | `mia2waiver20261122g` | Passed: copy admission rejected while unconfirmed work was pending | Passed: retained synthetic clip was observed |
| locked-sleep | `mia2waiver20261122h` | Passed: at the 30-second deadline the app returned `SkippedOwnershipUnavailable` while secure ownership metadata was unavailable | Unverified: MIUI secure keyguard remained after wake (`SecureKeyguardRemained`), so no independent post-wake removal observation was possible; no removal claim is made |

All eight rows demonstrate their scoped current-worktree API 29 behaviors. For locked/sleep, the safe skip behavior is Passed while the independent removal observation remains Unverified, as required by the best-effort contract. Preview presentation was exercised separately below; its sampled non-exposure is bounded and does not distinguish masking from omission. These dirty-worktree results do not bind to a release candidate or constitute independent review.

## Current-worktree preview probe attempts

Run `mia2preview20261122` stopped at `observer-wait-control-match-result` with `ObserverResultUnavailable`; no target-ready receipt was produced and no synthetic copy was launched. Read-only device state showed the Mi A2 awake but `mInputRestricted=true`, consistent with its secure lock screen preventing the observer activity from reaching its ready barrier. The report records `cleanupSucceeded=true`. No screenshot was retained and no clipboard content was read or changed. The sanitized report SHA-256 is `1C02B0F00503544120F9D73561BD0B9ACC9C85E04902FA3AFAED5E873959585D`. The preview observation remains Unverified until the device is interactively unlocked for the controlled run.

After the owner unlocked the device, run `mia2preview20261122b` passed its scheduled-attempt case: target publication was `MetadataConfirmed`, the observer positive control returned `marker-match` with `sensitiveFlag=true`, the target submitted its clear request after the 30-second deadline, and the independent observer classified the clip as `absent`. Twelve screenshots were sampled at approximately 250 ms intervals; the representative screen frame showed no clipboard text. All 12 window polls reported the MIUI `AssistPreviewPanel` window, while screenshots showed no visible text. This is bounded non-exposure evidence; the capture does not establish whether the OEM masked or omitted preview content. Report SHA-256: `C2C4208C1EE2A0A4473D11FC02E0D1E0B63A5292A1F53CAF7C5F5825EC0D0DC8`; frame SHA-256: `9B45528B204BB5FC3D8C24B8482F0661A0F21066FCFD6C3F1F1BB50A13F76E22`; window-poll SHA-256: `32CA4B1631B5667E91681DD46BD6F4D3B618ABA00055F41D47DBB5ECEA586F86`.

## Current-worktree locked/sleep run and cleanup recovery

Run `mia2sleepunlock20261122` published a metadata-confirmed synthetic value and reached its exact 30-second deadline. The app returned `SkippedOwnershipUnavailable`; this is a Passed scoped behavior result because the app safely skipped when ownership could not be confirmed. After wake the secure keyguard remained; a 90-second manual-unlock window elapsed without an unlock, so independent removal observation is Unverified. The report SHA-256 is `409EF19A5F3B2680BD12D259E78D3036B4C7A7DFE349D262B8F57D1664B8DD8C`.

Once the device was later unlocked, a new controlled synthetic scheduled-attempt run (`mia2sleepcleanup20261122a`) returned `ClearRequestSubmitted`; the observer independently classified the new clip as `absent`. This left the test clipboard empty without reading its text. Both audit APKs were then uninstalled and verified absent. Cleanup report SHA-256: `222AFD34281296AD6D23669BC478877F023E0D3945A99557E6C5E5EF179B37AA`. This recovery run is separate from the locked/sleep removal observation and does not replace its Unverified status.

## Artifact digests

| Run ID | Sanitized report SHA-256 |
|---|---|
| `mia2waiver20261122a` | `F8FEE8D44974D103DF71CDE47D927779297B8D061D9F49679F69B168BF5FEF59` |
| `mia2waiver20261122b` | `9AB6CC0E9C9E589534217E02902433CFF8E0010CB5F1FCA7AE7AEC4D8F6B6F74` |
| `mia2waiver20261122c` | `A207E355B3ABC1366F879398F7321BF52B1D89F01E0627A26A63E780B4599DB0` |
| `mia2waiver20261122d` | `1D9CE46D22BFCD1B812E03057EA3CCA89000E1744E06BE40AAE98E32669BDC79` |
| `mia2waiver20261122e` | `CCC419C4E4063E35597993E6B77535159488D7EB0F1FE0F500D084F6EB4D55A1` |
| `mia2waiver20261122f` | `0B3D94F6E53E42B0277CBEF087CC91075CCB46C7B4088776BAF464143DCCA140` |
| `mia2waiver20261122g` | `8B4DECDDAC726723E551E61D5A7D319E42F5DB13FDCB5E8A0221282D12DBDEB5` |
| `mia2waiver20261122h` | `FA07DAA87DBCDC0F44082416FF7BAEF4498BBEF7585E64DB7C96CEAAAF733CC6` |
