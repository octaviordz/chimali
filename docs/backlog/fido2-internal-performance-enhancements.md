# Backlog: FIDO2 Internal Performance Enhancements

**Status**: Open — all performance enhancements deferred to a later phase by the user  
**Created**: 2026-10-16  
**Origin**: Feature 055, SC-007 / T085  
**Related investigation**: [End-to-end latency measurement](fido2-end-to-end-latency-measurement-investigation.md) (T084, also deferred)  
**Task and evidence**: [Feature tasks](../../specs/055-migrate-hdk-draft/tasks.md), [implementation evidence](../../specs/055-migrate-hdk-draft/implementation-evidence.md)

## Decision and scope

The user requested that all performance enhancements be paused for a later phase. This includes internal processing optimization, additional profiling and the remaining per-operation sample campaign under T085. The user had already accepted the observed ASUS/Windows UX and deferred end-to-end measurement investigation under T084.

The targets remain documented as <200ms for all internal FIDO operations and <1s for end-to-end response under their defined boundaries. Deferral records outstanding work; it does not claim numeric compliance or remove the targets. Candidate listing must continue to perform no key derivation, and assertions must derive only the selected credential.

## Work preserved at deferral

- Existing instrumentation reports `internal_ns` separately from local Bluetooth response handoff and applies the internal budget to every dispatched CTAP command.
- Three earlier correlated registration samples had internal MakeCredential times of 247.818, 284.752 and 229.253 ms. Discovery/pre-registration probe timings are not independent RP-verified authentication ceremonies.
- Added fixed, secret-free async trace markers around registration stages and wallet initialization, plus requirement annotations, KDoc and a named internal-budget constant for timing helpers. These diagnostic changes are retained. No settings lookup optimization has been applied.
- The focused registration, wallet-owner and timing suites passed 47 tests, with zero failures/errors. FIDO2 Ktlint and Detekt passed after correcting a line-length finding. The optimized release built successfully and was installed as `com.chimali` on ASUS Zenfone 10 (Android 15/API 35), preserving app data. APK SHA-256: `3E7FA9EDBDEB252F2DC17980C159981FEEDD2FCAB75BE3712813D5538606FA27`.
- One instrumented registration verified at the local synthetic ES256 RP. The device recorded MakeCredential `internal_ns=239385208` (239.385 ms), measured prompt time 3,146.372 ms and adjusted local handoff 290.852 ms. All response reports were accepted and the user window was closed. No end-to-end sample was recorded for this run because T084 was already deferred.

## Profiling lead for the future phase

Trace: `D:\temp\chimali-t085-registration-before-20261016.perfetto-trace` (16,682,733 bytes; SHA-256 `B542FD55BC54EA7DC92D34E0E002CC96F01F64D730740C2885AF84B17FA1AEA8`). The capture used the official local Perfetto recorder with scheduling, frequency, activity/view events and app trace markers. On stopping the host recorder, the trace was flushed on the device and pulled again after the file finished writing. Kernel symbols were unavailable; named application slices were readable.

| Registration stage | Duration |
| --- | ---: |
| Relying-party update | 12.813 ms |
| Verification-policy lookup | 0.242 ms |
| Credential validation | 4.519 ms |
| Credential statistics | 2.357 ms |
| Gap between statistics completion and public-key generation | 134.339 ms |
| Public-key generation | 10.948 ms |
| Attestation | 14.006 ms |
| Credential persistence | 24.364 ms |
| Keepalive shutdown | 0.560 ms |

The only production call between the statistics and public-key trace sections is `settingsRepository.getMaxCredentialCount()`. Its implementation in `Fido2SettingsRepositoryImpl.kt` reads Proto DataStore and then opens legacy EncryptedSharedPreferences on every call to check for an old setting. The 134.339ms gap includes the call's scheduling and execution; the trace does not separately attribute its components. Repeated legacy preference/Keystore initialization is a concrete optimization candidate, not a measured cryptographic bottleneck.

A later phase should add a direct marker around this lookup, confirm its cost and remove the obsolete migration path under the agreed clean-install scope. Keep current Proto DataStore values, default behavior, storage-limit enforcement and unrelated preference fields intact. Replace the old unconfigured `src/androidTest/.../Fido2SettingsMigrationTest.kt` with appropriate configured tests of current settings behavior when that change is implemented; no test changes were made before deferral.

## Reproducible analysis

Perfetto trace processor v57.2 was used. Windows background-session creation/discovery was unavailable, so queries ran directly against the saved trace. The schema was inspected first with `SELECT * FROM thread_or_process_slice LIMIT 0` after loading `slices.with_context`.

```sql
INCLUDE PERFETTO MODULE slices.with_context;
SELECT name, ts, dur, ROUND(dur / 1e6, 3) AS ms, thread_name, track_id
FROM thread_or_process_slice
WHERE process_name = 'com.chimali'
  AND (name GLOB 'Fido2.*' OR name GLOB 'T003.*')
  AND name != 'Fido2.HID.SendReport'
ORDER BY ts;
```

The statistics slice starts at 297504033965756ns and lasts 2356928ns; the public-key slice starts at 297504170661954ns. Their intervening interval is 134339270ns. This is a single profiled sample, not a distribution or proof of the cost on every request.

## Completion criteria for the later phase

1. Confirm the settings-lookup attribution and implement the smallest supported optimization with meaningful configured regressions. Preserve authorization, persistence ordering, cancellation, secret cleanup and storage-limit behavior.
2. Profile any remaining over-budget stage before changing it. Include every implemented internal FIDO operation; do not restrict the target to MakeCredential/GetAssertion.
3. Capture at least 10 samples per implemented operation on the designated ASUS workload, preserving warm-up, cold-start, failures and outliers. Distinguish successful operations from discovery or error/probe responses.
4. Demonstrate the strict <200ms internal target with measured user wait removed and Bluetooth response handoff excluded; retain raw timing, process/session/operation identity, build identity, trace warnings and workload details.
5. Preserve zero candidate derivations and one selected assertion derivation, and reconcile T085/SC-007 and current documentation with actual results.

Resume the separate end-to-end investigation only in the phase authorized for that backlog item.
