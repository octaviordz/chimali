# Matched ASUS feature performance capture — 2026-10-16

## Scope and setup

- Compared the user-designated current feature branch `codex/055-migrate-hdk-draft` with baseline commit `d9f668a7024fa43f699642e4f2fb8ff144d5b1f1` documented in [the T003 capture](results-2026-10-15.md). The user waived Pixel 6a-class equivalence and selected ASUS Zenfone 10; this report does not claim device-class equivalence.
- Device: ASUS Zenfone 10 (`ASUS_AI2302`), Android 15/API 35, `com.chimali`, Gradle optimized `release` APK/AAB. The release APK was built from the restored feature source and installed with `adb install -r`; the app database was preserved. The local synthetic RP is loopback-only and keeps its single ES256 credential/challenge state in memory.
- The RP request required user verification for both registration and assertion. The matched trace used one in-memory ES256 registration and two assertions; the RP returned `verified: true`, `userVerified: true`, counters `0 → 2 → 4`. RP state from earlier setup attempts was cleared by restarting the disposable server before this capture.
- The five-minute Perfetto session used `D:\temp\chimali-t003-baseline-session.pftxt`, the same trace config as the baseline session. Trace: `D:\temp\chimali-feature-asus-session-uv-required-20261016.perfetto-trace`, 110,905,764 bytes, SHA-256 `D0B30447DAFDFD13637A2292923AFC35BE7B85ED7244CD7565D30D461767C9C3`. It contains ten cold launches followed by the required-UV RP registration and two assertions.

## Results

| Metric | T003 baseline | Feature release | Change / result |
|---|---:|---:|---|
| Cold `am start -S -W` WaitTime, median (range) | 356.5 ms (331–405) | 374 ms (355–436) | +17.5 ms; both below 2 s |
| Perfetto cold TTID, median (range) | 351.04 ms (326.59–4,800.75) | 371.89 ms (352.45–419.71) | +20.85 ms; baseline outliers retained |
| Fully drawn TTFD, median (range) | 480.5 ms (445–562) | 489 ms (436–534) | +8.5 ms; both below 2 s |
| Release APK | 62,659,875 B | 62,692,679 B | +32,804 B (+0.052%) |
| Release AAB | 41,231,186 B | 41,258,295 B | +27,109 B (+0.066%); remains below 50 MB |
| Perfetto sampled session RSS maximum | 245.65 MiB | 234.41 MiB | −11.24 MiB |
| Process `VmHWM` | 253.87 MiB | 238.87 MiB | −15.00 MiB |

The feature TTFD series contains ten valid Android `ActivityTaskManager: Fully drawn com.chimali/.MainActivity` events: `453, 513, 507, 503, 436, 534, 480, 466, 466, 498 ms`. The collection file also retains one initial launch attempt without a fully-drawn log event; it was replaced by a valid sample, not treated as a zero or omitted as if measured.

Raw startup inputs are retained at `D:\temp\chimali-feature-asus-startup-uv-required-20261016.csv` (ActivityManager WaitTime) and `D:\temp\chimali-feature-asus-ttfd-uv-required-20261016.csv` (fully-drawn events). The 10 cold TTID rows are also the startup rows returned by the Perfetto query below.

The matched RP registration browser ceremony was 10,383.3 ms. The two successful assertion ceremonies were 6,306.9 ms and 5,570.1 ms; these browser totals include Windows/browser transport and user interaction and are not the app's latency budget. Four app-side GetAssertion operations were observed for the two RP assertions (two CTAP requests per browser ceremony). Their local HID handoff totals/user intervals/adjusted intervals were:

| Total | User interval | Adjusted local handoff | Accepted |
|---:|---:|---:|---|
| 128.174 ms | 0 ms | 128.174 ms | yes |
| 4,229.141 ms | 4,152.928 ms | 76.213 ms | yes |
| 114.509 ms | 0 ms | 114.509 ms | yes |
| 3,307.760 ms | 3,234.487 ms | 73.273 ms | yes |

All four adjusted local handoffs met the strict `<200 ms` target; median was 95.36 ms. This is a small release sample and does not erase previously recorded over-budget debug-build observations. The measurement ends when Android accepts each final Bluetooth HID response report; it does not prove remote-host receipt.

Perfetto recorded four `Fido2.Assertion.DeriveAndSign` slices (mean 18.04 ms, maximum 30.17 ms), one per CTAP GetAssertion operation. The current host regression `GetAssertionUseCaseTest.assertion signs exactly the selected descriptor once without hydrating candidate credentials` exercises three candidates and asserts one sign operation for the selected credential. Combined with the implementation's selection-before-derivation flow, evidence supports zero candidate-key derivations and one selected derivation/sign per CTAP operation; no separate device-side counter was added. The two browser ceremonies each generated two CTAP GetAssertion operations, so the trace count is four rather than two.

The five-minute frame series contained 1,556 app frames, mean 8.39 ms, with 60 frames above 16.67 ms and a maximum observed frame duration of 152.49 ms. These are descriptive frame observations across startup and the full session; no frame-jank classification is claimed from a fixed 16.67 ms threshold on this device.

## Perfetto queries

Executed with Perfetto trace processor v57.2 against the saved trace. Schema was checked before querying `counter`, `counter_track`, `args`, `process`, `android_startups`, and `android_frames`.

```sql
INCLUDE PERFETTO MODULE android.startup.startups;
SELECT ROUND(dur/1e6, 3) AS startup_ms, startup_type
FROM android_startups
WHERE package = 'com.chimali'
ORDER BY ts;
```

```sql
SELECT p.pid, COUNT(c.value) AS samples,
       ROUND(MAX(c.value)/1048576.0, 2) AS max_mib
FROM counter c
JOIN counter_track ct ON c.track_id = ct.id
JOIN args a ON a.arg_set_id = ct.dimension_arg_set_id AND a.key = 'upid'
JOIN process p ON p.upid = a.int_value
WHERE ct.name = 'mem.rss' AND p.name = 'com.chimali'
GROUP BY p.pid
ORDER BY MAX(c.value) DESC;
```

```sql
INCLUDE PERFETTO MODULE android.frames.timeline;
SELECT COUNT(*) AS frame_count,
       SUM(CASE WHEN dur > 16666667 THEN 1 ELSE 0 END) AS over_16_67ms,
       ROUND(AVG(dur)/1e6, 2) AS mean_ms,
       ROUND(MAX(dur)/1e6, 2) AS max_ms
FROM android_frames
WHERE process_name = 'com.chimali';
```

```sql
INCLUDE PERFETTO MODULE slices.with_context;
SELECT name, COUNT(*) AS samples,
       ROUND(AVG(dur)/1e6, 2) AS mean_ms,
       ROUND(MAX(dur)/1e6, 2) AS max_ms
FROM thread_or_process_slice
WHERE name GLOB 'Fido2.Assertion.DeriveAndSign'
GROUP BY name;
```

Trace processor reported `config_write_into_file_no_flush`; the trace still loaded and returned the ten startup rows, RSS counters, frames and derivation slices. The process-stat polling uses the baseline's 100 ms interval. Current startup samples are noisy and modestly slower than baseline medians but remain well within the 2 s budgets; release binary size and sampled memory are slightly higher/lower respectively by the amounts above.

## Validation and remaining scope

- `tools/local-ci.ps1 -NoFix` passed on this feature state in 56.16 seconds: Ktlint, Detekt, release lint, compilation, generic unit tests, and configured Android host suites. The latest host reports contain 628 passing tests and no failures or skips.
- Current release APK SHA-256: `011E6B6341AEF3F2706A71B6F075DFD5FDD025CF49860C387E7DE02E3F8BEF68`. AAB SHA-256: `848215A671616B756D8603E8F44C14D4991739BB9B4F350AD6B3C706E28DEA9B`.
- This matched ASUS comparison supplies the ASUS-waived T066/T071 workload. The user's ASUS designation also applies to T074; no Moto-specific result or Pixel-class equivalence is claimed. Current SC-007 disposition is governed by the later internal/end-to-end measurement definitions and deferral recorded below. No deployment or production activation is claimed.

# Corrected timing-boundary follow-up — 2026-10-16

The earlier feature latency figures in this report were captured before the user-verification exclusion boundary fix and are superseded for app-side latency interpretation. The fixed release was rebuilt and installed on ASUS Zenfone 10 (Android 15/API 35); APK SHA-256 `11D628BBCF23E638221AA27FB95A50F0FF82DC87A063841B7312C08E5607A5AE`, 62,709,063 bytes.

The localhost synthetic RP verified one registration and two required-UV assertions. The completed device logs reported `reports_accepted=true` and `user_window_open=false`. The corrected app-side GetAssertion system values across the two assertions were 128.688, 135.548, 130.876, and 145.671 ms (all below 200 ms). The registration's system interval was 306.194 ms; its 7,774.792 ms user-verification interval was excluded. RP browser ceremony totals include host/browser and user wait and are not app-side latency.

The associated five-minute Perfetto trace is `D:\temp\chimali-t082-corrected-feature-asus-20261016.perfetto-trace`; it records 3,000 primary-process RSS samples, maximum sampled RSS 204.70 MiB and sampled `VmHWM` 261.07 MiB. The trace had one `config_write_into_file_no_flush` warning and 88 Systrace parse warnings; async slices were incomplete for part of the second ceremony, so completed Logcat records are the source for latency values. Full raw results and limitations are in `specs/055-migrate-hdk-draft/implementation-evidence.md`.

The corrected baseline-source build and RP run completed on 2026-10-16; its successful ceremony timings and adjusted CTAP handoffs are recorded in `results-2026-10-15.md` and `specs/055-migrate-hdk-draft/implementation-evidence.md`. On the same ASUS workload, four corrected feature GetAssertion handoffs had a 133.212 ms median versus 113.005 ms baseline, with all eight individual operations under 200 ms. This small sample is descriptive; the trace limitations and browser/host/user timing boundaries remain as stated above. Baseline capture does not establish Pixel-class device equivalence or remote-host report receipt.

## Current SC-007 disposition — 2026-10-16

The later instrumentation separates response construction (`internal_ns`) from local HID handoff (`system_ns`). Three fresh RP-verified registrations recorded MakeCredential internal times of 247.818, 284.752 and 229.253 ms, above the <200ms target. T085 internal processing investigation/optimization and the complete sample series for every implemented FIDO operation are deferred to [the later phase](../../docs/backlog/fido2-internal-performance-enhancements.md). The earlier handoff observations above are not substituted for the new internal metric.

The user accepted the observed ASUS/Windows UX and requested that end-to-end measurement be investigated in [a future session](../../docs/backlog/fido2-end-to-end-latency-measurement-investigation.md). T084 is deferred, with the raw browser totals and measured Android prompt intervals preserved in implementation evidence. Any Windows interaction outside the Android prompt remains in the residual, so those values do not establish system-only delay or post-fingerprint latency. T069/T083 documentation reconciliation is complete while SC-007 remains partial.
