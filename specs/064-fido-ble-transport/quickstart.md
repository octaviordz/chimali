# Validation Guide: FIDO BLE Transport

**Status**: BLE implementation, app composition and host evidence are recorded in [implementation-evidence.md](implementation-evidence.md). ASUS advertising Start/Stop timing and 10-second background persistence were measured (Record 026). Physical numeric-comparison acceptance/rejection, GetAssertion, interruption/reconnection, TalkBack, a fully green connected-device suite, coverage floors and target nonregression remain unverified. This independently delivered transport must also satisfy the parent selector's integration guide.

## Prerequisites and Dependency Probe

Use the repository wrapper and project toolchain with SDK37. Configure local SDK paths normally. Resolve and pin Nordic `no.nordicsemi.android.kotlin.ble:advertiser:1.3.1` from the existing dependency repositories. Nordic supplies peripheral advertising only: Chimali uses its public Android `BluetoothGattServerCallback` supplement for protocol-critical GATT admission, ATT errors, MITM permissions and ordered notification delivery. The Nordic high-level server is excluded because its released 1.3.1 write path auto-acknowledges into a dropping flow and its notification path has callback/mutex limitations; the v2 server API is not available. See [research](research.md), [TRD §4.6](../../docs/trd.md) and [dependency evidence](implementation-evidence.md#record-002-nordic-advertiser-dependency-resolution--build-verification-t004). Before acceptance, retain published AAR/POM, minimum-SDK, transitive-license and release-size review evidence; resolution and compilation alone do not prove API28 device support or size compliance. Verify no dynamic version or Nordic mock/server artifact enters production dependencies.

Use an Android API28+ peripheral-capable device supporting authenticated numeric comparison and a Windows11 22H2+ host. Record the exact Android model/OS, Windows build, browser, Bluetooth adapter/driver, source revision and build variant. Use synthetic credentials only. Pairing and WebAuthn interoperability cannot be established by mocks.

## Automated Validation

From the worktree root after implementation:

~~~powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest
.\gradlew.bat :feature:fido2:connectedAndroidDeviceTest
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
~~~

These tasks match the configured KMP Android host/device targets. After composing both parts, component acceptance includes the parent gate/selected-handle boundary checks in its [quickstart](../064-bluetooth-transport-selection/quickstart.md). The full parent settings/switch matrix belongs to later parent acceptance, as defined in [integration.md](../064-bluetooth-transport-selection/integration.md); it does not block independent BLE acceptance. Do not run unavailable future test-class filters or treat an empty suite as success.

Expected host tests:
1. Independently authored byte vectors verify identifiers, revision selection, GetInfo, raw MSG/CTAP payloads, PING, ERROR, zero/boundary/oversize lengths, default and large MTU, sequence wrap and malformed sequences. Define an acceptance corpus of valid fragmented requests within advertised limits; on an authenticated stable link with successful notification delivery, every request and response preserves bytes and order. Assess malformed input, delivery failures and interrupted links separately. Production encoders must not generate their own expected answers.
2. Virtual clock: at 2999ms partial request remains, at 3000ms it expires; valid continuation refreshes, invalid input does not. Completed requests awaiting UV remain alive past three seconds and emit <=500ms keepalives. When processing produces a terminal response, keepalives stop before its first fragment; a fragmented response taking longer than 500ms is delivered in order without interleaved keepalives, subject to the separate five-second notification completion deadline.
3. One host/operation; BUSY on conflicts except valid CANCEL; bounded admission and TX; exactly one outstanding notification. Saturation never silently loses acknowledged frames. Suppress a notification-completion callback: at its independent five-second deadline the session aborts, owned resources are released safely and a late callback cannot affect a new session.
4. Cancellation before/during prompt, response fragmentation and commit races; no CANCEL response, original-request cancellation only when cancellable; no late callback publishes success or mutates a newer owner.
5. Actual core handlers through raw CTAP boundary preserve registration/assertion, UV/authorization, append-only events and Classic framing. Owned buffers are erased after borrower completion on every terminal path.
6. Parent process gate precedes factory/radio calls; shared service/home activation, duplicate Start, failed startup, five-second cancellation, stale callbacks, Retry and no fallback. Host tests cannot certify Android pairing.

## Physical Procedures

| Procedure | Steps and expected outcome |
|---|---|
| Secure startup / discovery | Enable BLE after parent choice freezes; verify standard service/attributes with an independent scanner, successful advertising <500ms after Start, no premature Ready, persistent notification |
| Pairing | Pair from Windows; confirm matching numbers on phone and PC; prove authenticated access. Reject numeric comparison and attempt unencrypted/unauthenticated access: no CTAP operation occurs |
| Registration / assertion | Complete direct FIDO BLE MakeCredential then GetAssertion with real UV; retain evidence of BLE0xFFFD path and absence of Classic fallback. Record physical timing diagnostically |
| Negotiation / fragmentation | Confirm revision0x20 and notifications, test default23-byte and larger supported MTU, write capacity20–512, response boundaries and fragmented byte equality |
| CANCEL / interruption | Cancel while awaiting verification; prompt/keepalives stop, original request cancelled, link reusable. Interrupt Bluetooth and permissions during startup, RX, UV and TX: no crash, retained secret or stale response |
| Recovery | Invalidate saved pairing on one side; follow Forget/Pair again/Retry without deleting credentials or restarting phone/app |
| Lifecycle | Background/close Activity: service remains available. Explicit Stop: advertising ceases and resources release <1s; late callbacks cannot restart it |
| Reconnect stress | Perform50 consecutive connect/disconnect/reconnect cycles including interrupted requests; next request clean; inspect retained owners/buffers and memory against initial quiescent baseline; no crash/leak/process restart |
| Parent / Classic regression | Run the parent's clean-install default, saved Classic, pending-restart and no-fallback journeys. Preserve existing Windows/macOS/Linux Classic obligations separately |
| Accessibility | Recovery and status remain operable with TalkBack, high contrast and200% font scaling |

A platform/host combination that cannot meet authenticated pairing or direct FIDO BLE interoperability is recorded as unsupported/unverified or failed as the procedure dictates; never downgrade security or infer support from OS version.

## Performance, Coverage and Evidence

For SC-006, use the constitutional host-simulated synthetic relying party and UV double, measure each implemented internal operation <200ms excluding measured UV work and Bluetooth response handoff. Physical timing cannot substitute. Use the existing feature060 assessment procedure; retain individual samples and boundaries. Advertising/Stop budgets are separate feature criteria.

Require Tier1 >=60% instructions and applicable branches, Tier2 >=20% instructions, global >=50% instructions plus completeness and nonregression under feature058. Local coverage checks use tools/local-ci.ps1; accepted-target raw-count nonregression requires tools/integration-ci.ps1 and the trusted candidate/target, baseline index, harness and device prerequisites specified in tasks.md T038 and parent T039. If prerequisites are unavailable, record the assessment Unverified rather than treating local CI as a pass. Report actual affected production/test inventory and exact revision. Retain build-size, memory and lifecycle evidence. The hosted runner remains disabled under the existing waiver; local evidence is not hosted enforcement.

Record all ten TRD9.1 fields: requirement, scope, revision, named_procedure, environment, expected_observed_result, date, limitations, status, artifact_link. Distinguish real Android/Windows and substituted components. Use Passed/Failed only for executed procedures; otherwise Unverified or an authorized Deferred. Planning creates no numeric pass or device evidence.
