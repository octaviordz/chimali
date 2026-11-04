# Validation Guide: Transport Selection

**Status (2026-11-04)**: Focused Nordic advertiser startup and remote Android FIDO-service discovery passed on ASUS/API35 and Mi/API29 (Records 046–047). A current read-only snapshot shows Chimali's foreground transport service and advertiser registered on both phones (BLE Record 070); it does not establish a remote connection or ceremony. The latest configured run passed common/FIDO2 host checks, settings unit tests and settings instrumentation 12/12 on both devices. The earlier full FIDO2 suite passed 50/55 on ASUS/API35 and 47/52 on Mi/API29. The corrected package-filtered non-research suite passed 50/50 on ASUS and 47/47 on Mi; the five controlled trace procedures remain unverified (parent Record 051; BLE Record 069). BLE GATT security tests passed 4/4 per device, including both registered services. Runtime errors now display “Retry Authenticator” and route to Retry even when process availability is Ready; host and device UI checks passed (parent Records 052–053; BLE Records 071–072). Physical retry after interruption remains unverified. Windows Bluetooth Settings did not list the ASUS, and integrated-browser WebAuthn registration timed out without a credential response (Records 044–045); the user canceled the Windows dialog for this attempt. Device pairing, Windows ceremonies, accessibility inspection and complete lifecycle acceptance remain unverified; coverage integration and target nonregression remain deferred by owner direction.

## Prerequisites

- Repository JDK 17, Android SDK 37 and checked-in wrapper; configure the normal local SDK path without committing machine paths.
- Functional Classic and FIDO BLE transports, compatible lifecycle contract and explicit single selected binding. Record prerequisite revision/protocol/pairing contract. Missing BLE or a test double blocks acceptance.
- Dedicated Android API 28+ device with peripheral/advertising capability and Windows 11 host. Record device/OS, Windows build, browser, Bluetooth adapter/driver, source revision and build variant.
- Synthetic vault/credential data only. Use TRD §9.1 fields: requirement, scope, revision, named_procedure, environment, expected_observed_result, date, limitations, status, artifact_link.
- Follow [data-model.md](data-model.md) and [contract](contracts/transport-selection.md). Preserve the disabled hosted runner and feature-058 operational waiver.

## Automated Checks

Run from repository root after implementation:

```powershell
.\gradlew.bat :core:common:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:settings:testDebugUnitTest
.\gradlew.bat :feature:settings:connectedDebugAndroidTest
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

Commands follow current configured targets. Add an exact configured app composition-test command if implementation introduces that suite. Coverage invokes required connected suites: retain failures and missing-device status, never skip to synthesize acceptance. Local evidence does not establish waived hosted enforcement.

Expected assertions:

1. Missing field defaults to BLE; Classic and unknown code strings round-trip through serialization; the repository rejects unknown stored/input codes without overwrite or fallback; unrelated fields survive saves; invalid/corrupt/read failure is explicit without reset; write failure preserves prior choice/timestamp. For a persistent read failure, assert selection is disabled, retry remains available, storage/relaunch/technical-assistance guidance is shown, and no shared preferences or unrelated data is reset.
2. Service-first creation cannot construct a transport, initialize Bluetooth or use the transport until one snapshot is frozen. A non-initializing capability check distinguishes known unsupported from SelectedNotStarted when support is unknown; starting is shown until the first activation attempt completes. The snapshot 5s deadline runs from snapshot launch to frozen choice or failure; lazy activation has a separate 5s deadline from each activation attempt to ready or failure. Concurrent service/home callers share each attempt. Timeout/cancellation fences late callbacks and releases attempt-owned resources before retry; corrected permissions/Bluetooth permit a fresh activation attempt for the same frozen choice. The unselected fake has zero initialization/connect/disconnect calls.
3. For registration and authentication on each transport, save during a deterministically suspended fake ceremony and assert delegate identity, service, response, authorization and credential/events are preserved; the saved choice remains pending until process restart. Release each ceremony and independently verify the result.
4. Same-value selection is a no-op; serialized saves cannot finish out of order; selecting process mode clears pending. Activity/service recreation and reconnect retain process choice.
5. Unsupported, permission-required, Bluetooth-off and startup failure remain distinct with no fallback. Compose tests verify loading/save failure, radio semantics and active/pending text.

## Target Comparison and Latency Evidence

Ordinary local CI checks and coverage floors do not by themselves establish changed-module nonregression. For the exact candidate commit and live accepted target, obtain the independently trusted accepted-target index and its indexed bundle from the feature-058 accepted evidence source. Place the index at `<EvidenceRoot>/baselines/current.json` and retain the indexed bundle under the same evidence root at the index relative `bundle_path`; preserve its bytes, identity and digest. The integration script requires the index even when `-BaselineBundle` is provided, and any supplied bundle must match the indexed path. Validate target commit/tree, bundle digest, provenance and measurement compatibility using the trusted feature-058 workflow. If the index or compatible evidence is missing, follow only the applicable reviewed baseline establishment/promotion process and its prerequisites in `specs/058-enforce-coverage-targets/contracts/coverage-workflow.md` and `tools/coverage/publish.ps1`; capture of the exact target alone does not establish an accepted index. Do not hand-author or promote an index from an unverified capture. The initial-baseline publisher mode applies only when its explicit initial-baseline prerequisites hold; do not use it to replace an existing target index. Once trusted indexed evidence is available, invoke `tools/integration-ci.ps1` with exact `-CandidateRef`, live `-TargetRef`, trusted `-HarnessRoot`, authorized `-DeviceSerial` and an `-EvidenceRoot` outside the checkout; optional `-BaselineBundle` must identify the indexed bundle. Follow feature-058 clean-tree and retained-evidence rules. Record candidate/target identities and separate completeness, floor and regression outcomes. Missing device, live target, valid index or compatible bundle leaves comparison unverified; the hosted runner waiver remains in force.

For the constitutional latency assessment, run:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest
```

Follow `specs/060-fido2-latency-measurement/quickstart.md` to locate and retain the report. Record the candidate revision, synthetic relying party and user-verification substitutions, each implemented internal operation's measurement and strict <200ms decision, and any missing or substituted operation. A partial or unavailable report cannot establish full performance acceptance; physical transport timing remains diagnostic.

## Physical Validation

### User-visible flow

1. In Passkey Authenticator settings, verify FIDO BLE is selected on a clean install and Classic HID is available as the alternative.
2. Change the saved mode and verify the current active mode remains visible with a full-restart instruction; selecting the current mode cancels the pending change.
3. For FIDO BLE, compare the Android and host numeric-comparison values and reject mismatches. On a pairing/link failure, use system Bluetooth settings to forget the pairing on the phone and host where present, pair again, then use Retry. Credentials must remain intact and Retry must not require an app restart.
4. For Classic HID, preserve the existing system pairing and cross-platform host procedures. If the process-selected mode is unavailable, report its reason and do not advertise or connect through the other mode.

Install using the normal repository build workflow. Match package to variant: shrunkDebug uses com.chimali.shrunk; ordinary debug uses com.chimali. These commands use the shrunk variant:

```powershell
adb devices
adb shell am force-stop com.chimali.shrunk
adb shell am start -n com.chimali.shrunk/com.chimali.MainActivity
```

Launch the activity explicitly for lifecycle checks. The package also contains LeakCanary launcher components, so `monkey -p ... -c LAUNCHER` can select its diagnostic launcher instead of Chimali's MainActivity.

1. **Default**: Start a clean isolated test installation without visiting settings. Use the prerequisite's BLE pairing/connection procedure. Complete Windows registration and assertion from a test relying party, independently verify signatures/credential metadata, and confirm real BLE evidence and UI active state. Clean-install preparation may erase only synthetic test data.
2. **Switch to Classic**: Save Classic while BLE is active. Observe pending Classic and active BLE. Force-stop/relaunch, confirm Classic is selected and not started, start the authenticator, confirm Classic becomes active, and complete physical registration/assertion. Preserve credentials across restart.
3. **Switch back**: Save BLE and repeat full process restart and physical ceremonies. Do not clear app data between switches. Re-pair only if required by the transport contract, documenting it.
4. **Pending cancellation**: Select alternative then process mode; pending clears without reconnect.
5. **Persistence**: Verify both choices across force-stop, Android-managed process death and reboot separately. After force-stop or reboot, confirm the saved mode is selected and not started until explicit Start. For process death while the foreground service is active, record the exit reason and whether Android restarts the sticky service; verify the new process resolves the persisted selection before constructing a transport, and that only the resolved transport becomes active after successful startup. Relaunch MainActivity and confirm its selected and active status match the service. A saved alternative applies after process restart; Activity/service recreation within the same process must not apply it. Use `adb reboot`, wait for boot/unlock and relaunch to verify reboot retention.
6. **In-flight change**: Hold registration/assertion at confirmation, save alternative, then complete confirmation. Verify original transport response, committed event consistency and no disconnect. Only full process restart applies the new mode.
7. **Unavailable**: Use unsupported profile or controlled startup failure. Confirm no fallback advertisement/connection and reachable settings. Save alternative and restart to recover. Separately test permission/Bluetooth-off recovery using the same process choice.
8. **Accessibility**: Validate TalkBack focus/announcements, contrast and 200% scaling for saved/active/pending/error states. Semantics tests do not establish physical TalkBack/contrast compliance.

## Acceptance Record

Record SC-001–006 independently with environment and artifacts. Host tests establish logic, physical procedures establish Android/Windows behavior. Pairing or a connected label alone does not prove a passkey ceremony. Missing prerequisite/device evidence is Unverified or Deferred, never Passed. Existing Classic cross-platform duties remain outside this feature's limited Windows validation scope.
