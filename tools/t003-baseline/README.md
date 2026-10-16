# T003 baseline measurement branch

Baseline: `bug/056-credential-registration`, commit
`d9f668a7024fa43f699642e4f2fb8ff144d5b1f1`.

Measurement branch: `codex/t003-baseline-instrumentation`.

The 055 implementation and T003 evidence were saved in stash commit
`1c22336da0731255629d2d2b7a3499483c08d826` before switching branches. Restore on
`codex/055-migrate-hdk-draft` with `git stash apply --index` using that exact
commit when the measurement branch work is finished. Browser snapshots are
saved in a separate stash named `T003: preserve generated browser traces before
baseline instrumentation`.

## Changes

- Retain the baseline's original processing profiler boundary and user-interaction hooks.
- Record a second measurement from CTAP dispatch after complete message reassembly through
  the return of the final response report's `BluetoothHidDeviceWrapper.sendReport` call.
- Observe packet arrays by identity, retaining FIFO order, report pacing and existing enqueue
  and sender behavior. No extra wait is introduced in the transport.
- Emit numeric timings and fixed operation names through `Log.println` with tag `ChimaliT003`.
  Existing release log suppression stays enabled. No credential, RP/user identifier, key,
  request/response content or Bluetooth address is emitted by this instrumentation.
  A class-specific Detekt `ForbiddenImport` exception permits this diagnostic output on the
  measurement branch; the ordinary Kermit logging policy continues to apply elsewhere.
- Add async `T003.MakeCredential`, `T003.GetAssertion` and `T003.GetInfo` trace slices.
- Enable shell profiling in the release manifest while retaining release optimization,
  resource shrinking, signing and package `com.chimali`.

## Build and capture

Build `:app:assembleRelease`. The APK is `app/build/outputs/apk/release/app-release.apk`.
Install with `adb -s R7AIB7002901AZ3 install -r` to retain the ASUS installation's data.
Record the APK hash and confirm the installed package is not debuggable.

Capture Logcat with `adb -s R7AIB7002901AZ3 logcat -v epoch -s ChimaliT003:I "*:S"`.
Start the authenticator and complete a synthetic RP registration followed by assertions.
Correlate each ceremony with the RP's verified result; one browser action can generate
multiple CTAP operations. Retain all samples, including failures and warm-up operations.

Each log record contains nanoseconds:

- `processing_ns`: dispatch through baseline handler completion and keepalive shutdown,
  including the recorded user interaction interval.
- `total_ns`: dispatch through final local HID API acceptance, including user interaction.
- `user_ns`: accumulated interval from the baseline's existing user-interaction hooks.
- `system_ns`: `max(0, total_ns - user_ns)`. Divide by 1,000,000 for milliseconds.
- `reports_accepted`: whether all response reports were accepted by the local API.
- `user_window_open`: whether an interaction interval lacked its completion hook.

A failed enqueue/API call or open user window invalidates a successful latency sample.
No automatic compliance claim is emitted. API acceptance is not proof of remote receipt;
RP verification supplies independent completion evidence. Browser ceremony time includes
Windows, transport and operator waits. The baseline user interval follows its existing hook
boundaries; it must not be described as sensor-only time.

Use Perfetto's activity/window/frame sources for first display and frame timing, together
with `linux.process_stats` for RSS sampling. Capture startup and a defined complete ceremony
session, recording process identity and sampling interval; a sampled RSS maximum is bounded
by the capture window and interval. Record `/proc/<pid>/status` `VmHWM` at session end.
First-display and process-memory measurement do not require changes to cryptography.

Repeat the same measurement additions and capture protocol on the feature source before
claiming a comparison. Instrumentation adds overhead; retain original uninstrumented APK/AAB
sizes for binary budgets and label instrumented performance measurements explicitly.

## Build validation (2026-10-15)

`:feature:fido2:ktlintCheck :app:assembleRelease :app:lintRelease` passed in
15m 56s after correcting formatting in the new measurement class. The merged release manifest
contains `profileable android:shell="true"`, uses `com.chimali`, and does not enable debugging.
The optimized APK retains the `ChimaliT003` and `reports_accepted=` strings in `classes2.dex`.

Instrumented APK: 62,676,295 bytes; SHA-256
`8C94FA4DAE3130AC919B9F331BBB0F02BA620D5B01172872E9D265FF8697CCB1`.
Build log: `D:\temp\chimali-t003-baseline-instrumentation-build.log`.
This validates build and packaging; fresh on-device timing capture is still required.
