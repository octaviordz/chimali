# Disposable CTAP2 relying-party harness

This is a loopback-only WebAuthn relying party for manual authenticator checks. It creates one
synthetic account in memory, accepts only ES256 credentials, verifies registration and assertion
responses with `@simplewebauthn/server`, and forgets the credential and challenge state when the
process exits. It has no connection to an external service and must not be used for real accounts.

Requirements: Node.js 22 or newer, a browser supporting WebAuthn, and a CTAP2 authenticator.

```powershell
Set-Location 'D:\octav\source\repos\Chimali\tools\ctap2-rp-harness'
npm install
npm start
```

Open `http://localhost:4173` in the browser on the same Windows host. Registration and assertion
requests require user verification to match the T003 reference workload. Choose **Register** and
select the Chimali authenticator as a cross-platform security key. Complete registration, then
choose **Authenticate** twice and approve each user-verification prompt. The page displays server
verification, credential ID, user-verification result, and signature counter after each ceremony.
Restarting the server erases its synthetic state.

**Current disposition (2026-10-16):** The user accepted the observed UX and deferred investigation
of end-to-end measurement to [the superseding Feature 060 assessment](../../specs/060-fido2-latency-measurement/spec.md).
The adjusted browser value still includes any unmeasured Windows interaction and must not be
described as system-only processing or post-fingerprint response time. Existing results are
preserved in the implementation evidence. The procedure below is retained for a future session;
the internal-operation target remains documented, with T085 enhancements also deferred to a later phase.

For SC-007 measurement, reset the server process before the sample series and capture device logs
with `adb logcat -v epoch -s ChimaliT003:I "*:S"`. After each successful RP ceremony, use the
matching `operation_id` values from those logs and sum their `user_ns` values for the operation(s)
that contained the verification prompt. Divide that sum by 1,000,000 to convert nanoseconds to the
milliseconds accepted by the RP page; enter the result and correlated operation IDs, then record the
sample. The page stores RP options-to-verification time, subtracts the
measured app verification interval, reports the 1,000ms end-to-end limit, and emits a `SC007_E2E`
line in server output. Record at least 10 successful ceremonies. A sample without matching app
operation IDs and a measured user interval is not valid evidence. Logs and RP samples contain only
synthetic timing/correlation data and are held in memory or local Logcat.

For the <200ms internal FIDO/CTAP operation limit, use `internal_ns` in the same app log.
It stops when the response has been constructed, subtracts the measured user-verification interval,
and excludes Bluetooth queueing and report pacing. `system_ns` is a separate local HID handoff
measurement and must not be used as the internal-operation result.

This harness tests the WebAuthn server/browser path only when a real authenticator is selected.
It does not simulate Bluetooth, replace the project’s app/device suites, establish draft HDK
interoperability, or prove remote-host receipt timing. Record the actual authenticator transport,
selection path, ceremony outcome, counter values, and timing boundary in
`specs/055-migrate-hdk-draft/implementation-evidence.md`.
