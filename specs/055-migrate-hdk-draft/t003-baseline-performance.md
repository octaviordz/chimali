# T003 baseline performance evidence — historical capture and follow-up

The original 2026-10-15 capture below was paused and used a reconstructed
`8b4d85ad` baseline that failed registration. On 2026-10-16, the user designated
`codex/t003-baseline-instrumentation` as the baseline and resumed T003. Its
corrected release/RP capture succeeded; the authoritative result, build identity,
coverage findings, and matched latency comparison are recorded in
[`tools/t003-baseline/results-2026-10-15.md`](../../tools/t003-baseline/results-2026-10-15.md)
and [`implementation-evidence.md`](implementation-evidence.md). T003 is now
checked in `tasks.md`. The historical `8b4d85ad` failure and measurements below
remain diagnostics, not the source for the corrected comparison.

## Baseline identity and collection order

The baseline was reconstructed after feature implementation from commit
`8b4d85ad66948a8a7c83683e34cb2507c94a4561`. T003 should have preceded implementation;
this retrospective capture does not establish a contemporaneous pre-change device state.
The target APK is the pre-feature production `release` build, locally signed with
the existing debug signing key for device installation. Package: `com.chimali`,
version 0.1.0, non-debuggable, R8/resource shrinking enabled.

- APK: `D:/temp/chimali-baseline-release-signed.apk`, 62,440,630 bytes,
  SHA-256 `DB6CF22B7FE701373713B3735CC0C03DD449EC4EBAAF52383982308A1E759A99`.
- Original baseline AAB:
  `C:/Users/octav/.codex/worktrees/hdk-baseline/Chimali/app/build/outputs/bundle/release/app-release.aab`,
  40,984,305 bytes, SHA-256
  `30B162015AD65FC33D10D33851998E3AA42F76667DD488C5CD6E12A0A0BF3684`.
- Rebuilt AAB at
  `D:/temp/chimali-baseline-8b4d85ad/app/build/outputs/bundle/release/app-release.aab`
  has the same byte length and SHA-256
  `D1BB7B42BB8E43525DF4E4FC369CFC4831D80243E12A83A055F93CC39020D4AC`.
  These are distinct artifacts; their hashes must not be interchanged.
- The temporary source checkout is at the named baseline commit. Its local changes
  add coverage/LeakCanary only to `shrunkDebug`; inspection found no release
  dependency or release build-type change in that diff.

## Device and workload

Moto G Stylus 5G (2022), serial `ZY22G3FJSX`, Android 13. Fingerprint:
`motorola/milanf_g/milanf:13/T2SDS33.75-38-1-3-30/819dd2-ff9246:user/release-keys`.
`/proc/meminfo` reported 7,674,656 KiB total RAM; thermal status was 0 before
startup collection. The user selected Moto as the named alternative; no Pixel 6a
hardware-equivalence claim has been established.

The ordinary package was absent before installation. Cleared only the newly
installed baseline package and granted Bluetooth advertise/connect/scan and
notification permissions. The current test package `com.chimali.shrunk` was
force-stopped for baseline collection; its data was preserved. ASUS's disposable
authenticator process was not running.

## Startup and rendering observations

First-run welcome screen, portrait, USB connected and awake. Ten launches per
mode, with 1.5-second settling intervals; hot launches additionally used Home
and a 500 ms interval. Cold uses `am start -S -W`; warm recreates the task with
`am start -W -f 0x10008000`; hot uses Home followed by `am start -W`.
All target `com.chimali/com.chimali.MainActivity`.

| Activity Manager launch mode | Samples | WaitTime median | Range |
| --- | ---: | ---: | ---: |
| COLD | 10 | 239.5 ms | 233–410 ms |
| WARM | 10 | 103 ms | 95–126 ms |
| HOT | 10 | 54 ms | 49–59 ms |

Raw rows: `D:/temp/chimali-t003-baseline-startup-samples.csv` and
`D:/temp/chimali-t003-baseline-warm-samples.csv`. The collection loop is preserved
at `D:/temp/chimali-t003-measure-startup.ps1`.

The 90-second startup trace links seven cold launches to initial-display frames:
265.692, 226.650, 222.684, 230.652, 231.487, 238.780, 221.447 ms.
Three startup rows have unknown type and incorrectly link to a later process's
first frame; exclude them from first-display statistics. Ten hot initial-display
durations are 49.793, 49.620, 39.687, 47.450, 46.739, 49.792, 46.573,
44.259, 39.475, 47.964 ms. The warm trace has no `android_startups` rows;
its Activity Manager results are not a Perfetto first-frame measurement.

Startup trace: 43 app frame rows, 28 with jank classification, maximum frame
duration 87.651 ms. Warm trace: 21 frame rows, all classified as janky, maximum
53.106 ms. These are first-run launch/task-recreation frames, not a steady-state
60 FPS interaction benchmark. Baseline startup jank is present; do not claim a
smoothness pass from the launch-time results.

## Baseline Bluetooth registration failure

Enabled only Passkey Authenticator during baseline onboarding. Used the unchanged
disposable ES256 RP harness on `http://localhost:4176/`, with synthetic in-memory
account state and `userVerification=discouraged`. Started the authenticator and
allowed the Android 120-second Bluetooth visibility prompt. The Moto showed a
connection to the paired Windows PC.

The first registration reached the phone and displayed:

> Registration failed — Could not perform cryptographic operation. Generated key
> not found in KeyStore.

The user independently confirmed the phone's registration failure. Windows
WebAuthN event 2225 at 13:06:23.075 and 13:06:28.522 recorded 166-byte CBOR
requests containing `localhost`, followed by KEEPALIVE replies. Init and GetInfo
responses were also recorded. Raw challenge/request payloads and the synthetic
credential identifier are omitted. No registration was verified at the RP.
The browser call remained pending and was canceled by reloading the test page.

The baseline `RegisterCredentialUseCase.kt:275` emits this text when
`getPublicKey()` returns null. The baseline crypto adapter catches
`GeneralSecurityException` and returns null. Therefore the displayed wording
does not by itself prove an actual Android Keystore lookup failure or expose
the underlying provider exception. No baseline code was changed to make it pass.

Successful Bluetooth authentication latency is **unavailable because baseline
registration failed**. Error-response or KEEPALIVE timing is not successful
authentication timing. A subsequent short capture entered advertising without
a confirmed host connection; it did not reproduce the credential-creation
failure or produce a verified registration.

## Memory observations and trace quality

First session: PID 10622, portrait, cold launch through authenticator startup,
connected registration failure and pending host retries. Its five-minute trace
contains 3,830 RSS observations, maximum 197.258 MiB and maximum swap 22.996 MiB.
The trace ended about six seconds before the final memory snapshot/process stop.
Trace Processor reports five negative timestamps dropped and one
`config_write_into_file_no_flush` warning. Retain these limitations.

At session end, `/proc/10622/status` reported VmHWM 202,196 KiB and VmRSS
182,828 KiB. A final `dumpsys meminfo` snapshot reported PSS 112,308 KiB and
RSS 215,604 KiB; an earlier failed-registration snapshot was PSS 133,719 KiB /
RSS 236,156 KiB. These sources use different accounting and must not be merged
into one precise peak. There is no continuously sampled PSS peak or successful
authentication-session memory result.

A second 120-second memory-only trace added periodic flushes and removed kernel
RSS events. It has zero positive error/data-loss statistics. It covers the
launch and stop of PID 13685, with 970 RSS observations from
`ts=2065849696232560` through `2065946595948617`; trace bounds are
`2065839996723709`–`2065959595781737`. Observed RSS range: 59.469–171.117 MiB;
maximum swap: 22.063 MiB. The device rotated to landscape for this repeat.
The app advertised without a confirmed host connection, so its lower peak
does not replace the first attempt's credential-processing observation.
Before stop, VmHWM was 177,812 KiB; final PSS/RSS were 111,183/211,492 KiB.
Raw process start/end timestamps are null in the process table; shell launch,
PID observations and force-stop metadata provide the lifecycle boundary.

End metadata and snapshots are preserved in `D:/temp/chimali-t003-baseline-session-end*`
and `D:/temp/chimali-t003-baseline-memory-complete-*`.

## Trace inventory

| File in `D:/temp/` | Bytes | SHA-256 |
| --- | ---: | --- |
| `chimali-t003-baseline-startup.perfetto-trace` | 61,475,383 | `9371DAB0677491C88E2EB085ABD3C2ED03A35FC53BE215055655DB2E01167D15` |
| `chimali-t003-baseline-warm.perfetto-trace` | 23,855,810 | `56E294A38CE238CE8CC284D2DBF8FF4F6628393052E1FCE862DD3013AC5061D1` |
| `chimali-t003-baseline-session.perfetto-trace` | 93,070,725 | `5E381E8AD25B4D4A9F93038CEC0AC63772AC3681B350417B3527C6512093E875` |
| `chimali-t003-baseline-memory-complete.perfetto-trace` | 12,322,984 | `0528D2221B35271B06AB1C4E49EC81B866745906F1D2BE314D555C3BB7E33DC5` |

Configs with matching stems are retained as `.pftxt` files in `D:/temp/`.
Use the Android profiler skill's pinned Trace Processor wrapper (v57.2) and
official `record_android_trace.py` helper. Queries used:

```sql
INCLUDE PERFETTO MODULE android.startup.time_to_display;
SELECT s.startup_id, s.ts, s.startup_type, t.upid,
       t.time_to_initial_display / 1e6 AS initial_display_ms, t.ttid_frame_id
FROM android_startups s
LEFT JOIN android_startup_time_to_display t USING (startup_id)
WHERE s.package = 'com.chimali'
ORDER BY s.ts;
```

```sql
INCLUDE PERFETTO MODULE android.frames.timeline;
SELECT f.upid, COUNT(*) AS frame_count,
       SUM(a.jank_type != 'None') AS janky_frames,
       MAX(f.dur) / 1e6 AS max_frame_ms
FROM android_frames f
JOIN actual_frame_timeline_slice a ON a.id = f.actual_frame_timeline_id
WHERE f.process_name = 'com.chimali'
GROUP BY f.upid;
```

```sql
INCLUDE PERFETTO MODULE linux.memory.process;
SELECT m.upid, m.pid, COUNT(*) AS observations,
       MIN(m.ts) AS first_ts, MAX(m.ts) AS last_ts,
       MIN(m.rss) / 1048576.0 AS min_rss_mib,
       MAX(m.rss) / 1048576.0 AS max_rss_mib,
       MAX(m.swap) / 1048576.0 AS max_swap_mib
FROM memory_rss_and_swap_per_process m
WHERE m.process_name = 'com.chimali'
GROUP BY m.upid, m.pid;
```

```sql
SELECT s.name, s.severity, s.value
FROM stats s
WHERE s.value > 0 AND s.severity IN ('error', 'data_loss');
```

## Coverage availability and resume state

The baseline has only the scoped `tools/vault-memory-coverage.init.gradle`
report configuration. It cannot establish whole affected-module/repository
coverage, and no such baseline report was found in the inspected build report
locations. Existing feature coverage evidence is separately recorded under T008
and T064 in `implementation-evidence.md`; it is not retroactive baseline coverage.

At the end of the original capture, no task was marked complete. On resumption,
the separate baseline measurement branch was used without switching the active
feature checkout. The corrected RP ceremony verified one synthetic registration
and two UV assertions; see the authoritative result above. Do not substitute the
historical failed Moto registration or earlier `8b4d85ad` observations for that
successful ASUS measurement, and do not interpret the small sample as a broad
performance guarantee.
